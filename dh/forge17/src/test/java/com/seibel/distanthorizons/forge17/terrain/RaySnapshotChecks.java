package com.seibel.distanthorizons.forge17.terrain;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.api.enums.config.EDhApiWorldCompressionMode;
import com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiWorldGenerationStep;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.util.FullDataPointUtil;
import com.seibel.distanthorizons.core.util.objects.DataCorruptedException;
import com.seibel.distanthorizons.core.wrapperInterfaces.block.IBlockStateWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IBiomeWrapper;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.lang.reflect.*;
public final class RaySnapshotChecks {
    private static Object proxy(Class<?> type,boolean air) {
        return Proxy.newProxyInstance(type.getClassLoader(),new Class[]{type},(p,m,a)->{
            if(m.getName().equals("isAir"))return air;
            if(m.getName().equals("getSerialString"))return type.getSimpleName()+air;
            if(m.getName().equals("hashCode"))return System.identityHashCode(p);
            if(m.getName().equals("equals"))return p==a[0];
            if(m.getReturnType()==boolean.class)return false;
            if(m.getReturnType()==int.class)return 0;
            if(m.getReturnType()==byte.class)return (byte)0;
            return null;
        });
    }
    public static int run() {
        try {
        Class<?> type=Class.forName(DhTerrainRayApi.class.getName()+"$Cache");
        Constructor<?> constructor=type.getDeclaredConstructor();constructor.setAccessible(true);Object cache=constructor.newInstance();
        Method copy=type.getDeclaredMethod("copy",FullDataSourceV2.class);copy.setAccessible(true);
        try(FullDataSourceV2 source=FullDataSourceV2.createEmpty(DhSectionPos.encode((byte)6,0,0))) {
            IBiomeWrapper biome=(IBiomeWrapper)proxy(IBiomeWrapper.class,false);
            int air=source.mapping.addIfNotPresentAndGetId(biome,(IBlockStateWrapper)proxy(IBlockStateWrapper.class,true));
            int partial=source.mapping.addIfNotPresentAndGetId(biome,(IBlockStateWrapper)proxy(IBlockStateWrapper.class,false));
            source.dataPoints[0]=new LongArrayList(new long[]{FullDataPointUtil.encode(air,512,0,(byte)0,(byte)15)});
            source.dataPoints[1]=new LongArrayList(new long[]{FullDataPointUtil.encode(partial,1,320,(byte)0,(byte)15)});
            source.columnGenerationSteps.set(0,(byte)9);source.columnGenerationSteps.set(1,(byte)9);
            source.columnWorldCompressionMode.set(0,(byte)1);source.columnWorldCompressionMode.set(1,(byte)1);
            long[][] snapshot=(long[][])copy.invoke(cache,source);
            require(snapshot[0]!=null && (snapshot[0][0]&3)==1,"air sentinel is copied without dereferencing a missing native block state");
            require(snapshot[1]!=null && (snapshot[1][0]&3)==0,"unsupported collision remains unknown");
            require(snapshot[2]==null,"missing column cannot become known sky");
            source.dataPoints[0].clear();require(snapshot[0].length==1,"snapshot does not retain mutable source columns");
        }
        try(FullDataSourceV2 coarse=FullDataSourceV2.createEmpty(DhSectionPos.encode((byte)7,0,0))) {
            require(copy.invoke(cache,coarse)==null,"coarse LODs cannot authorize first-hit targeting");
        }
        Class<?> entryType=Class.forName(DhTerrainRayApi.class.getName()+"$Entry");
        Constructor<?> entryConstructor=entryType.getDeclaredConstructor(java.util.concurrent.CompletableFuture.class);entryConstructor.setAccessible(true);
        java.util.concurrent.CompletableFuture<FullDataSourceV2> pending=new java.util.concurrent.CompletableFuture<>();
        Object entry=entryConstructor.newInstance(pending);
        Field created=entryType.getDeclaredField("created"),columns=entryType.getDeclaredField("columns"),future=entryType.getDeclaredField("future");
        created.setAccessible(true);columns.setAccessible(true);future.setAccessible(true);created.setLong(entry,0L);columns.set(entry,new long[4096][]);
        Method usable=entryType.getDeclaredMethod("usable",long.class),due=entryType.getDeclaredMethod("refreshDue",long.class);usable.setAccessible(true);due.setAccessible(true);
        require((Boolean)usable.invoke(entry,1_500_000_000L),"pending refresh retains a still-valid snapshot");
        require(!(Boolean)usable.invoke(entry,2_000_000_001L),"slow refresh cannot extend the two-second validity bound");
        require(!(Boolean)due.invoke(entry,1_500_000_000L),"pending refresh is not duplicated");
        future.set(entry,null);require((Boolean)due.invoke(entry,1_000_000_000L),"refresh starts before the validity deadline");
        Method adopt=type.getDeclaredMethod("adopt",entryType,long.class);adopt.setAccessible(true);
        Field conversions=type.getDeclaredField("conversions");conversions.setAccessible(true);conversions.setInt(cache,0);
        future.set(entry,java.util.concurrent.CompletableFuture.completedFuture(null));adopt.invoke(cache,entry,1_500_000_000L);
        require(((long[][])columns.get(entry)).length==0 && future.get(entry)==null,"missing replacement invalidates old terrain and consumes pending future");
        Object older=entryConstructor.newInstance(java.util.concurrent.CompletableFuture.completedFuture(null));
        Object newer=entryConstructor.newInstance(java.util.concurrent.CompletableFuture.completedFuture(null));
        created.setLong(older,1);created.setLong(newer,2);
        Field entries=type.getDeclaredField("entries");entries.setAccessible(true);
        java.util.Map<Long,Object> queued=(java.util.Map<Long,Object>)entries.get(cache);queued.put(1L,older);queued.put(2L,newer);
        conversions.setInt(cache,0);
        Method ready=type.getDeclaredMethod("adoptReady",entryType,long.class);ready.setAccessible(true);ready.invoke(cache,newer,10L);
        require(future.get(older)==null && future.get(newer)!=null,"completed older snapshots progress even when the ray first queries a newer one");
        ready.invoke(cache,newer,11L);
        require(future.get(newer)!=null,"oldest-first adoption retains the one-conversion budget");
        double[] hit=new double[3];
        int path=2+(15<<5),lower=2+(8<<5),upper=2+8+(16<<5);
        TerrainRayWalk.Cells paths=(x,y,z)->y==0?path:0;
        require(TerrainRayWalk.trace(.5,2,.5,.5,-1,.5,paths,hit)==DhTerrainRayApi.Result.BLOCKED && Math.abs(hit[1]-.9375)<1e-9,"path designation hits its actual 15/16 surface");
        require(TerrainRayWalk.trace(-1,.97,.5,2,.97,.5,paths,hit)==DhTerrainRayApi.Result.CLEAR,"laser passes over the empty strip above a path");
        require(TerrainRayWalk.trace(.5,2,.5,.5,-1,.5,(x,y,z)->y==0?lower:0,hit)==DhTerrainRayApi.Result.BLOCKED && Math.abs(hit[1]-.5)<1e-9,"lower slab surface is targetable");
        require(TerrainRayWalk.trace(.5,-1,.5,.5,2,.5,(x,y,z)->y==0?upper:0,hit)==DhTerrainRayApi.Result.BLOCKED && Math.abs(hit[1]-.5)<1e-9,"upper slab underside is targetable");
        require(TerrainRayWalk.trace(-1,.25,.5,2,.25,.5,(x,y,z)->y==0?upper:0,hit)==DhTerrainRayApi.Result.CLEAR,"upper slab air does not invent a wall");
        int[][] airCubes={{2,6},{3,7},{2,3},{6,7},{0,4},{1,5},{0,1},{4,5}};
        for(int meta=0;meta<8;meta++) for(int cube=0;cube<8;cube++) {
            final int shape=1024+meta;
            double x=.25+.5*(cube&1),y=.25+.5*((cube>>>1)&1),z=.25+.5*((cube>>>2)&1);
            boolean empty=cube==airCubes[meta][0] || cube==airCubes[meta][1];
            require(TerrainRayWalk.trace(x,y,z,x+.01,y,z,(cx,cy,cz)->shape,hit)==(empty?DhTerrainRayApi.Result.CLEAR:DhTerrainRayApi.Result.BLOCKED),"stairs preserve all eight native ray-trace orientations");
        }
        return 81 + radarSnapshots();
        } catch(Exception failure) { throw new AssertionError("DH terrain snapshot failure",failure); }
    }

