package com.seibel.distanthorizons.forge17.terrain;

import com.seibel.distanthorizons.common.wrappers.world.ServerLevelWrapper;
import com.seibel.distanthorizons.common.wrappers.block.BlockStateWrapper;
import com.seibel.distanthorizons.core.api.internal.SharedApi;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.level.IDhLevel;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.util.FullDataPointUtil;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.ILevelWrapper;
import com.seibel.distanthorizons.core.world.IDhWorld;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

/** Finest-data, nonblocking laser terrain query. No chunk loads, generation, packets or client claims. */
public final class DhTerrainRayApi {
    public static final int API_VERSION=1;
    public enum Result { CLEAR, BLOCKED, UNKNOWN }
    private static final Map<World,Cache> caches=new WeakHashMap<>();
    private static volatile Thread serverThread;
    public static void serverTick() { serverThread=Thread.currentThread(); }
    private DhTerrainRayApi() { }
    public static Result trace(World world,double x0,double y0,double z0,double x1,double y1,double z1,double[] hit) {
        if(hit==null || hit.length<3) return Result.UNKNOWN;
        Arrays.fill(hit,0,3,Double.NaN);
        if(world==null || !Double.isFinite(x0+y0+z0+x1+y1+z1) || Math.max(Math.max(Math.abs(x0),Math.abs(z0)),Math.max(Math.abs(x1),Math.abs(z1)))>30_000_000) return Result.UNKNOWN;
        double dx=x1-x0,dy=y1-y0,dz=z1-z0,distance=Math.sqrt(dx*dx+dy*dy+dz*dz);
        if(distance<=0 || distance>1536.01 || Math.abs(y0)>8192 || Math.abs(y1)>8192) return Result.UNKNOWN;
        try {
            ILevelWrapper wrapper;IDhWorld dhWorld;
            if(world.isRemote) {
                wrapper=(ILevelWrapper)Class.forName("com.seibel.distanthorizons.forge17.terrain.client.ClientRayLevel").getMethod("wrapper",World.class).invoke(null,world);
                dhWorld=SharedApi.tryGetDhClientWorld();
            } else {
                if(Thread.currentThread()!=serverThread || !(world instanceof WorldServer)) return Result.UNKNOWN;
                wrapper=ServerLevelWrapper.getWrapper((WorldServer)world);dhWorld=SharedApi.tryGetDhServerWorld();
            }
            if(wrapper==null || dhWorld==null) return Result.UNKNOWN;
            IDhLevel level=dhWorld.getLevel(wrapper);if(level==null) return Result.UNKNOWN;
            Cache cache;
            synchronized(caches) {
                cache=caches.get(world);
                if(cache!=null && cache.level.get()!=level) { cache.clear();cache=null; }
                if(cache==null) { cache=new Cache();cache.level=new java.lang.ref.WeakReference<>(level);caches.put(world,cache); }
            }
            final Cache selected=cache;final ILevelWrapper bounds=wrapper;
            Result result=TerrainRayWalk.trace(x0,y0,z0,x1,y1,z1,(x,y,z)->selected.cell(level,bounds,x,y,z),hit);
            selected.report(result,world.isRemote);return result;
        } catch(ReflectiveOperationException | RuntimeException | LinkageError unavailable) { return Result.UNKNOWN; }
    }
    public static void unload(World world) {
        Cache cache;synchronized(caches) { cache=caches.remove(world); }
        if(cache!=null) cache.clear();
    }
    private static int kind(Block block,int meta,String serial) {
        if(block==Blocks.air || block==Blocks.water || block==Blocks.flowing_water || block==Blocks.lava || block==Blocks.flowing_lava || block==Blocks.tallgrass || block==Blocks.deadbush || block==Blocks.yellow_flower
            || block==Blocks.red_flower || block==Blocks.double_plant || block==Blocks.sapling || block==Blocks.brown_mushroom
            || block==Blocks.red_mushroom || block==Blocks.wheat || block==Blocks.carrots || block==Blocks.potatoes
            || block==Blocks.nether_wart || block==Blocks.reeds || block==Blocks.vine) return 0;
        if(com.seibel.distanthorizons.core.dataObjects.transformers.EfrLodCompat.distinctDirtFaces(serial)) return height(0,15);
        if(block instanceof net.minecraft.block.BlockSlab) return block.isOpaqueCube()?1:((meta&8)==0?height(0,8):height(8,16));
        if(block instanceof net.minecraft.block.BlockStairs) return 1024+(meta&7);
        if(block==Blocks.snow_layer) return height(0,2*((meta&7)+1));
        // Neighbour/tile-dependent shapes need more than a block ID. They remain unknown.
        if(block==Blocks.glass || block==Blocks.stained_glass || block==Blocks.ice || block==Blocks.packed_ice
            || block==Blocks.leaves || block==Blocks.leaves2) return 1;
        return block.isOpaqueCube() && block.renderAsNormalBlock()?1:-1;
    }
    private static int height(int bottom,int top) { return 2+bottom+(top<<5); }
    private static final class Entry {
        long created=System.nanoTime();CompletableFuture<FullDataSourceV2> future;
        long[][] columns;
        Entry(CompletableFuture<FullDataSourceV2> future) { this.future=future; }
        boolean usable(long now) { return columns!=null && now-created<=2_000_000_000L; }
        boolean refreshDue(long now) { return future==null && now-created>=1_000_000_000L; }
        void discard() { if(future!=null) { future.whenComplete((source,error)->{if(source!=null)source.close();});future=null; } }
    }
    private static final class Cache {
        final LinkedHashMap<Long,Entry> entries=new LinkedHashMap<>(32,.75F,true);
        java.lang.ref.WeakReference<IDhLevel> level=new java.lang.ref.WeakReference<>(null);
        long window;int requests,conversions;
        int reason,lastX,lastY,lastZ,reports;long lastReport;
        final java.util.Set<String> unsupported=new java.util.LinkedHashSet<>();
        int unknown(int reason,int x,int y,int z) { this.reason=reason;lastX=x;lastY=y;lastZ=z;return -1; }
        void report(Result result,boolean client) {
            long now=System.nanoTime();if(result!=Result.UNKNOWN || reports>=20 || now-lastReport<1_000_000_000L) return;
            reports++;lastReport=now;
            org.apache.logging.log4j.LogManager.getLogger("DistantHorizons").info("DH laser pending ["+(client?"client":"server")+"]: reason="+reason+" cell="+lastX+","+lastY+","+lastZ+" cachedSections="+entries.size()+" unsupportedShapeCandidates="+unsupported);
        }
        void clear() { for(Entry entry:entries.values()) entry.discard();entries.clear(); }
        int cell(IDhLevel level,ILevelWrapper bounds,int x,int y,int z) {
            if(y>=bounds.getMaxHeight()) return 0;
            if(y<bounds.getMinHeight()) return -1;
            long now=System.nanoTime();
            if(now-window>50_000_000L) { window=now;requests=conversions=0; }
            long pos=DhSectionPos.encode(DhSectionPos.SECTION_BLOCK_DETAIL_LEVEL,x>>6,z>>6);
            Entry entry=entries.get(pos);
            if(entry==null) {
                if(requests++>=4) return unknown(5,x,y,z);
                if(entries.size()>=32) { Iterator<Entry> old=entries.values().iterator();old.next().discard();old.remove(); }
                entry=new Entry(level.getFullDataProvider().getAsync(pos));entries.put(pos,entry);
            }
            if(entry.refreshDue(now) && requests<4) {
                requests++;entry.future=level.getFullDataProvider().getAsync(pos);
            }
            adopt(entry,now);
            // Prefetch while the old snapshot is still within its original two-second validity.
            // Slow/missing refreshes still fail closed when that deadline is reached.
            if(!entry.usable(now)) return unknown(entry.columns==null?1:4,x,y,z);
            if(entry.columns.length==0) return unknown(6,x,y,z);
            long[] column=entry.columns[FullDataSourceV2.relativePosToIndex(x&63,z&63)];
            if(column==null) return unknown(2,x,y,z);
            int relY=y-bounds.getMinHeight();
            int highest=0;
            for(long interval:column) {
                int bottom=(int)(interval>>>32),top=(int)((interval>>>12)&0xfffffL);
                highest=Math.max(highest,top);
                if(relY>=bottom && relY<top) { int type=(int)(interval&0xfff)-1;return type<0?unknown(3,x,y,z):type; }
            }
            return relY>=highest?0:unknown(2,x,y,z);
        }
        void adopt(Entry entry,long now) {
            if(entry.future==null || !entry.future.isDone() || conversions>=1) return;
            conversions++;
            CompletableFuture<FullDataSourceV2> future=entry.future;entry.future=null;
            FullDataSourceV2 source=null;
            try {
                source=future.getNow(null);entry.columns=source==null?null:copy(source);
            } catch(RuntimeException unsupported) { entry.columns=null; }
            finally { if(source!=null) source.close();entry.created=now; }
            if(entry.columns==null) entry.columns=new long[0][];
        }
        long[][] copy(FullDataSourceV2 source) {
            if(source.getDataDetailLevel()!=0) return null;
            long[][] columns=new long[4096][];Map<Integer,Integer> materials=new HashMap<>();int points=0;
            for(int index=0;index<4096;index++) {
                if(source.columnGenerationSteps.getByte(index)<8 || source.columnWorldCompressionMode.getByte(index)<0 || source.columnWorldCompressionMode.getByte(index)>1) continue;
                it.unimi.dsi.fastutil.longs.LongArrayList data=source.dataPoints[index];
                if(data==null || data.isEmpty()) continue;
                if((points+=data.size())>65536) return null;
                long[] result=new long[data.size()];boolean valid=false;
                for(int n=0;n<data.size();n++) {
                    long point=data.getLong(n);if(FullDataPointUtil.getHeight(point)<=0) continue;valid=true;
                    int id=FullDataPointUtil.getId(point),bottom=FullDataPointUtil.getBottomY(point);
                    Integer type=materials.get(id);
                    if(type==null) {
                        com.seibel.distanthorizons.core.wrapperInterfaces.block.IBlockStateWrapper state=source.mapping.getBlockStateWrapper(id);
                        type=state.isAir()?0:state instanceof BlockStateWrapper && ((BlockStateWrapper)state).blockState!=null
                            ?kind(((BlockStateWrapper)state).blockState.getBlock(),((BlockStateWrapper)state).blockState.getMeta(),state.getSerialString()):-1;
                        if(type<0 && unsupported.size()<16) unsupported.add(state.getSerialString());
                        materials.put(id,type);
                    }
                    result[n]=((long)bottom<<32)|((long)(bottom+FullDataPointUtil.getHeight(point))<<12)|(type+1);
                }
                if(valid) columns[index]=result;
            }
            return columns;
        }
    }
}
