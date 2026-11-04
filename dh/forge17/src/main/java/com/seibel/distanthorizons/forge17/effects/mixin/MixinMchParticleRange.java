package com.seibel.distanthorizons.forge17.effects.mixin;

import com.seibel.distanthorizons.forge17.effects.EffectSources;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;

@Pseudo
@Mixin(targets="mcheli.particles.MCH_ParticlesUtil",remap=false)
public class MixinMchParticleRange
{
    @ModifyConstant(method="doSpawnParticle",constant=@Constant(doubleValue=300D),remap=false,require=0)
    private static double dh$range(double original) { return EffectSources.extendedRange(original); }
}
