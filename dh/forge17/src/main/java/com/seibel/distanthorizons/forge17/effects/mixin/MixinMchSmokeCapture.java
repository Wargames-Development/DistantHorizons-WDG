package com.seibel.distanthorizons.forge17.effects.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.seibel.distanthorizons.forge17.effects.DistantSmokeRenderer;
import com.seibel.distanthorizons.forge17.mixin.MixinTessellatorAccess;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.client.renderer.Tessellator;
import org.spongepowered.asm.mixin.*;

@Pseudo
@Mixin(targets="mcheli.particles.MCH_EntityParticleSmoke",remap=false)
public class MixinMchSmokeCapture
{
    @WrapMethod(method={"renderParticle","func_70539_a"},remap=false,require=0)
    private void dh$capture(Tessellator tess,float partial,float a,float b,float c,float d,float e,Operation<Void> original)
    {
        boolean capture=DistantSmokeRenderer.begin((EntityFX)(Object)this);
        int start=capture?((MixinTessellatorAccess)tess).dh$getRawBufferIndex():0;
        try { original.call(tess,partial,a,b,c,d,e); }
        finally { if(capture) DistantSmokeRenderer.finish(tess,start); }
    }
}
