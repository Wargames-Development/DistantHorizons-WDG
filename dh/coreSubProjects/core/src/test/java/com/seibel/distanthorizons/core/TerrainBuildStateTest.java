package com.seibel.distanthorizons.core;

import com.seibel.distanthorizons.core.render.TerrainBuildState;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import static org.junit.Assert.*;

public class TerrainBuildStateTest {
    @Test public void terrainUpdateDuringBuildSurvivesOldUpload() {
        TerrainBuildState state=new TerrainBuildState();
        long old=state.begin();state.invalidate();
        assertFalse(state.complete(old));assertTrue(state.isDirty());
        assertTrue(state.complete(state.begin()));assertFalse(state.isDirty());
    }
    @Test public void oldCompletionCannotClearAnotherRebuildsInvalidation() {
        TerrainBuildState state=new TerrainBuildState();state.invalidate();long old=state.begin();
        state.invalidate();long newer=state.begin();state.invalidate();
        assertFalse(state.complete(newer));assertFalse(state.complete(old));assertTrue(state.isDirty());
        assertTrue(state.complete(state.begin()));assertFalse(state.isDirty());
    }
    @Test public void crossThreadInvalidationPublishesBeforeStaleCompletion() throws Exception {
        TerrainBuildState state=new TerrainBuildState();long old=state.begin();
        CountDownLatch changed=new CountDownLatch(1);
        Thread update=new Thread(()-> { state.invalidate();changed.countDown(); });update.start();
        assertTrue(changed.await(5,TimeUnit.SECONDS));update.join(5000);
        assertFalse(update.isAlive());assertTrue(state.isDirty());assertFalse(state.complete(old));assertTrue(state.isDirty());
    }
}
