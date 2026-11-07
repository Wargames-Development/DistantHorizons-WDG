package com.seibel.distanthorizons.forge17.effects;

import java.lang.reflect.Method;
import java.nio.FloatBuffer;
import com.seibel.distanthorizons.api.objects.math.DhApiMat4f;
import net.minecraft.client.Minecraft;

/** Optional Angelica DH bridge. Resolve methods once; refresh pipeline/texture data each frame.
 * Depth and parity APIs are public; RenderTargets lookup is a checked private compatibility seam. */
final class ShaderEffectsTarget {
    private static boolean resolved,unsupported;
    private static ShaderEffectsTarget cached;
    static void invalidate() { cached=null; }
    private static Method manager,pipeline,compat,instance,depth,depthCopy,nativeDepth,packName;
    private static Method renderTargets,flipped,getTarget,mainTexture,altTexture;
    private static Method projection,writeProjection;
    private static final FloatBuffer projectionValues=org.lwjgl.BufferUtils.createFloatBuffer(16);
    final int sceneDepth,terrainDepth,nativeSceneDepth,sceneColour,cloudDepth,particleMaterial;
    final DhApiMat4f terrainProjection;
    final boolean colourFlipped,cloudFlipped;
    final boolean complementaryClouds;
    private ShaderEffectsTarget(int depth,int copy,int nativeDepth,String pack,int colour,int cloud,int material,java.util.Set<?> flips,DhApiMat4f terrainProjection) {
        this.terrainProjection=terrainProjection;
        sceneDepth=depth;terrainDepth=copy;nativeSceneDepth=nativeDepth;sceneColour=colour;cloudDepth=cloud;
        colourFlipped=flips.contains(0);cloudFlipped=flips.contains(5);
        String name=pack.toLowerCase(java.util.Locale.ROOT);
        complementaryClouds=name.contains("bountifulstars") || name.contains("complementary");
        particleMaterial=complementaryClouds?material:0;
    }
    private static int texture(Object targets,java.util.Set<?> flips,int index) throws ReflectiveOperationException {
        Object target=getTarget.invoke(targets,index);
        // IrisSamplers.addRenderTargetSamplers: flipped -> alt, otherwise main.
        return target==null?0:((Number)(flips.contains(index)?altTexture:mainTexture).invoke(target)).intValue();
    }
    static ShaderEffectsTarget current() {
        if(!com.seibel.distanthorizons.forge17.EffectCaptureTransformer.shaderFinalizationHookInstalled) return null;
        if(unsupported) return null;
        if(cached!=null) return cached;
        try {
            if(!resolved) {
                Class<?> iris=Class.forName("net.coderbot.iris.Iris");
                manager=iris.getMethod("getPipelineManager");packName=iris.getMethod("getCurrentPackName");
                pipeline=manager.getReturnType().getMethod("getPipelineNullable");
                compat=pipeline.getReturnType().getMethod("getDHCompat");
                instance=compat.getReturnType().getMethod("getInstance");
                // This is the supplier registered for the pack's dhProjection.
                // Core DH caps its own near plane at 7.5, whereas the pack's
                // terrain shader uses Angelica's uncapped projection. They must
                // never be mixed in a depth comparison or gl_FragDepth write.
                projection=compat.getReturnType().getMethod("getProjection");
                writeProjection=projection.getReturnType().getMethod("get",FloatBuffer.class);
                depth=instance.getReturnType().getMethod("getStoredDepthTex");
                depthCopy=instance.getReturnType().getMethod("getDepthTexNoTranslucent");
                nativeDepth=Class.forName("net.coderbot.iris.rendertarget.IRenderTargetExt").getMethod("iris$getDepthTextureId");
                // The public parity getter already resolves this frame's ping-pong state.
                // RenderTargets access is private in both supported versions; fail closed
                // if this optional implementation detail changes.
                Class<?> deferred=Class.forName("net.coderbot.iris.pipeline.DeferredWorldRenderingPipeline");
                renderTargets=deferred.getDeclaredMethod("getRenderTargets");renderTargets.setAccessible(true);
                flipped=deferred.getMethod("getFlippedAfterTranslucent");
                getTarget=renderTargets.getReturnType().getMethod("get",int.class);
                mainTexture=getTarget.getReturnType().getMethod("getMainTexture");
                altTexture=getTarget.getReturnType().getMethod("getAltTexture");
                resolved=true;
            }
            Object m=manager.invoke(null);if(m==null) return null;
            Object p=pipeline.invoke(m);if(p==null) return null;
            Object c=compat.invoke(p);if(c==null) return null;
            Object dh=instance.invoke(c);if(dh==null) return null;
            int d=((Number)depth.invoke(dh)).intValue(),copy=((Number)depthCopy.invoke(dh)).intValue();
            int n=((Number)nativeDepth.invoke(Minecraft.getMinecraft().getFramebuffer())).intValue();
            java.util.Set<?> flips=(java.util.Set<?>)flipped.invoke(p);
            Object targets=renderTargets.invoke(p);
            int colour=texture(targets,flips,0),cloud=texture(targets,flips,5),material=texture(targets,flips,6);
            projectionValues.clear();writeProjection.invoke(projection.invoke(null),projectionValues);
            DhApiMat4f terrainProjection=readColumnMajorProjection(projectionValues);
            cached=d>0 && copy>0 && n>0 && colour>0 && d!=copy && d!=n
                ?new ShaderEffectsTarget(d,copy,n,String.valueOf(packName.invoke(null)),colour,cloud,material,flips,terrainProjection):null;
            return cached;
        } catch(ReflectiveOperationException | RuntimeException | LinkageError error) {
            unsupported=true;
            org.apache.logging.log4j.LogManager.getLogger("DistantHorizons").warn("Distant effects cannot access this Angelica DH pipeline; native rendering retained",error);
            return null;
        }
    }
    static DhApiMat4f readColumnMajorProjection(FloatBuffer values) {
        float[] rows=new float[16];
        for(int row=0;row<4;row++) for(int column=0;column<4;column++) {
            float value=values.get(column*4+row);
            if(!Float.isFinite(value)) throw new IllegalArgumentException("Nonfinite Angelica DH projection");
            rows[row*4+column]=value;
        }
        return new DhApiMat4f(rows);
    }
}
