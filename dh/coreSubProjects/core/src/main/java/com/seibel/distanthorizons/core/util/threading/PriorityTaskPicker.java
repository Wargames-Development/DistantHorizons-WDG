package com.seibel.distanthorizons.core.util.threading;

import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.config.listeners.IConfigListener;
import com.seibel.distanthorizons.core.enums.MinecraftTextFormat;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.logging.f3.F3Screen;
import com.seibel.distanthorizons.core.util.objects.RollingAverage;
import com.seibel.distanthorizons.core.logging.DhLogger;
import com.seibel.distanthorizons.core.render.TerrainCacheDiagnostics;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.NumberFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

/**
 * This handles dividing work DH needs to do across
 * DH's thread pool.
 */
public class PriorityTaskPicker
{
	private static final DhLogger LOGGER = new DhLoggerBuilder().build();
	
	private static final ScheduledExecutorService SCHEDULED_EXECUTOR_SERVICE = Executors.newScheduledThreadPool(1, new DhThreadFactory("Task Picker Re-queue Schedule", Thread.NORM_PRIORITY, true));
	/** 
	 * If a thread is paused we can end up in a situation where
	 * all the other pools are done, causing {@link PriorityTaskPicker#tryStartNextTask()}
	 * to queue nothing, preventing the paused pools from running until
	 * more work is queued. <br><br>
	 * 
	 * This way we can check on those paused threads at a future time
	 * to queue their work.
	 */
	private static final int MS_TO_CHECK_ON_PAUSED_THREADS = 1_000;
	
	private final AtomicReference<ScheduledFuture<?>> scheduledFutureRef = new AtomicReference<>();
	private final AtomicBoolean dispatchRequested = new AtomicBoolean();
	private final TerrainCacheDiagnostics.Work dispatchWork = new TerrainCacheDiagnostics.Work();
	private long dispatchSequence;
	
	
	/** the list of currently registered executors */
	private final List<Executor> executors = new CopyOnWriteArrayList<>();
	
	/** Lock to ensure task picking logic is thread-safe */
	private final ReentrantLock taskPickerLock = new ReentrantLock();
	/** Tracks the number of active threads */
	private final AtomicInteger occupiedThreadsRef = new AtomicInteger(0);
	
	private final AtomicBoolean isShutDownRef = new AtomicBoolean(false);
	
	
	
	//==========//
	// executor //
	//==========//
	///region
	
	public Executor createExecutor(String name) { return this.createExecutor(name, Thread.NORM_PRIORITY, null); }
	public Executor createExecutor(String name, int priority) { return this.createExecutor(name, priority, null); }
	public Executor createExecutor(String name, int priority, @Nullable PriorityTaskPicker.IExecutorCanRunFunc canRunFunc)
	{
		return this.createExecutor(name, priority, canRunFunc, 0);
	}

	public Executor createExecutor(String name, int priority, @Nullable PriorityTaskPicker.IExecutorCanRunFunc canRunFunc, int threadPriorityOffset)
	{
		Executor executor = new Executor(this, name, priority, canRunFunc, threadPriorityOffset);
		this.executors.add(executor);
		return executor;
	}
	
	/**
	 * Tries to start the next queued task
	 * for one of the available executors.
	 */
	private void tryStartNextTask()
	{
		if (this.occupiedThreadsRef.get() >= Config.Common.MultiThreading.numberOfThreads.get()) return;
		this.dispatchRequested.set(true);
		do
		{
			if (this.isShutDownRef.get() || !this.taskPickerLock.tryLock()) return;
			try
			{
				this.dispatchRequested.set(false);
				this.dispatchReadyWork();
			}
			finally
			{
				this.taskPickerLock.unlock();
			}
			// An execute() or completion that lost the tryLock can request another pass.
		}
		while (this.dispatchRequested.get());
	}

	private void dispatchReadyWork()
	{
		if (!TerrainCacheDiagnostics.ENABLED)
		{
			this.fillSlots();
			return;
		}
		long wallStart = System.nanoTime();
		long cpuStart = TerrainCacheDiagnostics.threadCpuTime();
		try { this.fillSlots(); }
		finally { this.dispatchWork.record(cpuStart, wallStart); }
	}

