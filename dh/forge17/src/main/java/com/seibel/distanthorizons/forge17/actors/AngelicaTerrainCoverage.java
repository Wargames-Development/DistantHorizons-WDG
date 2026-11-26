package com.seibel.distanthorizons.forge17.actors;

import com.gtnewhorizons.angelica.rendering.celeritas.CeleritasWorldRenderer;
import com.seibel.distanthorizons.api.objects.math.DhApiMat4f;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ReferenceMap;
import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.world.World;
import org.embeddedt.embeddium.impl.render.chunk.RenderSection;
import org.embeddedt.embeddium.impl.render.chunk.RenderSectionManager;
import org.embeddedt.embeddium.impl.render.terrain.SimpleWorldRenderer;
import org.embeddedt.embeddium.impl.util.PositionUtil;

/** Optional Celeritas adapter. Instantiate only after checking Angelica is present.
 * All methods belong to the render thread. The primary view can reuse section
 * evidence until the next frame or until Angelica publishes a new render list.
 */
@SideOnly(Side.CLIENT)
public final class AngelicaTerrainCoverage {
    private static final int MAX_SECTIONS=131072;
    private static final byte KNOWN=1,UNKNOWN=2;
    private final Long2ByteOpenHashMap checkedSections=new Long2ByteOpenHashMap();
    private Field worldField,sectionsField;
    private MethodHandle sectionLookup;
    private boolean failed,sectionLookupChecked;
    private World world;
    private RenderSectionManager manager;
    private Object renderLists;
    private Object nativeSections;
    private Long2ReferenceMap<RenderSection> fallbackSections;
    private int minSection,maxSection;

    public boolean begin(World expectedWorld) {
        if(failed || expectedWorld==null) return unavailable();
        try {
            CeleritasWorldRenderer renderer=CeleritasWorldRenderer.getInstanceOrNull();
            if(renderer==null || !renderer.isActive()) return unavailable();
            // SimpleWorldRenderer exposes no public world getter. This single,
            // checked identity read prevents old-world meshes authorising a view.
            if(worldField==null) { worldField=SimpleWorldRenderer.class.getDeclaredField("world");worldField.setAccessible(true); }
            if(worldField.get(renderer)!=expectedWorld) return unavailable();
            RenderSectionManager nextManager=renderer.getRenderSectionManager();
            if(nextManager==null || nextManager.isInShadowPass()) return unavailable();
            Object nextLists=nextManager.getRenderLists();
            if(expectedWorld==world && nextManager==manager && nextLists!=null && nextLists==renderLists) return true;
            clear();
            minSection=renderer.getMinimumBuildHeight()>>4;maxSection=(renderer.getMaximumBuildHeight()-1)>>4;
            if(maxSection<minSection || maxSection-minSection>=64 || nextManager.getTotalSections()>MAX_SECTIONS) return false;
            manager=nextManager;renderLists=nextLists;
            resolveSectionLookup();
            if(sectionLookup!=null) nativeSections=sectionsField.get(manager);
            if(nativeSections==null) rebuildFallbackIndex();
            world=expectedWorld;return true;
        } catch(ReflectiveOperationException | RuntimeException | LinkageError error) {
            failed=true;clear();
            org.apache.logging.log4j.LogManager.getLogger("DistantHorizons").warn("Native actor terrain coverage unavailable; unknown terrain remains hidden",error);
            return false;
        }
    }
    private void resolveSectionLookup() {
        if(sectionLookupChecked) return;
        sectionLookupChecked=true;
        try {
            // The packaged DH and Angelica jars use different FastUtil types.
            // Resolve the primitive lookup against the actual field's interface.
            Field field=RenderSectionManager.class.getDeclaredField("sectionByPosition");
            field.setAccessible(true);
            sectionLookup=primitiveLookup(field.getType());
            sectionsField=field;
        } catch(ReflectiveOperationException | RuntimeException unavailable) {
            // Alternate builds can still expose the public section collection.
        }
    }

    private static MethodHandle primitiveLookup(Class<?> indexType) throws ReflectiveOperationException {
        Method method=indexType.getMethod("get",long.class);
        return MethodHandles.publicLookup().unreflect(method).asType(
            MethodType.methodType(RenderSection.class,Object.class,long.class));
    }

    private void rebuildFallbackIndex() {
        fallbackSections=new Long2ReferenceOpenHashMap<>();
        for(RenderSection section:manager.getAllRenderSections())
            fallbackSections.put(PositionUtil.packSection(section.getChunkX(),section.getChunkY(),section.getChunkZ()),section);
    }

    private RenderSection findSection(long key) {
        if(nativeSections==null) return fallbackSections.get(key);
        try {
            return (RenderSection)sectionLookup.invokeExact(nativeSections,key);
        } catch(RuntimeException | Error failure) {
            throw failure;
        } catch(Throwable failure) {
            throw new IllegalStateException("Angelica section lookup failed",failure);
        }
    }

    private boolean sectionKnown(int x,int y,int z) {
        long key=PositionUtil.packSection(x,y,z);
        byte status=checkedSections.get(key);
        if(status==0) {
            RenderSection section=findSection(key);
            // A pending rebuild retains its current mesh. Built empty sections
            // need no draw; occupied sections must be in this view's render list.
            status=section!=null && !section.isDisposed() && section.isBuilt()
                && (!section.hasAnythingToRender() || manager.isSectionVisible(x,y,z))?KNOWN:UNKNOWN;
            checkedSections.put(key,status);
        }
        return status==KNOWN;
    }

    /** Inclusive vertical interval. Outside build height is known air. */
    public boolean isKnown(World expectedWorld,int blockX,int minY,int maxY,int blockZ,
                           DhApiMat4f nativeMvp,double originX,double originY,double originZ) {
        if(expectedWorld!=world || world==null || minY>maxY) return false;
        int lo=Math.max(minSection,minY>>4),hi=Math.min(maxSection,maxY>>4);
        if(lo>hi) return true;
        for(int y=lo;y<=hi;y++) {
            if(sectionKnown(blockX>>4,y,blockZ>>4)) continue;
            // Celeritas correctly omits out-of-view occupied sections. Only the
            // queried part of a section that can affect this view requires depth.
            if(ModelBounds.intersectsView(nativeMvp,blockX-originX,Math.max(minY,y<<4)-originY,blockZ-originZ,
                blockX+16-originX,Math.min(maxY+1,(y+1)<<4)-originY,blockZ+16-originZ)) return false;
        }
        return true;
    }
    private boolean unavailable() { clear();return false; }
    public void clear() { world=null;manager=null;renderLists=null;nativeSections=null;fallbackSections=null;checkedSections.clear(); }
}
