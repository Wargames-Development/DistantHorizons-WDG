package com.seibel.distanthorizons.forge17.terrain;

import java.lang.reflect.*;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.util.*;
import net.minecraft.world.*;
import net.minecraft.world.chunk.IChunkProvider;

/** Native ray tracing is the oracle, including oblique rays which miss small boxes. */
public final class RayBlockShapeChecks {
    private static sun.misc.Unsafe allocator;
    private static int checks;
    public static int run() {
        try {
            Field field=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");field.setAccessible(true);allocator=(sun.misc.Unsafe)field.get(null);
            RayBlockShapes shapes=new RayBlockShapes();
            BoxBlock block=(BoxBlock)allocator.allocateInstance(BoxBlock.class);block.setBlockBounds(0,0,0,1,1,1);
            for(int meta=0;meta<16;meta++) compare(shapes,block,meta);
            Block neighbour=(Block)allocator.allocateInstance(NeighbourBlock.class);
            require(shapes.kind(neighbour,0)==-1,"absent neighbour remains unknown");
            Block tile=(Block)allocator.allocateInstance(TileBlock.class);
            require(shapes.kind(tile,0)==-1,"absent tile remains unknown");
            Block missing=(Block)allocator.allocateInstance(MissingOptionalBlock.class);
            require(shapes.kind(missing,0)==-1,"optional class linkage failure stays unknown");
            Block custom=(Block)allocator.allocateInstance(CustomRayBlock.class);
            require(shapes.kind(custom,0)==-1,"custom ray override needs an explicit shape contract");
            MultiBoxBlock exported=(MultiBoxBlock)allocator.allocateInstance(MultiBoxBlock.class);
            compare(shapes,exported,0);
            Block clear=(Block)allocator.allocateInstance(ClearBlock.class);
            require(shapes.kind(clear,0)==0,"native non-collidable state is clear");
            require(shapes.kind(block,16)==-1,"invalid metadata stays unknown");
            return checks;
        } catch(Exception error) { throw new AssertionError("Native laser selection shape fixture",error); }
    }
    public static void main(String[] args) throws Exception {
        run();RayBlockShapes shapes=new RayBlockShapes();
        for(String name:new String[]{"BlockAzaleaLeaves","BlockMangroveRoots","BlockAmethystCluster"}) {
            Block block=(Block)allocator.allocateInstance(Class.forName("ganymedes01.etfuturum.blocks."+name));
            block.setBlockBounds(0,0,0,1,1,1);
            for(int variant=0;variant<(name.equals("BlockAmethystCluster")?2:1);variant++) {
                if(name.equals("BlockAmethystCluster")) {
                    Field type=block.getClass().getDeclaredField("type");type.setAccessible(true);type.setInt(block,variant);
                }
                // Separate instances/pools for immutable definition variants.
                shapes=new RayBlockShapes();
                for(int meta=0;meta<16;meta++) compare(shapes,block,meta);
            }
        }
        Class<?> entries=Class.forName("ganymedes01.etfuturum.ModernMapParityBlocks");
        Class<?> model=Class.forName(entries.getName()+"$ParityModelBlock");
        Field definition=model.getDeclaredField("entry");definition.setAccessible(true);
        int supported=0,unknown=0;
        Field style=entries.getDeclaredField("style");style.setAccessible(true);
        for(Object entry:entries.getEnumConstants()) {
            String name=((Enum<?>)entry).name(),family=String.valueOf(style.get(entry));
            // These definitions instantiate a different native class in createBlock().
            if(family.equals("STAIRS") || family.equals("SLAB") || name.equals("POWDER_SNOW")
                || name.endsWith("COPPER_CHEST") || name.equals("PALE_OAK_DOOR")
                || name.equals("PALE_OAK_TRAPDOOR") || name.equals("PALE_OAK_PRESSURE_PLATE")) continue;
            Block block=(Block)allocator.allocateInstance(model);definition.set(block,entry);block.setBlockBounds(0,0,0,1,1,1);
            shapes=new RayBlockShapes();
            for(int meta=0;meta<16;meta++) {
                int kind=shapes.kind(block,meta);
                if(kind<0) { if(meta==0)System.out.println("UNKNOWN metadata-only EFR shape: "+entry);unknown++;continue; }
                compare(shapes,block,meta);supported++;
            }
        }
        require(supported>100,"actual modern EFR metadata states were compared");
        System.out.println("PASS "+checks+" native/EFR laser checks; modern supported states="+supported+" unknown states="+unknown);
    }