	private void fillSlots()
	{
		int budget = Config.Common.MultiThreading.numberOfThreads.get();
		boolean retry = false;
		while (!this.isShutDownRef.get() && this.occupiedThreadsRef.get() < budget)
		{
			Executor next = null;
			for (Executor executor : this.executors)
			{
				if (executor.taskQueue.isEmpty() || executor.isShutdown()) continue;
				if (!executor.canRun())
				{
					retry = true;
					continue;
				}
				if (next == null || executor.shouldDispatchBefore(next)) next = executor;
			}
			if (next == null) break;

			TrackedRunnable task = next.taskQueue.poll();
			if (task == null) continue;
			// Reserve the slot before the worker starts. A fast task can finish inside execute().
			this.occupiedThreadsRef.incrementAndGet();
			next.admittedTasks.incrementAndGet();
			try
			{
				next.runTask(task);
				next.lastDispatch = ++this.dispatchSequence;
			}
			catch (RejectedExecutionException e)
			{
				next.admittedTasks.decrementAndGet();
				this.occupiedThreadsRef.decrementAndGet();
				if (!this.isShutDownRef.get() && !next.isShutdown())
				{
					next.taskQueue.add(task);
					retry = true;
				}
				break;
			}
		}
		if (retry && !this.isShutDownRef.get() && this.scheduledFutureRef.get() == null)
		{
			this.scheduledFutureRef.set(SCHEDULED_EXECUTOR_SERVICE.schedule(() ->
			{
				this.scheduledFutureRef.set(null);
				this.tryStartNextTask();
			}, MS_TO_CHECK_ON_PAUSED_THREADS, TimeUnit.MILLISECONDS));
		}
	}
	
	/**
	 * Stop all pools on a single bounded deadline instead of waiting up to five
	 * seconds for each executor in sequence on the client disconnect thread.
	 * World-save and database-close lifecycles remain synchronous and unchanged.
	 */
	public void shutdownNow()
	{
		if (!this.isShutDownRef.compareAndSet(false, true))
		{
			return;
		}
		LOGGER.info("Shutting down PriorityTaskPicker thread pool...");
		
		this.taskPickerLock.lock();
		try
		{
			ScheduledFuture<?> scheduled = this.scheduledFutureRef.getAndSet(null);
			if (scheduled != null) scheduled.cancel(false);
		}
		finally { this.taskPickerLock.unlock(); }
		
		// Signal every executor before waiting; they can terminate in parallel.
		for (Executor executor : this.executors)
		{
			if (executor != null)
			{
				executor.shutdown();
			}
		}
		
		final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
		boolean interrupted = false;
		for (Executor executor : this.executors)
		{
			if (executor == null || executor.isTerminated())
			{
				continue;
			}
			try
			{
				long remaining = deadline - System.nanoTime();
				if (remaining <= 0 || !executor.awaitTermination(remaining, TimeUnit.NANOSECONDS))
				{
					// Still-running tasks get an interruption request just as they did
					// after the old five-second per-pool timeout.
					executor.shutdownNow();
				}
			}
			catch (InterruptedException e)
			{
				interrupted = true;
				executor.shutdownNow();
			}
		}
		if (interrupted)
		{
			Thread.currentThread().interrupt();
		}
	}

	///endregion
	
	
	
	//================//
	// helper classes //
	//================//
	///region
	
	/**
	 * Each executor handles a specific type of work that DH needs done.
	 * By separating out task into its own executor it allows for easier performance monitoring
	 * via a tool like Visual VM and fairly spreading out CPU time between tasks.
	 */
	public static class Executor extends AbstractExecutorService implements IConfigListener
	{
		private final PriorityTaskPicker parentTaskPicker;
		private final String name;
		
		private final Queue<TrackedRunnable> taskQueue = new ConcurrentLinkedQueue<>();
		
		private final AtomicInteger runningTasksRef = new AtomicInteger(0);
		private final AtomicInteger completedTasksRef = new AtomicInteger(0);
		private final AtomicInteger admittedTasks = new AtomicInteger(0);
		private final TerrainCacheDiagnostics.Work work = new TerrainCacheDiagnostics.Work();
		private long lastDispatch;
		
		/** 
		 * a higher number means this executor will run before others. <br> 
		 * 0 = default
		 */
		private final int priority;
		private final int threadPriorityOffset;
		
		/** used for performance logging */
		private final RollingAverage runTimeInMsRollingAverage = new RollingAverage(200);
		
		@Nullable
		private final PriorityTaskPicker.IExecutorCanRunFunc canRunFunc;
		
		/** holds the threads this {@link Executor} can run */
		private final RateLimitedThreadPoolExecutor threadPoolExecutor;
		
		
		
		//=============//
		// constructor //
		//=============//
		///region
		
