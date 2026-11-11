package com.seibel.distanthorizons.forge17.actors;

import com.gtnewhorizons.angelica.rendering.celeritas.CeleritasWorldRenderer;
import com.seibel.distanthorizons.api.objects.math.DhApiMat4f;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import java.lang.reflect.Field;
import net.minecraft.world.World;
import org.embeddedt.embeddium.impl.render.chunk.RenderSection;
import org.embeddedt.embeddium.impl.render.chunk.RenderSectionManager;
import org.embeddedt.embeddium.impl.render.terrain.SimpleWorldRenderer;

/** Optional Celeritas adapter. Instantiate only after checking Angelica is present.
 * All methods belong to the render thread, within one synchronous view draw.
 */
@SideOnly(Side.CLIENT)
public final class AngelicaTerrainCoverage {
    private static final int MAX_SECTIONS=131072;
    private final Long2LongOpenHashMap columns=new Long2LongOpenHashMap();
    private Field worldField;
    private boolean failed;
    private World world;
    private CeleritasWorldRenderer renderer;
    private RenderSectionManager manager;
    private int minSection,maxSection;

    public boolean begin(World expectedWorld) {
        world=null;columns.clear();manager=null;renderer=null;
        if(failed || expectedWorld==null) return false;
        try {
            renderer=CeleritasWorldRenderer.getInstanceOrNull();
            if(renderer==null || !renderer.isActive()) return false;
            // SimpleWorldRenderer exposes no public world getter. This single,
            // checked identity read prevents old-world meshes authorising a view.
            if(worldField==null) { worldField=SimpleWorldRenderer.class.getDeclaredField("world");worldField.setAccessible(true); }
            if(worldField.get(renderer)!=expectedWorld) return false;
            manager=renderer.getRenderSectionManager();
            if(manager==null || manager.isInShadowPass()) return false;
            minSection=renderer.getMinimumBuildHeight()>>4;maxSection=(renderer.getMaximumBuildHeight()-1)>>4;
            if(maxSection<minSection || maxSection-minSection>=64) return false;
            java.util.Collection<RenderSection> sections=manager.getAllRenderSections();
            if(sections.size()>MAX_SECTIONS) return false;
            for(RenderSection section:sections) {
                int y=section.getChunkY()-minSection;
                // A queued/in-flight rebuild retains its built mesh until upload.
                // Coverage follows that displayed mesh, not the pending replacement.
                if(y<0 || y>maxSection-minSection || section.isDisposed() || !section.isBuilt()) continue;
                // Known empty sections need no draw; occupied sections must be in
                // this view's native render list to provide matching scene depth.
                if(section.hasAnythingToRender() && !manager.isSectionVisible(section.getChunkX(),section.getChunkY(),section.getChunkZ())) continue;
                long key=key(section.getChunkX(),section.getChunkZ());
                columns.put(key,columns.get(key)|(1L<<y));
            }
            world=expectedWorld;return true;
        } catch(ReflectiveOperationException | RuntimeException | LinkageError error) {
            failed=true;columns.clear();world=null;
            org.apache.logging.log4j.LogManager.getLogger("DistantHorizons").warn("Native actor terrain coverage unavailable; unknown terrain remains hidden",error);
            return false;
        }
    }
    /** Inclusive vertical interval. Outside build height is known air. */
    public boolean isKnown(World expectedWorld,int blockX,int minY,int maxY,int blockZ,
                           DhApiMat4f nativeMvp,double originX,double originY,double originZ) {
        if(expectedWorld!=world || world==null || minY>maxY) return false;
        int lo=Math.max(minSection,minY>>4),hi=Math.min(maxSection,maxY>>4);
        if(lo>hi) return true;
        long known=columns.get(key(blockX>>4,blockZ>>4));
        for(int y=lo;y<=hi;y++) {
            if((known & (1L<<(y-minSection)))!=0) continue;
            // Celeritas correctly omits out-of-view occupied sections. Only the
            // queried part of a section that can affect this view requires depth.
            if(ModelBounds.intersectsView(nativeMvp,blockX-originX,Math.max(minY,y<<4)-originY,blockZ-originZ,
                blockX+16-originX,Math.min(maxY+1,(y+1)<<4)-originY,blockZ+16-originZ)) return false;
        }
        return true;
    }
    public void clear() { world=null;renderer=null;manager=null;columns.clear(); }
    private static long key(int x,int z) { return ((long)x<<32) ^ (z & 0xffffffffL); }
}
