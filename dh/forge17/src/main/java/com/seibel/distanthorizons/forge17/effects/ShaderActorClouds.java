package com.seibel.distanthorizons.forge17.effects;

import java.lang.ref.WeakReference;
import java.lang.reflect.*;
import org.lwjgl.opengl.GL20;

/** Read-only compiled particle variant lookup, including frames without native particles. */
final class ShaderActorClouds {
    private WeakReference<Object> pipeline=new WeakReference<>(null);
    private boolean known,enabled,failed;
    private Method renderDistance;
    private static Field table;
    private static Method match,getProgram,getProgramId;
    private static Object condition,inputs;
    float distance;
    int texture;

    boolean refresh(ShaderEffectsTarget target) {
        texture=0;distance=0;
        if(target==null || !target.complementaryClouds) return true;
        try {
            if(pipeline.get()!=target.pipelineIdentity) {
                pipeline=new WeakReference<>(target.pipelineIdentity);known=enabled=failed=false;
                resolve();
                Object pass=match.invoke(table.get(target.pipelineIdentity),condition,inputs);
                Object program=pass==null?null:getProgram.invoke(pass);
                if(program==null) return false;
                int id=((Number)getProgramId.invoke(program)).intValue();
                if(id<=0) return false;
                int sampler=GL20.glGetUniformLocation(id,"gaux2");
                int range=GL20.glGetUniformLocation(id,"dhRenderDistance");
                // Disabled variants optimise away the cloud sampler. Never use stale colortex5 alpha.
                known=sampler<0 || range>=0;enabled=sampler>=0 && range>=0;
                renderDistance=Class.forName("net.coderbot.iris.compat.dh.DHCompat").getMethod("getRenderDistance");
            }
            if(!known || failed) return false;
            if(!enabled) return true;
            int blocks=((Number)renderDistance.invoke(null)).intValue();
            if(blocks<=0 || target.cloudDepth<=0) return false;
            texture=target.cloudDepth;distance=blocks;return true;
        } catch(ReflectiveOperationException | RuntimeException | LinkageError unsupported) {
            if(!failed) org.apache.logging.log4j.LogManager.getLogger("DistantHorizons").warn(
                "Actor cloud contract unavailable; shader actors withheld",unsupported);
            failed=true;return false;
        }
    }
    @SuppressWarnings({"unchecked","rawtypes"})
    private static void resolve() throws ReflectiveOperationException {
        if(table!=null) return;
        Class<?> deferred=Class.forName("net.coderbot.iris.pipeline.DeferredWorldRenderingPipeline");
        Field candidate=deferred.getDeclaredField("table");candidate.setAccessible(true);
        Class<?> renderCondition=Class.forName("net.coderbot.iris.gbuffer_overrides.matching.RenderCondition");
        Class<?> availability=Class.forName("net.coderbot.iris.gbuffer_overrides.matching.InputAvailability");
        match=candidate.getType().getMethod("match",renderCondition,availability);
        condition=Enum.valueOf((Class)renderCondition,"PARTICLES_TRANSLUCENT");
        inputs=availability.getConstructor(boolean.class,boolean.class).newInstance(true,true);
        getProgram=Class.forName(deferred.getName()+"$Pass").getMethod("getProgram");
        getProgramId=getProgram.getReturnType().getMethod("getProgramId");
        table=candidate;
    }
}
