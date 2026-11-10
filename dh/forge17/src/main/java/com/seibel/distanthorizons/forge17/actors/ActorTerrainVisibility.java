package com.seibel.distanthorizons.forge17.actors;

import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiRenderParam;
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
    public boolean begin(DhApiRenderParam view,World world) {
        clear();
        if(view==null || world==null || !(view.clientLevelWrapper instanceof IClientLevelWrapper)) return false;
        IClientLevelWrapper wrapper=(IClientLevelWrapper)view.clientLevelWrapper;
        if(wrapper.getWrappedMcObject()!=world || wrapper.getDhLevel()==null) return false;
        buffers=wrapper.getDhLevel().getRenderBufferHandler();if(buffers==null) return false;
        this.view=view;this.world=world;corridor.reset(8192);
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
        return world!=null && corridor.known(cx,cy,cz,x,y,z,radius,columns);
    }
    private boolean known(int chunkX,int minY,int maxY,int chunkZ) {
        return (nativeReady && nativeCoverage.isKnown(world,chunkX<<4,minY,maxY,chunkZ<<4))
            || buffers.isTerrainChunkKnown(view,chunkX,chunkZ);
    }
    public void clear() {
        world=null;view=null;buffers=null;nativeReady=false;corridor.reset(0);
        if(nativeCoverage!=null) nativeCoverage.clear();
    }
}
