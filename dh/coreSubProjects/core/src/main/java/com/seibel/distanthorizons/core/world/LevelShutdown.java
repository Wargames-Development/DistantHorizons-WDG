package com.seibel.distanthorizons.core.world;

import com.seibel.distanthorizons.core.logging.DhLogger;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/** Shared completion handling for the three world shutdown paths. */
final class LevelShutdown
{
	private static final DhLogger LOGGER = new DhLoggerBuilder().build();

	private LevelShutdown() { }

	static CompletableFuture<Void> closeAsync(Runnable closeAction)
	{
		CompletableFuture<Void> completion = new CompletableFuture<>();
		try
		{
			Thread closeThread = new Thread(() ->
			{
				try
				{
					closeAction.run();
					completion.complete(null);
				}
				catch (Throwable failure)
				{
					// Errors must also signal completion, otherwise the world waits forever.
					completion.completeExceptionally(failure);
				}
			}, "level shutdown");
			closeThread.start();
		}
		catch (Throwable failure)
		{
			completion.completeExceptionally(failure);
		}
		return completion;
	}

	static void awaitAll(Iterable<CompletableFuture<Void>> completions)
	{
		for (CompletableFuture<Void> completion : completions)
		{
			try
			{
				completion.join();
			}
			catch (CompletionException failure)
			{
				LOGGER.error("Failed to close a DH level during world shutdown.", failure.getCause());
			}
			catch (CancellationException failure)
			{
				LOGGER.error("DH level close was cancelled during world shutdown.", failure);
			}
		}
	}
}