		private Executor(PriorityTaskPicker parentTaskPicker, String name, int priority, @Nullable PriorityTaskPicker.IExecutorCanRunFunc canRunFunc, int threadPriorityOffset)
		{
			this.parentTaskPicker = parentTaskPicker;
			this.name = name;
			this.priority = priority;
			this.threadPriorityOffset = threadPriorityOffset;
			this.canRunFunc = canRunFunc;
			
			this.threadPoolExecutor = this.createThreadPool();
			
			Config.Common.MultiThreading.numberOfThreads.addListener(this);
		}
		
		private RateLimitedThreadPoolExecutor createThreadPool()
		{
			int threadPriority = Math.max(Thread.MIN_PRIORITY, Math.min(Thread.MAX_PRIORITY,
				Config.Common.MultiThreading.threadPriority.get() + this.threadPriorityOffset));
			return new RateLimitedThreadPoolExecutor(
					Config.Common.MultiThreading.numberOfThreads.get(),
					new DhThreadFactory(this.name, threadPriority, false),
					new ArrayBlockingQueue<>(Runtime.getRuntime().availableProcessors())
			);
		}
		
		///endregion
		
		
		
		//=================//
		// config handling //
		//=================//
		///region
		
		@Override
		public synchronized void onConfigValueSet()
		{
			if (this.threadPoolExecutor.isShutdown()) return;
			int size = Config.Common.MultiThreading.numberOfThreads.get();
			if (size < this.threadPoolExecutor.getMaximumPoolSize())
			{
				this.threadPoolExecutor.setCorePoolSize(size);
				this.threadPoolExecutor.setMaximumPoolSize(size);
			}
			else
			{
				this.threadPoolExecutor.setMaximumPoolSize(size);
				this.threadPoolExecutor.setCorePoolSize(size);
			}
			this.parentTaskPicker.tryStartNextTask();
		}

		private boolean shouldDispatchBefore(Executor other)
		{
			int active = this.admittedTasks.get();
			int otherActive = other.admittedTasks.get();
			if (active != otherActive) return active < otherActive;
			if (this.lastDispatch != other.lastDispatch) return this.lastDispatch < other.lastDispatch;
			return this.priority > other.priority;
		}
		
		///endregion
		
		
		
		//=====================//
		// task queue handling //
		//=====================//
		///region
		
		@Override
		public void execute(@NotNull Runnable command)
		{
			// needed so we don't try queuing tasks that will never be completed
			if (this.threadPoolExecutor.isShutdown())
			{
				throw new RejectedExecutionException("Thread pool ["+this.name+"] shutdown.");
			}
			
			
			this.taskQueue.add(new TrackedRunnable(this.parentTaskPicker, this, command));
			
			// Attempt to start the task immediately
			this.parentTaskPicker.tryStartNextTask();
		}
		
		/** The passed in {@link Runnable} must be exactly the same as the one passed into {@link PriorityTaskPicker.Executor#execute(Runnable)} */
		public void remove(@NotNull Runnable command) { this.removeIfQueued(command); }

		public boolean removeIfQueued(@NotNull Runnable command)
		{
			for (TrackedRunnable task : this.taskQueue)
			{
				if (task.command == command) return this.taskQueue.remove(task);
			}
			return false;
		}
		
		
		public void runTask(@NotNull Runnable command) { this.threadPoolExecutor.execute(command); }
		
		
		public int getQueueSize() { return this.taskQueue.size(); }
		/** number of threads */
		public int getPoolSize() { return Config.Common.MultiThreading.numberOfThreads.get(); }
		
		public int getRunningTaskCount() { return this.runningTasksRef.get(); }
		public int getCompletedTaskCount() { return this.completedTasksRef.get(); }
		public void writeWorkMetrics(Map<String, Long> output, String prefix)
		{
			this.work.writeTo(output, prefix + "_executed");
			if ("render_loader".equals(prefix)) parentTaskPicker.dispatchWork.writeTo(output, "worker_picker_dispatch");
		}
		/** Will return NaN if nothing has been submitted yet */
		public double getAverageRunTimeInMs() { return this.runTimeInMsRollingAverage.getAverage(); }
		
		public void clearQueue() { this.taskQueue.clear(); }
		
		public boolean canRun()
		{
			if (this.canRunFunc == null)
			{
				return true;
			}
			
			return this.canRunFunc.canRun();
		}
		
		
		//endregion
		
		
		
		//==========//
		// shutdown //
		//==========//
		///region
		
		@Override
		public void shutdown() { this.threadPoolExecutor.shutdown(); }
		
