package com.seibel.distanthorizons.forge17.effects;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

/** BountifulStars/Complementary's native particle cloud-distance contract: gaux2.a^2 * dhRenderDistance. */
final class ShaderCloudOcclusion {
    private final int[] tried=new int[8];private int count;
    private final java.nio.IntBuffer value=BufferUtils.createIntBuffer(1);
    int texture;float distance;
    void clear() { count=0;texture=0;distance=0; }
    void refreshAtDraw(ShaderEffectsTarget target) {
        observe(target);
        // Uniform observation establishes the pack's cloud-enabled variant only.
        // Texture bindings observed before deferred can be stale, or overwritten by
        // another program. Resolve colortex5 from the same parity as scene colour.
        texture=target!=null && distance>0?target.cloudDepth:0;
    }
    void observe(ShaderEffectsTarget target) {
        if(target==null || !target.complementaryClouds || distance>0 || count==tried.length) return;
        int program=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        if(program<=0) return;
        for(int i=0;i<count;i++) if(tried[i]==program) return;
        tried[count++]=program;
        int sampler=GL20.glGetUniformLocation(program,"gaux2");
        int range=GL20.glGetUniformLocation(program,"dhRenderDistance");
        if(sampler<0 || range<0) return; // Cloud-disabled variants optimize these out.
        value.clear();GL20.glGetUniform(program,range,value);int blocks=value.get(0);
        if(blocks>0) distance=blocks;
    }
}
