package com.seibel.distanthorizons.core.api.internal;

import com.seibel.distanthorizons.core.generation.WorldBorderBounds;
import com.seibel.distanthorizons.core.pos.DhChunkPos;
import com.seibel.distanthorizons.core.wrapperInterfaces.chunk.IChunkWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IClientLevelWrapper;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.Test;
import static org.junit.Assert.*;

public class ChunkIntakeTest {
    @Test
    public void onlyAcceptedOrWaitingUpdatesTakeCallerOwnedSnapshot() {
        IClientLevelWrapper level = (IClientLevelWrapper) Proxy.newProxyInstance(
            IClientLevelWrapper.class.getClassLoader(), new Class<?>[]{IClientLevelWrapper.class},
            (proxy, method, args) -> {
                if ("getLodGenerationBounds".equals(method.getName())) return new WorldBorderBounds(0, 0, 16, 16);
                if ("hashCode".equals(method.getName())) return System.identityHashCode(proxy);
                if ("equals".equals(method.getName())) return proxy == args[0];
                return null;
            });
        DhChunkPos acceptedPos = new DhChunkPos(0, 0);
        IChunkWrapper snapshot = chunk(acceptedPos);
        IChunkWrapper replacement = chunk(new DhChunkPos(0, 0));
        AtomicInteger snapshots = new AtomicInteger();
        Thread caller = Thread.currentThread();
        Supplier<IChunkWrapper> createSnapshot = () -> {
            assertSame(caller, Thread.currentThread());
            snapshots.incrementAndGet();
            return snapshot;
        };
        ClientApi.WaitingChunkKey key = new ClientApi.WaitingChunkKey(level, acceptedPos);
        try {
            assertNull(SharedApi.getAbstractDhWorld());
            SharedApi.INSTANCE.applyChunkUpdate(new DhChunkPos(1, 0), level, true, createSnapshot);
            assertEquals(0, snapshots.get());
            SharedApi.INSTANCE.applyChunkUpdate(acceptedPos, level, true, createSnapshot);
            assertEquals(1, snapshots.get());
            assertSame(snapshot, ClientApi.INSTANCE.waitingChunks.get(key));
            SharedApi.INSTANCE.applyChunkUpdate(replacement, level, true);
            assertSame(replacement, ClientApi.INSTANCE.waitingChunks.get(key));
            assertFalse(ClientApi.INSTANCE.waitingChunks.containsValue(snapshot));
        } finally {
            ClientApi.INSTANCE.waitingChunks.remove(key);
        }
    }

    private static IChunkWrapper chunk(DhChunkPos pos) {
        return (IChunkWrapper) Proxy.newProxyInstance(
            IChunkWrapper.class.getClassLoader(), new Class<?>[]{IChunkWrapper.class},
            (proxy, method, args) -> {
                if ("equals".equals(method.getName())) return proxy == args[0];
                if ("getChunkPos".equals(method.getName())) return pos;
                if ("toString".equals(method.getName())) return pos.toString();
                return null;
            });
    }
}
