package com.seibel.distanthorizons.forge17.mixin;

import com.seibel.distanthorizons.forge17.effects.EffectSources;
import com.seibel.distanthorizons.forge17.effects.TrailClient;
import com.seibel.distanthorizons.forge17.effects.NtmTrace;
import org.spongepowered.asm.mixin.Shadow;
import java.util.List;
import net.minecraft.client.particle.EffectRenderer;
import net.minecraft.client.particle.EntityFX;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=EffectRenderer.class,priority=500)
public class MixinEffectCapture
{
    @Shadow private List[] fxLayers;
    @Inject(method="updateEffects",at=@At("TAIL"))
    private void dh$live(CallbackInfo ci) { NtmTrace.live(fxLayers); }

    @Inject(method="addEffect",at=@At("HEAD"),cancellable=true)
    private void dh$attribute(EntityFX particle,CallbackInfo ci)
    {
        if(!TrailClient.acceptBirth(particle)) { ci.cancel();return; }
        EffectSources.added(particle);
    }
    @Inject(method="addEffect",at=@At("TAIL"))
    private void dh$age(EntityFX particle,CallbackInfo ci) { NtmTrace.born(particle);TrailClient.ageBirth(particle); }

}
