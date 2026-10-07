package com.seibel.distanthorizons.core.world;

import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

public class LevelShutdownTest
{
	@Test(timeout = 5000)
	public void successfulCloseSignalsCompletion() throws Exception
	{
		AtomicBoolean closed = new AtomicBoolean();
		LevelShutdown.closeAsync(() -> closed.set(true)).get(2, TimeUnit.SECONDS);
		Assert.assertTrue(closed.get());
	}

	@Test(timeout = 5000)
	public void throwingCloseSignalsOriginalException() throws Exception
	{
		RuntimeException expected = new IllegalStateException("simulated close failure");
		assertCloseFailure(expected);
	}

	@Test(timeout = 5000)
	public void errorDuringCloseAlsoSignalsCompletion() throws Exception
	{
		assertCloseFailure(new AssertionError("simulated close error"));
	}

	private static void assertCloseFailure(Throwable expected) throws Exception
	{
		CompletableFuture<Void> completion = LevelShutdown.closeAsync(() ->
		{
			if (expected instanceof Error) { throw (Error) expected; }
			throw (RuntimeException) expected;
		});
		try
		{
			completion.get(2, TimeUnit.SECONDS);
			Assert.fail("A failed close must complete exceptionally.");
		}
		catch (ExecutionException failure)
		{
			Assert.assertSame(expected, failure.getCause());
		}
	}

	@Test(timeout = 5000)
	public void failedLevelDoesNotSkipWaitingForOtherLevelsOrWorldCleanup() throws Exception
	{
		CompletableFuture<Void> failed = LevelShutdown.closeAsync(() ->
		{ throw new IllegalStateException("simulated failed level"); });
		CompletableFuture<Void> pending = new CompletableFuture<>();
		CountDownLatch waitingStarted = new CountDownLatch(1);
		AtomicBoolean worldCleaned = new AtomicBoolean();
		CompletableFuture<Void> worldClose = LevelShutdown.closeAsync(() ->
		{
			waitingStarted.countDown();
			LevelShutdown.awaitAll(Arrays.asList(failed, pending));
			worldCleaned.set(true);
		});
		try
		{
			Assert.assertTrue(waitingStarted.await(2, TimeUnit.SECONDS));
			try
			{
				worldClose.get(100, TimeUnit.MILLISECONDS);
				Assert.fail("World close must still wait for the other level.");
			}
			catch (TimeoutException expected) { }
			Assert.assertFalse(worldCleaned.get());
		}
		finally
		{
			pending.complete(null);
			worldClose.get(2, TimeUnit.SECONDS);
		}
		Assert.assertTrue(worldCleaned.get());
	}

	@Test(timeout = 5000)
	public void cancelledLevelDoesNotPreventWaitingForRemainingLevels() throws Exception
	{
		CompletableFuture<Void> cancelled = new CompletableFuture<>();
		cancelled.cancel(false);
		AtomicBoolean closed = new AtomicBoolean();
		CompletableFuture<Void> other = LevelShutdown.closeAsync(() -> closed.set(true));
		LevelShutdown.awaitAll(Arrays.asList(cancelled, other));
		Assert.assertTrue(closed.get());
	}
}
