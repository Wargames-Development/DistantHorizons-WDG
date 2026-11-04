package com.seibel.distanthorizons.forge17.effects.mixin;

import com.seibel.distanthorizons.forge17.effects.EffectSources;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;

@Pseudo
@Mixin(targets="com.flansmod.client.ClientProxy",remap=false)
public class MixinFlansParticleRange
{
    @ModifyConstant(method="doSpawnParticle",constant={@Constant(doubleValue=160D),@Constant(doubleValue=1024D)},remap=false,require=0)
    private static double dh$range(double original) { return EffectSources.extendedRange(original); }
}
