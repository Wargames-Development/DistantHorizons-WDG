package com.seibel.distanthorizons.core;

import com.seibel.distanthorizons.api.enums.rendering.EDhApiBlockMaterial;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.dataObjects.render.columnViews.ColumnRenderView;
import com.seibel.distanthorizons.core.dataObjects.transformers.FullDataToRenderDataTransformer;
import com.seibel.distanthorizons.core.dataObjects.transformers.NtmLodPolicy;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.pos.blockPos.DhBlockPosMutable;
import com.seibel.distanthorizons.core.util.FullDataPointUtil;
import com.seibel.distanthorizons.core.util.RenderDataPointUtil;
import com.seibel.distanthorizons.core.wrapperInterfaces.IWrapperFactory;
import com.seibel.distanthorizons.core.wrapperInterfaces.block.IBlockStateWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.*;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.lang.reflect.*;
import org.junit.Test;
import static org.junit.Assert.*;
import testItems.wrappers.TestBlockStateWrapper;

public class NtmStructureLodTest {
    private static final IBlockStateWrapper AIR = block("minecraft:air", EDhApiBlockMaterial.AIR, false, false);
    private static final IBlockStateWrapper WATER = block("minecraft:water", EDhApiBlockMaterial.WATER, false, true);
    private static final IBlockStateWrapper STONE = block("minecraft:stone", EDhApiBlockMaterial.STONE, true, false);
    private static final ObjectOpenHashSet<IBlockStateWrapper> EMPTY = new ObjectOpenHashSet<>();
    private static final ObjectOpenHashSet<IBlockStateWrapper> IGNORED = new ObjectOpenHashSet<>();
    private static final IWrapperFactory FACTORY;
    static {
        IGNORED.add(AIR);
        FACTORY = proxy(IWrapperFactory.class, (p,m,a) -> {
            if (m.getName().equals("getDelayedSetupComplete")) return true;
            if (m.getName().equals("getRendererIgnoredBlocks")) return IGNORED;
            if (m.getName().startsWith("get") && m.getReturnType() == ObjectOpenHashSet.class) return EMPTY;
            if (m.getName().equals("getWaterBlockStateWrapper")) return WATER;
            if (m.getName().equals("deserializeBlockStateWrapperOrGetDefault")) return AIR;
            return defaultValue(m.getReturnType());
        });
        SingletonInjector.INSTANCE.bind(IWrapperFactory.class, FACTORY);
    }
    @org.junit.AfterClass public static void releaseFactory() {
        SingletonInjector.INSTANCE.unbind(IWrapperFactory.class, FACTORY);
    }
    private static IBlockStateWrapper block(String name, EDhApiBlockMaterial material, boolean solid, boolean liquid) {
        return new TestBlockStateWrapper(name) {
            @Override public boolean isAir() { return material == EDhApiBlockMaterial.AIR; }
            @Override public boolean isSolid() { return solid; }
            @Override public boolean isLiquid() { return liquid; }
            @Override public byte getMaterialId() { return material.index; }
            @Override public int getOpacity() { return solid ? 15 : liquid ? 1 : 0; }
        };
    }
    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == double.class) return 1D;
        return null;
    }
    @SuppressWarnings("unchecked") private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }
    private long[] convert(String roof, boolean culling) throws Exception {
        boolean oldCulling = Config.Client.Advanced.Graphics.Culling.enableCaveCulling.get();
        int oldHeight = Config.Client.Advanced.Graphics.Culling.caveCullingHeight.get();
        try {
            Config.Client.Advanced.Graphics.Culling.enableCaveCulling.setWithoutFiringEvents(culling);
            Config.Client.Advanced.Graphics.Culling.caveCullingHeight.setWithoutFiringEvents(60);
            IDimensionTypeWrapper dimension = proxy(IDimensionTypeWrapper.class, (p,m,a) -> defaultValue(m.getReturnType()));
            IClientLevelWrapper level = proxy(IClientLevelWrapper.class, (p,m,a) -> {
                if (m.getName().equals("getDimensionType")) return dimension;
                if (m.getName().equals("getBlockColor")) return a[a.length-1] == WATER ? 0x80204080 : 0xFF808080;
                return defaultValue(m.getReturnType());
            });
            IBiomeWrapper biome = proxy(IBiomeWrapper.class, (p,m,a) -> {
                if (m.getName().equals("hashCode")) return 0;
                if (m.getName().equals("equals")) return p == a[0];
                return defaultValue(m.getReturnType());
            });
            try (FullDataSourceV2 source = FullDataSourceV2.createEmpty(DhSectionPos.encode((byte)6,0,0));
                 ColumnRenderView result = ColumnRenderView.getPooled(
                     new LongArrayList(new long[8]), new LongArrayList(new long[1]), 0,0,0,8)) {
                IBlockStateWrapper top = block(roof, EDhApiBlockMaterial.METAL, true, false);
                LongArrayList input = new LongArrayList();
                // A one-block deck above a shaded gap, ocean and seabed. Both air and water
                // have zero skylight, reproducing the old cave-culling roof extension.
                input.add(FullDataPointUtil.encode(source.mapping.addIfNotPresentAndGetId(biome,top),1,50,(byte)0,(byte)15));
                input.add(FullDataPointUtil.encode(source.mapping.addIfNotPresentAndGetId(biome,AIR),6,44,(byte)0,(byte)0));
                input.add(FullDataPointUtil.encode(source.mapping.addIfNotPresentAndGetId(biome,WATER),4,40,(byte)0,(byte)0));
                input.add(FullDataPointUtil.encode(source.mapping.addIfNotPresentAndGetId(biome,STONE),40,0,(byte)0,(byte)0));
                Method convert = FullDataToRenderDataTransformer.class.getDeclaredMethod("setRenderColumnView",
                    IClientLevelWrapper.class,FullDataSourceV2.class,int.class,int.class,
                    ColumnRenderView.class,LongArrayList.class,DhBlockPosMutable.class);
                convert.setAccessible(true);
                convert.invoke(null,level,source,0,0,result,input,new DhBlockPosMutable(0,0,0));
                return new long[]{result.get(0), result.get(1)};
            }
        } finally {
            Config.Client.Advanced.Graphics.Culling.enableCaveCulling.setWithoutFiringEvents(oldCulling);
            Config.Client.Advanced.Graphics.Culling.caveCullingHeight.setWithoutFiringEvents(oldHeight);
        }
    }
    @Test public void oilRigDeckAndFluidTankKeepTheirUndersideAndOcean() throws Exception {
        for (String roof : new String[]{"hbm:tile.deco_steel", "hbm:tile.machine_fluidtank:13"}) {
            long[] result = convert(roof,true);
            assertEquals(51,RenderDataPointUtil.getYMax(result[0]));
            assertEquals(50,RenderDataPointUtil.getYMin(result[0]));
            assertEquals(44,RenderDataPointUtil.getYMax(result[1]));
            assertEquals(40,RenderDataPointUtil.getYMin(result[1]));
            assertEquals(EDhApiBlockMaterial.WATER.index,RenderDataPointUtil.getBlockMaterialId(result[1]));
        }
    }
    @Test public void ordinaryCaveCullingAndDisabledCullingAreUnchanged() throws Exception {
        assertEquals(40,RenderDataPointUtil.getYMin(convert("minecraft:iron_block",true)[0]));
        assertEquals(50,RenderDataPointUtil.getYMin(convert("minecraft:iron_block",false)[0]));
    }
    @Test public void pipeFacesFollowRendererMetadataOnAllThreeAxes() {
        for (String pipe : new String[]{"hbm:tile.deco_pipe", "hbm:tile.deco_pipe_framed_red",
                "hbm:tile.deco_pipe_quad_rusted", "hbm:tile.deco_pipe_rim_marked:8"}) {
            for (int meta=0;meta<16;meta++) for(int face=0;face<6;face++) {
                int expected = meta==4 ? (face>=4 ? 1 : 2)
                    : meta==8 ? (face==2 || face==3 ? 1 : 2) : face;
                assertEquals(expected,NtmLodPolicy.iconSide(pipe,face,meta));
                assertEquals(2,NtmLodPolicy.iconSide(pipe,NtmLodPolicy.representativeSide(pipe,meta),meta));
                assertEquals(face,NtmLodPolicy.iconSide("other:tile.deco_pipe_red",face,meta));
                assertEquals(face,NtmLodPolicy.iconSide("hbm:tile.machine_fluidtank",face,meta));
            }
        }
        assertFalse(NtmLodPolicy.preserveUnderside("hbm:tile.logic_block_invis",EDhApiBlockMaterial.METAL.index));
        assertFalse(NtmLodPolicy.preserveUnderside("hbm:tile.fluid_oil",EDhApiBlockMaterial.WATER.index));
    }
}