    private static int radarSnapshots() throws DataCorruptedException {
        try (FullDataSourceV2 source = FullDataSourceV2.createEmpty(
                DhSectionPos.encode(DhSectionPos.SECTION_BLOCK_DETAIL_LEVEL, -1, 0))) {
            IBiomeWrapper biome = (IBiomeWrapper) proxy(IBiomeWrapper.class, false);
            int stone = source.mapping.addIfNotPresentAndGetId(biome, (IBlockStateWrapper) proxy(IBlockStateWrapper.class, false));
            int air = source.mapping.addIfNotPresentAndGetId(biome, (IBlockStateWrapper) proxy(IBlockStateWrapper.class, true));
            long stoneRun = FullDataPointUtil.encode(stone, 20, 320, (byte) 0, (byte) 0);
            long airRun = FullDataPointUtil.encode(air, 20, 320, (byte) 0, (byte) 0);
            EDhApiWorldGenerationStep light = EDhApiWorldGenerationStep.LIGHT;
            EDhApiWorldCompressionMode exact = EDhApiWorldCompressionMode.MERGE_SAME_BLOCKS;
            source.setSingleColumn(new LongArrayList(new long[] {stoneRun}), 63, 0, light, exact);
            source.setSingleColumn(new LongArrayList(new long[] {stoneRun}), 1, 0, light, EDhApiWorldCompressionMode.VISUALLY_EQUAL);
            source.setSingleColumn(new LongArrayList(new long[] {airRun}), 2, 0, light, exact);
            source.setSingleColumn(new LongArrayList(new long[] {stoneRun}), 3, 0, EDhApiWorldGenerationStep.DOWN_SAMPLED, exact);
            DhRadarTerrainApi.Snapshot radar = DhRadarTerrainApi.decode(source, 512, id -> id == stone);
            require(radar.blocks(63, 320, 0) && radar.blocks(63, 339, 0), "radar uses full opaque spans in exact data");
            require(!radar.blocks(63, 319, 0) && !radar.blocks(63, 340, 0), "radar checks the span's exact boundaries");
            require(radar.blocks(1, 339, 0) && !radar.blocks(1, 338, 0), "visual compression only proves the original top cell");
            require(!radar.blocks(2, 330, 0) && !radar.blocks(3, 330, 0) && !radar.blocks(4, 330, 0),
                    "transparent, downsampled, and missing columns contribute no radar obstruction");
            source.getColumnAtRelPos(63, 0).clear();
            require(radar.blocks(63, 320, 0), "radar snapshot owns its immutable cell data");
        }
        try (FullDataSourceV2 coarse = FullDataSourceV2.createEmpty(DhSectionPos.encode((byte) 7, 0, 0))) {
            require(DhRadarTerrainApi.decode(coarse, 256, id -> true) == null,
                    "radar rejects lower detail data");
        }
        return 6;
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
