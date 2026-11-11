package com.seibel.distanthorizons.core;
import com.seibel.distanthorizons.api.enums.rendering.EDhApiBlockMaterial;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.dataObjects.transformers.FullDataOcclusionCuller;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.util.FullDataPointUtil;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import org.junit.Test;
import testItems.wrappers.TestBiomeWrapper;
import testItems.wrappers.TestBlockStateWrapper;
import static org.junit.Assert.*;
public class TreeOcclusionTest {
    @Test public void opaqueCanopyCannotReplaceStructuralTrunk() throws Exception {
        try(FullDataSourceV2 source=FullDataSourceV2.createEmpty(DhSectionPos.encode((byte)6,0,0))) {
            TestBiomeWrapper biome=new TestBiomeWrapper("tree");
            int wood=source.mapping.addIfNotPresentAndGetId(biome,new TestBlockStateWrapper("minecraft:log") { public byte getMaterialId(){return EDhApiBlockMaterial.WOOD.index;} });
            int leaves=source.mapping.addIfNotPresentAndGetId(biome,new TestBlockStateWrapper("minecraft:leaves") { public byte getMaterialId(){return EDhApiBlockMaterial.LEAVES.index;} });
            int ground=source.mapping.addIfNotPresentAndGetId(biome,new TestBlockStateWrapper("minecraft:stone"));
            source.dataPoints[FullDataSourceV2.relativePosToIndex(1,1)]=new LongArrayList(new long[]{
                FullDataPointUtil.encode(leaves,2,70,(byte)0,(byte)15),
                FullDataPointUtil.encode(wood,6,64,(byte)0,(byte)15),
                FullDataPointUtil.encode(ground,64,0,(byte)0,(byte)15)});
            for(int[] p:new int[][]{{0,1},{2,1},{1,0},{1,2}})
                source.dataPoints[FullDataSourceV2.relativePosToIndex(p[0],p[1])]=new LongArrayList(new long[]{FullDataPointUtil.encode(leaves,72,0,(byte)0,(byte)15)});
            FullDataOcclusionCuller.cullHiddenDatapointsInColumn(source,1,1);
            assertEquals(3,source.getColumnAtRelPos(1,1).size());
            assertEquals(wood,FullDataPointUtil.getId(source.getColumnAtRelPos(1,1).getLong(1)));
        }
    }
    @Test public void separatedOverheadCoverCannotHideAnExposedColumn() throws Exception {
        try(FullDataSourceV2 source=FullDataSourceV2.createEmpty(DhSectionPos.encode((byte)6,0,0))) {
            int stone=source.mapping.addIfNotPresentAndGetId(new TestBiomeWrapper("test"),new TestBlockStateWrapper("minecraft:stone"));
            java.lang.reflect.Method check=FullDataOcclusionCuller.class.getDeclaredMethod("checkOcclusion",FullDataSourceV2.class,long.class,LongArrayList.class,int.class);check.setAccessible(true);
            long trunk=FullDataPointUtil.encode(stone,6,64,(byte)0,(byte)15);
            LongArrayList above=new LongArrayList(new long[]{FullDataPointUtil.encode(stone,2,70,(byte)0,(byte)15)});
            assertTrue(((Integer)check.invoke(null,source,trunk,above,0))<0);
        }
    }
    @Test public void verticalReductionDoesNotExtendLeavesDownOverATrunk() {
        int wood=EDhApiBlockMaterial.WOOD.index,leaves=EDhApiBlockMaterial.LEAVES.index;
        com.seibel.distanthorizons.core.util.RenderDataPointReducingList reducer=new com.seibel.distanthorizons.core.util.RenderDataPointReducingList();
        long canopy=com.seibel.distanthorizons.core.util.RenderDataPointUtil.createDataPoint(255,30,150,30,72,70,15,0,leaves);
        long trunk=com.seibel.distanthorizons.core.util.RenderDataPointUtil.createDataPoint(255,100,60,30,70,64,15,0,wood);
        long ground=com.seibel.distanthorizons.core.util.RenderDataPointUtil.createDataPoint(255,90,90,90,64,0,15,0,0);
        // An unrelated small pair of stone intervals can merge instead of changing the trunk material.
        long floating=com.seibel.distanthorizons.core.util.RenderDataPointUtil.createDataPoint(255,90,90,90,76,73,15,0,0);
        long floating2=com.seibel.distanthorizons.core.util.RenderDataPointUtil.createDataPoint(255,80,80,80,79,76,15,0,0);
        try(com.seibel.distanthorizons.core.dataObjects.render.columnViews.ColumnRenderView input=
                com.seibel.distanthorizons.core.dataObjects.render.columnViews.ColumnRenderView.getPooled(
                        new LongArrayList(new long[]{floating2,floating,canopy,trunk,ground}),new LongArrayList(new long[]{0}),5,0,0,5)) {
            reducer.populate(input);reducer.reduce(4);
            boolean found=false;
            for(int n=reducer.getHighest();n!=com.seibel.distanthorizons.core.util.RenderDataPointReducingList.NULL;n=reducer.getLower(n)) {
                long value=reducer.getData(n);
                if(com.seibel.distanthorizons.core.util.RenderDataPointUtil.getYMin(value)==64) {
                    assertEquals(wood,com.seibel.distanthorizons.core.util.RenderDataPointUtil.getBlockMaterialId(value));found=true;
                }
            }
            assertTrue(found);
        }
    }
}
