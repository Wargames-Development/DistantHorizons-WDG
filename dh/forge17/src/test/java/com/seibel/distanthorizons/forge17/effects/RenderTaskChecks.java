package com.seibel.distanthorizons.forge17.effects;

import com.seibel.distanthorizons.core.dataObjects.render.bufferBuilding.LodBufferContainer;
import com.seibel.distanthorizons.core.dataObjects.render.TerrainCoverage;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.render.RenderThreadTaskHandler;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftClientWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftRenderWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftSharedWrapper;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.concurrent.TimeUnit;

final class RenderTaskChecks {
    private RenderTaskChecks() { }

    static int run() {
        try {
            List<Runnable> scheduled = new ArrayList<>();
            SingletonInjector.INSTANCE.bind(IMinecraftSharedWrapper.class, stub(IMinecraftSharedWrapper.class, scheduled));
            SingletonInjector.INSTANCE.bind(IMinecraftClientWrapper.class, stub(IMinecraftClientWrapper.class, scheduled));
            SingletonInjector.INSTANCE.bind(IMinecraftRenderWrapper.class, stub(IMinecraftRenderWrapper.class, scheduled));
            RenderThreadTaskHandler handler = RenderThreadTaskHandler.INSTANCE;
            // Invoke the recovery timer explicitly so assertions have no timing race.
            ((Timer)field("TIMER").get(null)).cancel();
            Method drain = RenderThreadTaskHandler.class.getDeclaredMethod("runRenderThreadTasks", long.class);
            drain.setAccessible(true);
            List<Integer> executed = new ArrayList<>();
            for (int i = 0; i < 70; i++) {
                int id = i;
                handler.queueRunningOnRenderThread("fixture", () -> executed.add(id));
            }
            drain.invoke(handler, Long.MAX_VALUE);
            require(executed.size() == 64, "hard task count bound");
            drain.invoke(handler, 0L);
            require(executed.size() == 65, "at least one FIFO task makes progress");
            drain.invoke(handler, Long.MAX_VALUE);
            for (int i = 0; i < 70; i++) require(executed.get(i) == i, "no job is lost or reordered");

            Method cleanup = RenderThreadTaskHandler.class.getDeclaredMethod("manualCleanupTick");
            cleanup.setAccessible(true);
            field("lastRenderDrainNanos").setLong(handler, System.nanoTime() - TimeUnit.SECONDS.toNanos(2));
            cleanup.invoke(handler);
            require(scheduled.isEmpty(), "idle recovery skips an empty queue");
            handler.queueRunningOnRenderThread("fixture", () -> executed.add(70));
            cleanup.invoke(handler);
            cleanup.invoke(handler);
            require(scheduled.size() == 1, "idle callbacks coalesce");
            handler.runRenderThreadTasks();
            handler.queueRunningOnRenderThread("fixture", () -> executed.add(71));
            scheduled.remove(0).run();
            require(executed.size() == 71, "queued recovery yields when foreground rendering resumes");
            drain.invoke(handler, Long.MAX_VALUE);
            require(executed.size() == 72, "foreground drain still sees the queued job");
            require(LodBufferContainer.tryMakeAndUploadBuffersAsync(0, null, null, null, TerrainCoverage.UNKNOWN,
                    () -> true).isCancelled(), "cancelled mesh never starts GPU setup");
            return 77;
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Render task fixture", failure);
        }
    }

    private static Field field(String name) throws ReflectiveOperationException {
        Field field = RenderThreadTaskHandler.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static <T> T stub(Class<T> type, List<Runnable> scheduled) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, (proxy, method, arguments) -> {
            if (method.getName().equals("executeOnRenderThread")) scheduled.add((Runnable) arguments[0]);
            if (method.getName().equals("getDelayedSetupComplete")) return true;
            if (method.getReturnType() == boolean.class) return false;
            if (method.getReturnType() == int.class) return 240;
            return null;
        }));
    }

    private static void require(boolean valid, String description) {
        if (!valid) throw new AssertionError(description);
    }
}