		@Override
		public @NotNull List<Runnable> shutdownNow() { return this.threadPoolExecutor.shutdownNow(); }
		
		@Override
		public boolean isShutdown() { return this.threadPoolExecutor.isShutdown(); }
		@Override
		public boolean isTerminated() { return this.threadPoolExecutor.isTerminated(); }
		
		@Override
		public boolean awaitTermination(long timeout, @NotNull TimeUnit unit) throws InterruptedException 
		{ return this.threadPoolExecutor.awaitTermination(timeout, unit); }
		
		///endregion
		
		
		
		//===========//
		// debugging //
		//===========//
		///region
		
		public static String getThreadPoolStatString(String displayName, PriorityTaskPicker.Executor pool)
		{
			String o = MinecraftTextFormat.ORANGE;
			String g = MinecraftTextFormat.GREEN;
			String b = MinecraftTextFormat.DARK_BLUE;
			String y = MinecraftTextFormat.YELLOW;
			String cf = MinecraftTextFormat.CLEAR_FORMATTING;
			
			
			
			NumberFormat numberFormat = F3Screen.NUMBER_FORMAT;
			
			String queueSize = (pool != null) ? numberFormat.format(pool.getQueueSize()) : "-";
			String completedCount = (pool != null) ? numberFormat.format(pool.getCompletedTaskCount()) : "-";
			
			String message = displayName+", Tasks: "+o+queueSize+cf+", Done: "+g+completedCount+cf;
			
			if (pool != null)
			{
				// active threads
				int activeThreadCount = pool.getRunningTaskCount();
				int threadCount = pool.getPoolSize();
				
				boolean threadPoolActive = pool.canRun();
				String poolActiveString = threadPoolActive ? ("Active") : (o+"Paused"+cf);
				
				message += ", "+poolActiveString+": "+y+activeThreadCount+cf+"/"+threadCount;
				
				// thread runtime
				String runTimeAvgStr;
				double runTimeAvgInMs = pool.getAverageRunTimeInMs();
				if (!Double.isNaN(runTimeAvgInMs))
				{
					runTimeAvgStr = numberFormat.format(runTimeAvgInMs);
				}
				else
				{
					runTimeAvgStr = "<0";
				}
				
				message += ", Avg: "+b+runTimeAvgStr+"ms"+cf;
			}
			
			
			return message;
		}
		
		///endregion
		
		
		
		//================//
		// base overrides //
		//================//
		///region
		
		@Override 
		public String toString() { return getThreadPoolStatString(this.name, this); }
		
		///endregion
		
	}
	
	/** used so we can {@link PriorityTaskPicker.Executor#remove(Runnable)} using the original {@link Runnable} */
	private static class TrackedRunnable implements Runnable
	{
		private final PriorityTaskPicker parentTaskPicker;
		private final Executor executor;
		
		/** the runnable passed into {@link PriorityTaskPicker.Executor#execute(Runnable)} */
		public final Runnable command;
		
		
		
		//=============//
		// constructor //
		//=============//
		
		public TrackedRunnable(PriorityTaskPicker parentTaskPicker, Executor executor, Runnable command)
		{
			this.parentTaskPicker = parentTaskPicker;
			this.executor = executor;
			this.command = command;
		}
		
		
		
		//=========//
		// running //
		//=========//
		
		@Override
		public void run()
		{
			this.executor.runningTasksRef.getAndIncrement();
			
			long startTime = System.nanoTime();
			long cpuStart = TerrainCacheDiagnostics.threadCpuTime();
			try
			{
				this.command.run();
			}
			finally
			{
				long timeElapsed = System.nanoTime() - startTime;
				if (TerrainCacheDiagnostics.ENABLED) this.executor.work.record(cpuStart, startTime);
				this.executor.runTimeInMsRollingAverage.add(TimeUnit.NANOSECONDS.toMillis(timeElapsed));
				
				// Update variables related to task status
				this.executor.runningTasksRef.getAndDecrement();
				this.executor.completedTasksRef.getAndIncrement();
				this.executor.admittedTasks.decrementAndGet();
				this.parentTaskPicker.occupiedThreadsRef.getAndDecrement();
				
				this.parentTaskPicker.tryStartNextTask();
			}
		}
		
	}
	
	/**
	 * Provides a way to dynamically enable/disable
	 * certain {@link PriorityTaskPicker.Executor}'s.
	 */
	@FunctionalInterface
	public interface IExecutorCanRunFunc
	{
		boolean canRun();
	}
	
	///endregion
	
	
	
}