    private static void compare(RayBlockShapes shapes,Block block,int meta) throws Exception {
        int type=shapes.kind(block,meta);require(type>=0,"metadata shape is supported");
        TestWorld world=(TestWorld)allocator.allocateInstance(TestWorld.class);world.block=block;world.meta=meta;
        double[][] rays={{-1,.5,.5,2,.5,.5},{.5,-1,.5,.5,2,.5},{.5,.5,-1,.5,.5,2},{-1,.1,.1,2,.1,.1},
            {-1,.9,.9,2,.9,.9},{2,.25,-1,-1,.75,2},{.1,2,.9,.9,-1,.1},{-1,-1,-1,2,2,2}};
        double[] old={block.getBlockBoundsMinX(),block.getBlockBoundsMinY(),block.getBlockBoundsMinZ(),block.getBlockBoundsMaxX(),block.getBlockBoundsMaxY(),block.getBlockBoundsMaxZ()};
        for(double[] ray:rays) {
            Vec3 start=Vec3.createVectorHelper(ray[0],ray[1],ray[2]),end=Vec3.createVectorHelper(ray[3],ray[4],ray[5]);
            double enter=TerrainRayWalk.box(ray[0],ray[1],ray[2],ray[3]-ray[0],ray[4]-ray[1],ray[5]-ray[2],0,0,0,1,1,1,0,1);
            double exit=1-TerrainRayWalk.box(ray[3],ray[4],ray[5],ray[0]-ray[3],ray[1]-ray[4],ray[2]-ray[5],0,0,0,1,1,1,0,1);
            double contact=Double.isFinite(enter)?shapes.surface(type,0,0,0,ray[0],ray[1],ray[2],ray[3]-ray[0],ray[4]-ray[1],ray[5]-ray[2],enter,exit):Double.NaN;
            MovingObjectPosition nativeHit=block.collisionRayTrace(world,0,0,0,start,end);
            require(Double.isFinite(contact)==(nativeHit!=null),"stored shape hit/miss matches native ray");
            if(nativeHit!=null) {
                Vec3 actual=start.addVector((ray[3]-ray[0])*contact,(ray[4]-ray[1])*contact,(ray[5]-ray[2])*contact);
                require(actual.squareDistanceTo(nativeHit.hitVec)<1e-12,"stored first surface matches native ray: "+block.getClass().getName()+" meta="+meta+" ray="+java.util.Arrays.toString(ray)+" actual="+actual+" native="+nativeHit.hitVec);
            }
        }
        // Restore the shape before a later state is classified (native oracle mutates Block bounds).
        block.setBlockBounds((float)old[0],(float)old[1],(float)old[2],(float)old[3],(float)old[4],(float)old[5]);
    }
    private static void require(boolean value,String message) { checks++;if(!value)throw new AssertionError(message); }
    public static class BoxBlock extends Block {
        BoxBlock() { super(Material.rock); }
        @Override public void setBlockBoundsBasedOnState(IBlockAccess state,int x,int y,int z) {
            int meta=state.getBlockMetadata(x,y,z);float a=(meta&3)/16F;
            setBlockBounds(a,.125F,.25F,.5F+a,(meta&4)==0?.5F:.875F,.75F);
        }
    }
    public static final class NeighbourBlock extends BoxBlock {
        @Override public void setBlockBoundsBasedOnState(IBlockAccess state,int x,int y,int z) { state.getBlock(x+1,y,z); }
    }
    public static final class TileBlock extends BoxBlock {
        @Override public void setBlockBoundsBasedOnState(IBlockAccess state,int x,int y,int z) { state.getTileEntity(x,y,z); }
    }
    public static final class MissingOptionalBlock extends BoxBlock {
        @Override public void setBlockBoundsBasedOnState(IBlockAccess state,int x,int y,int z) { throw new NoClassDefFoundError("optional fixture"); }
    }
    public static class CustomRayBlock extends BoxBlock {
        @Override public MovingObjectPosition collisionRayTrace(World world,int x,int y,int z,Vec3 start,Vec3 end) { return null; }
    }
    public static final class MultiBoxBlock extends CustomRayBlock {
        public double[] getDistantLaserRayBoxesV1(IBlockAccess state,int x,int y,int z) { return new double[]{0,0,0,.25,1,1,.75,0,0,1,1,1}; }
        @Override public MovingObjectPosition collisionRayTrace(World world,int x,int y,int z,Vec3 start,Vec3 end) {
            MovingObjectPosition a=AxisAlignedBB.getBoundingBox(0,0,0,.25,1,1).calculateIntercept(start,end);
            MovingObjectPosition b=AxisAlignedBB.getBoundingBox(.75,0,0,1,1,1).calculateIntercept(start,end);
            return a==null?b:b==null?a:start.squareDistanceTo(a.hitVec)<start.squareDistanceTo(b.hitVec)?a:b;
        }
    }
    public static final class ClearBlock extends BoxBlock { @Override public boolean canStopRayTrace(int meta,boolean liquids) { return false; } }
    public static final class TestWorld extends World {
        Block block;int meta;
        TestWorld() { super(null,"ray_fixture",(WorldProvider)null,(WorldSettings)null,null); }
        @Override public Block getBlock(int x,int y,int z) { return block; }
        @Override public int getBlockMetadata(int x,int y,int z) { return meta; }
        @Override protected IChunkProvider createChunkProvider() { return null; }
        @Override protected int getRenderDistanceChunks() { return 0; }
        @Override public net.minecraft.entity.Entity getEntityByID(int id) { return null; }
    }
}
