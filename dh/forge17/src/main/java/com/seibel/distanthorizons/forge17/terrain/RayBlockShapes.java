package com.seibel.distanthorizons.forge17.terrain;

import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Vec3;
import net.minecraft.world.*;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraftforge.common.util.ForgeDirection;

/** Bounded native selection boxes for metadata-only blocks; never guesses neighbour/tile state. */
final class RayBlockShapes {
    private static final int FIRST=1032,MAX=4094;
    private final Map<Block,int[]> states=new IdentityHashMap<>();
    private final List<double[]> boxes=new ArrayList<>();
    int kind(Block block,int meta) {
        if(meta<0 || meta>15) return -1;
        int[] known=states.get(block);
        if(known==null) {
            if(states.size()>=4096) return -1;
            known=new int[16];Arrays.fill(known,Integer.MIN_VALUE);states.put(block,known);
        }
        if(known[meta]!=Integer.MIN_VALUE) return known[meta];
        int result=-1;
        try {
            if(!block.canStopRayTrace(meta,false)) result=0;
            else {
                java.lang.reflect.Method exported=null;
                if(!baseRay(block)) {
                    // EFR owns custom multi-box selection semantics. Do not infer them
                    // from rendering or entity collision boxes.
                    try { exported=block.getClass().getMethod("getDistantLaserRayBoxesV1",IBlockAccess.class,int.class,int.class,int.class);exported.setAccessible(true); }
                    catch(NoSuchMethodException absent) { known[meta]=-1;return -1; }
                }
                double[] old=bounds(block),shape;
                try {
                    State state=new State(block,meta);
                    if(exported==null) { block.setBlockBoundsBasedOnState(state,0,0,0);shape=bounds(block); }
                    else shape=(double[])exported.invoke(block,state,0,0,0);
                }
                finally { block.setBlockBounds((float)old[0],(float)old[1],(float)old[2],(float)old[3],(float)old[4],(float)old[5]); }
                boolean valid=shape!=null && shape.length>0 && shape.length<=96 && shape.length%6==0;
                if(valid) for(int i=0;i<shape.length;i+=6) for(int axis=0;axis<3;axis++)
                    valid &= Double.isFinite(shape[i+axis]) && Double.isFinite(shape[i+axis+3])
                        && shape[i+axis]>=0 && shape[i+axis+3]<=1 && shape[i+axis]<shape[i+axis+3];
                if(valid) {
                    if(Arrays.equals(shape,new double[]{0,0,0,1,1,1})) result=1;
                    else {
                        int index=-1;for(int i=0;i<boxes.size();i++) if(Arrays.equals(shape,boxes.get(i))) { index=i;break; }
                        if(index<0 && boxes.size()<=MAX-FIRST) { index=boxes.size();boxes.add(shape.clone()); }
                        if(index>=0) result=FIRST+index;
                    }
                }
            }
        } catch(ReflectiveOperationException | RuntimeException | LinkageError unavailable) { result=-1; }
        known[meta]=result;return result;
    }
    private static boolean baseRay(Block block) {
        for(String name:new String[]{"collisionRayTrace","func_149731_a"}) try {
            return block.getClass().getMethod(name,World.class,int.class,int.class,int.class,Vec3.class,Vec3.class).getDeclaringClass()==Block.class;
        } catch(NoSuchMethodException ignored) { }
        return false;
    }
    double surface(int type,int x,int y,int z,double sx,double sy,double sz,double dx,double dy,double dz,double enter,double exit) {
        if(type<FIRST) return TerrainRayWalk.surface(type,x,y,z,sx,sy,sz,dx,dy,dz,enter,exit);
        double[] box=boxes.get(type-FIRST);
        double first=Double.NaN;
        for(int i=0;i<box.length;i+=6) {
            double contact=TerrainRayWalk.box(sx,sy,sz,dx,dy,dz,x+box[i],y+box[i+1],z+box[i+2],x+box[i+3],y+box[i+4],z+box[i+5],enter,exit);
            if(Double.isFinite(contact) && (!Double.isFinite(first) || contact<first)) first=contact;
        }
        return first;
    }
    private static double[] bounds(Block block) {
        return new double[]{block.getBlockBoundsMinX(),block.getBlockBoundsMinY(),block.getBlockBoundsMinZ(),block.getBlockBoundsMaxX(),block.getBlockBoundsMaxY(),block.getBlockBoundsMaxZ()};
    }
    void clear() { states.clear();boxes.clear(); }
    /** Only the queried state exists here. Any other input makes classification unknown. */
    private static final class State implements IBlockAccess {
        final Block block;final int meta;
        State(Block block,int meta) { this.block=block;this.meta=meta; }
        private void local(int x,int y,int z) { if(x!=0 || y!=0 || z!=0) throw new IllegalArgumentException("Neighbour-dependent ray shape"); }
        public Block getBlock(int x,int y,int z) { local(x,y,z);return block; }
        public int getBlockMetadata(int x,int y,int z) { local(x,y,z);return meta; }
        public boolean isAirBlock(int x,int y,int z) { return getBlock(x,y,z)==Blocks.air; }
        public TileEntity getTileEntity(int x,int y,int z) { throw new IllegalArgumentException("Tile-dependent ray shape"); }
        public int getLightBrightnessForSkyBlocks(int x,int y,int z,int light) { throw new IllegalArgumentException("Light-dependent ray shape"); }
        public int isBlockProvidingPowerTo(int x,int y,int z,int side) { throw new IllegalArgumentException("Power-dependent ray shape"); }
        public BiomeGenBase getBiomeGenForCoords(int x,int z) { throw new IllegalArgumentException("Biome-dependent ray shape"); }
        public int getHeight() { throw new IllegalArgumentException("World-dependent ray shape"); }
        public boolean extendedLevelsInChunkCache() { throw new IllegalArgumentException("World-dependent ray shape"); }
        public boolean isSideSolid(int x,int y,int z,ForgeDirection side,boolean fallback) { throw new IllegalArgumentException("Neighbour-dependent ray shape"); }
    }
}
