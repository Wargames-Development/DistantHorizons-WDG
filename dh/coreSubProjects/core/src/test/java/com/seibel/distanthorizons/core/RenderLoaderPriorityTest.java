package com.seibel.distanthorizons.core;

import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.config.eventHandlers.presets.ThreadPresetConfigEventHandler;
import com.seibel.distanthorizons.core.util.threading.PriorityTaskPicker;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RenderLoaderPriorityTest {
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
