package com.seibel.distanthorizons.forge17.effects.mixin;

import com.seibel.distanthorizons.forge17.effects.EffectSources;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;

@Pseudo
@Mixin(targets="com.hbm.main.ClientProxy",remap=false)
public class MixinHbmParticleRange
{
    @ModifyConstant(method="effectNT",constant=@Constant(doubleValue=350D),remap=false,require=0)
    private double dh$range(double original) { return EffectSources.extendedRange(original); }
}
