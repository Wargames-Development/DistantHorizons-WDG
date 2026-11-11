package com.seibel.distanthorizons.forge17.actors;

import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiRenderParam;
import com.seibel.distanthorizons.api.objects.math.DhApiMat4f;
import com.seibel.distanthorizons.core.render.RenderBufferHandler;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IClientLevelWrapper;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.world.World;

/** Combines current-view native coverage with the matching uploaded DH coverage. */
@SideOnly(Side.CLIENT)
public final class ActorTerrainVisibility {
    private final TerrainCorridor corridor=new TerrainCorridor();
    private final TerrainCorridor.Columns columns=this::known;
    private AngelicaTerrainCoverage nativeCoverage;
    private boolean nativeFailed,nativeReady;
    private RenderBufferHandler buffers;
    private DhApiRenderParam view;
    private World world;
    private long generation;
    private final DhApiMat4f nativeMvp=new DhApiMat4f();
    private double originX,originY,originZ;
    private String missing="none";
    public String missing() { return missing; }
    public boolean begin(DhApiRenderParam view,World world,long generation,
                         DhApiMat4f nativeMvp,double originX,double originY,double originZ) {
        clear();
        if(view==null || world==null || !(view.clientLevelWrapper instanceof IClientLevelWrapper)) return false;
        IClientLevelWrapper wrapper=(IClientLevelWrapper)view.clientLevelWrapper;
        if(wrapper.getWrappedMcObject()!=world || wrapper.getDhLevel()==null) return false;
        buffers=wrapper.getDhLevel().getRenderBufferHandler();if(buffers==null) return false;
        if(generation==0 || generation!=buffers.getCoverageGeneration()) return false;
        this.view=view;this.world=world;this.generation=generation;corridor.reset(8192);
        this.nativeMvp.set(nativeMvp);this.originX=originX;this.originY=originY;this.originZ=originZ;
        if(!nativeFailed && Loader.isModLoaded("angelica")) try {
            if(nativeCoverage==null) nativeCoverage=new AngelicaTerrainCoverage();
            nativeReady=nativeCoverage.begin(world);
        } catch(RuntimeException | LinkageError unsupported) {
            nativeFailed=true;
            org.apache.logging.log4j.LogManager.getLogger("DistantHorizons").warn("Native actor coverage adapter unavailable; DH coverage remains required",unsupported);
        }
        return true;
    }
    public boolean isKnown(double cx,double cy,double cz,double x,double y,double z,double radius) {
        missing="budget or invalid corridor";
        return world!=null && buffers.getCoverageGeneration()==generation && corridor.known(cx,cy,cz,x,y,z,radius,columns);
    }
    private boolean known(int chunkX,int minY,int maxY,int chunkZ) {
        // The conservative model corridor also contains off-screen/behind-camera
        // terrain. Its absence from this view cannot leave a hole in an actor pixel.
        if(!intersectsView(chunkX<<4,minY,maxY,chunkZ<<4)) return true;
        boolean known=(nativeReady && nativeCoverage.isKnown(world,chunkX<<4,minY,maxY,chunkZ<<4,nativeMvp,originX,originY,originZ))
            || buffers.isTerrainChunkKnown(view,generation,chunkX,chunkZ);
        if(!known) missing="chunk="+chunkX+","+chunkZ+" y="+minY+".."+maxY;
        return known;
    }
    private boolean intersectsView(int x,int minY,int maxY,int z) {
        return ModelBounds.intersectsView(nativeMvp,x-originX,minY-originY,z-originZ,x+16-originX,maxY+1-originY,z+16-originZ);
    }
    public void clear() {
        world=null;view=null;buffers=null;nativeReady=false;corridor.reset(0);
        if(nativeCoverage!=null) nativeCoverage.clear();
    }
}
