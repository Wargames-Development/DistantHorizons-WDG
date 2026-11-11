package com.seibel.distanthorizons.core;

import com.seibel.distanthorizons.core.dataObjects.render.TerrainCoverage;
import com.seibel.distanthorizons.core.dataObjects.render.bufferBuilding.LodBufferContainer;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.render.RenderBufferHandler;
import com.seibel.distanthorizons.core.render.RenderParams;
import com.seibel.distanthorizons.core.render.TerrainBuildState;
import com.seibel.distanthorizons.core.render.QuadTree.LodRenderSection;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.lang.reflect.Field;
import org.junit.Test;
import static org.junit.Assert.*;

/** Exercise the real actor coverage query without a GL context or terrain worker. */
public class RenderedTerrainCoverageTest {
    private static final Object ALLOCATOR;
    static {
        try { Field field=Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");field.setAccessible(true);ALLOCATOR=field.get(null); }
        catch(Exception error) { throw new AssertionError(error); }
    }
    private static <T> T allocate(Class<T> type) throws Exception { return type.cast(ALLOCATOR.getClass().getMethod("allocateInstance",Class.class).invoke(ALLOCATOR,type)); }
    private static void set(Object object,String name,Object value) throws Exception {
        Field field=object.getClass().getDeclaredField(name);field.setAccessible(true);field.set(object,value);
    }
    @Test public void pendingRebuildUsesDisplayedMeshUntilReplacementIsSelected() throws Exception {
        RenderBufferHandler handler=allocate(RenderBufferHandler.class);
        RenderParams view=new RenderParams();
        LodRenderSection section=allocate(LodRenderSection.class);
        long pos=DhSectionPos.encode(DhSectionPos.SECTION_BLOCK_DETAIL_LEVEL,0,0);
        LodBufferContainer old=allocate(LodBufferContainer.class),replacement=allocate(LodBufferContainer.class);
        long[] rows=new long[64];java.util.Arrays.fill(rows,-1L);
        set(old,"terrainCoverage",TerrainCoverage.copyOf(rows));old.buffersUploaded=true;
        set(replacement,"terrainCoverage",TerrainCoverage.UNKNOWN);replacement.buffersUploaded=true;
        set(section,"pos",pos);section.setRenderingEnabled(true);section.renderBufferContainer=old;
        set(section,"buildState",new TerrainBuildState());
        Long2ObjectOpenHashMap<LodRenderSection> sections=new Long2ObjectOpenHashMap<>();sections.put(pos,section);
        Long2ObjectOpenHashMap<LodBufferContainer> buffers=new Long2ObjectOpenHashMap<>();buffers.put(pos,old);
        set(handler,"coverageSections",sections);set(handler,"coverageBuffers",buffers);
        set(handler,"coverageIndexReady",true);set(handler,"coverageView",view);set(handler,"coverageGeneration",1L);
        assertTrue(handler.isTerrainChunkKnown(view.apiCopy,1,0,0));
        section.markRenderDataDirty();
        assertTrue("Source invalidation must not hide actors against the still-displayed mesh",handler.isTerrainChunkKnown(view.apiCopy,1,0,0));
        assertFalse("Previous view generation cannot authorize coverage",handler.isTerrainChunkKnown(view.apiCopy,2,0,0));
        assertFalse(handler.isTerrainChunkKnown(new RenderParams().apiCopy,1,0,0));
        old.buffersUploaded=false;
        assertFalse("A released mesh is no longer coverage",handler.isTerrainChunkKnown(view.apiCopy,1,0,0));
        old.buffersUploaded=true;section.renderBufferContainer=replacement;
        assertFalse("A replacement cannot use the old buffer's provenance",handler.isTerrainChunkKnown(view.apiCopy,1,0,0));
        buffers.put(pos,replacement);
        assertFalse("Missing source columns stay unknown after replacement",handler.isTerrainChunkKnown(view.apiCopy,1,0,0));
        set(replacement,"terrainCoverage",TerrainCoverage.copyOf(rows));
        assertTrue("New complete mesh becomes coverage when selected",handler.isTerrainChunkKnown(view.apiCopy,1,0,0));
        section.setRenderingEnabled(false);
        assertFalse("Disabled terrain cannot authorize rendering",handler.isTerrainChunkKnown(view.apiCopy,1,0,0));
        section.setRenderingEnabled(true);section.renderBufferContainer=null;
        assertFalse("No terrain mesh still withholds actors",handler.isTerrainChunkKnown(view.apiCopy,1,0,0));
    }
}
