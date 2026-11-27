package com.seibel.distanthorizons.core;

import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.config.eventHandlers.presets.ThreadPresetConfigEventHandler;
import com.seibel.distanthorizons.core.util.threading.PriorityTaskPicker;
import java.util.concurrent.Future;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RenderLoaderPriorityTest {
    @Test public void aBurstOfMeshWorkYieldsTheNextSlotToWaitingIo() throws Exception {
        assertTrue(ThreadPresetConfigEventHandler.getDefaultThreadCount() > 0);
        PriorityTaskPicker picker = new PriorityTaskPicker();
        PriorityTaskPicker.Executor render = picker.createExecutor("Render Loader", Thread.NORM_PRIORITY + 1);
        PriorityTaskPicker.Executor io = picker.createExecutor("IO");
        int capacity = render.getPoolSize();
        CountDownLatch allRunning = new CountDownLatch(capacity);
        Semaphore release = new Semaphore(0);
        try {
            for (int i = 0; i < capacity * 2; i++) {
                render.execute(() -> {
                    allRunning.countDown();
                    release.acquireUninterruptibly();
                });
            }
            assertTrue(allRunning.await(5, TimeUnit.SECONDS));
            Future<Integer> waitingIo = io.submit(() -> 42);
            release.release();
            assertEquals(42, (int) waitingIo.get(5, TimeUnit.SECONDS));
            assertTrue(render.getQueueSize() >= capacity - 1);
        } finally {
            release.release(capacity * 2);
            picker.shutdownNow();
        }
    }

    @Test public void concurrentSubmissionsAndFastCompletionsDoNotLoseAWakeup() throws Exception {
        assertTrue(ThreadPresetConfigEventHandler.getDefaultThreadCount() > 0);
        PriorityTaskPicker picker = new PriorityTaskPicker();
        PriorityTaskPicker.Executor pool = picker.createExecutor("Worker");
        CountDownLatch finished = new CountDownLatch(2000);
        Thread secondProducer = new Thread(() -> {
            for (int i = 0; i < 1000; i++) pool.execute(finished::countDown);
        });
        try {
            secondProducer.start();
            for (int i = 0; i < 1000; i++) pool.execute(finished::countDown);
            secondProducer.join(5000);
            assertTrue(finished.await(5, TimeUnit.SECONDS));
        } finally {
            picker.shutdownNow();
        }
    }

    @Test public void renderLoaderAndOtherWorkersBothCompleteAtTheirOwnPriority() throws Exception {
        // Follow the normal startup order before constructing executors in this standalone test.
        assertTrue(ThreadPresetConfigEventHandler.getDefaultThreadCount()>0);
        PriorityTaskPicker picker=new PriorityTaskPicker();
        PriorityTaskPicker.Executor render=picker.createExecutor("Render Loader",Thread.NORM_PRIORITY+1,null,-1);
        PriorityTaskPicker.Executor io=picker.createExecutor("IO");
        try {
            int configured=Config.Common.MultiThreading.threadPriority.get();
            Future<Integer> renderPriority=render.submit(()->Thread.currentThread().getPriority());
            Future<Integer> ioPriority=io.submit(()->Thread.currentThread().getPriority());
            assertEquals(Math.max(Thread.MIN_PRIORITY,configured-1),(int)renderPriority.get(5,TimeUnit.SECONDS));
            assertEquals(configured,(int)ioPriority.get(5,TimeUnit.SECONDS));
        } finally {
            picker.shutdownNow();
        }
        assertTrue(render.isTerminated());
        assertTrue(io.isTerminated());
    }
}
