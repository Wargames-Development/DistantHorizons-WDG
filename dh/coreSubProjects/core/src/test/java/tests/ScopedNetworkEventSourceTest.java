package tests;

import com.seibel.distanthorizons.core.network.event.AbstractNetworkEventSource;
import com.seibel.distanthorizons.core.network.event.ScopedNetworkEventSource;
import com.seibel.distanthorizons.core.network.event.internal.CloseInternalEvent;
import com.seibel.distanthorizons.core.network.messages.AbstractNetworkMessage;
import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class ScopedNetworkEventSourceTest
{
	@Test
	public void eachLocalHandlerReceivesMessageOnce()
	{
		TestEventSource parent = new TestEventSource();
		ScopedNetworkEventSource scope = new ScopedNetworkEventSource(parent);
		AtomicInteger first = new AtomicInteger();
		AtomicInteger second = new AtomicInteger();
		scope.registerHandler(CloseInternalEvent.class, event -> first.incrementAndGet());
		scope.registerHandler(CloseInternalEvent.class, event -> second.incrementAndGet());

		parent.dispatch(new CloseInternalEvent());

		Assert.assertEquals(1, first.get());
		Assert.assertEquals(1, second.get());
	}

	@Test
	public void closingOneDimensionScopePreservesOtherScopesAndSessionHandlers()
	{
		TestEventSource parent = new TestEventSource();
		ScopedNetworkEventSource departed = new ScopedNetworkEventSource(parent);
		ScopedNetworkEventSource destination = new ScopedNetworkEventSource(parent);
		AtomicInteger departedCalls = new AtomicInteger();
		AtomicInteger destinationCalls = new AtomicInteger();
		AtomicInteger sessionCalls = new AtomicInteger();
		departed.registerHandler(CloseInternalEvent.class, event -> departedCalls.incrementAndGet());
		destination.registerHandler(CloseInternalEvent.class, event -> destinationCalls.incrementAndGet());
		parent.registerHandler(CloseInternalEvent.class, event -> sessionCalls.incrementAndGet());

		departed.close();
		departed.close();
		parent.dispatch(new CloseInternalEvent());

		Assert.assertEquals(0, departedCalls.get());
		Assert.assertEquals(1, destinationCalls.get());
		Assert.assertEquals(1, sessionCalls.get());
	}

	@Test
	public void closedScopeCannotRegisterNewHandlers()
	{
		TestEventSource parent = new TestEventSource();
		ScopedNetworkEventSource scope = new ScopedNetworkEventSource(parent);
		AtomicInteger calls = new AtomicInteger();
		scope.close();
		scope.registerHandler(CloseInternalEvent.class, event -> calls.incrementAndGet());
		parent.dispatch(new CloseInternalEvent());
		Assert.assertEquals(0, calls.get());
	}

	@Test
	public void dimensionCanReloadWithoutRetainingPreviousCallbacks()
	{
		TestEventSource parent = new TestEventSource();
		AtomicInteger staleCalls = new AtomicInteger();
		AtomicInteger activeCalls = new AtomicInteger();
		for (int i = 0; i < 50; i++)
		{
			ScopedNetworkEventSource scope = new ScopedNetworkEventSource(parent);
			scope.registerHandler(CloseInternalEvent.class, event -> staleCalls.incrementAndGet());
			scope.close();
		}
		ScopedNetworkEventSource reloaded = new ScopedNetworkEventSource(parent);
		reloaded.registerHandler(CloseInternalEvent.class, event -> activeCalls.incrementAndGet());
		parent.dispatch(new CloseInternalEvent());
		Assert.assertEquals(0, staleCalls.get());
		Assert.assertEquals(1, activeCalls.get());
	}

	private static final class TestEventSource extends AbstractNetworkEventSource
	{
		@Override
		public <T extends AbstractNetworkMessage> void registerHandler(Class<T> messageClass, Consumer<T> handler)
		{ this.registerHandler(this, messageClass, handler); }

		void dispatch(AbstractNetworkMessage message) { this.handleMessage(message); }
	}
}
