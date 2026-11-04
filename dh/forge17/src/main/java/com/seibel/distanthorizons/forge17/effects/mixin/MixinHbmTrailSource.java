package com.seibel.distanthorizons.forge17.effects.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.seibel.distanthorizons.forge17.effects.*;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets="com.hbm.entity.missile.EntityMissileBaseNT",remap=false)
public class MixinHbmTrailSource
{
    @Inject(method={"onUpdate","func_70071_h_"},at=@At("TAIL"),remap=false,require=0)
    private void dh$serverSource(CallbackInfo ci) { TrailSources.hbm((Entity)(Object)this); }

    @WrapOperation(method={"onUpdate","func_70071_h_"},at=@At(value="INVOKE",
        target="Lcom/hbm/entity/missile/EntityMissileBaseNT;spawnContrail()V",remap=false),remap=false,require=0)
    private void dh$nativeSource(@Coerce Object self,Operation<Void> original)
    {
        Entity entity=(Entity)self;
        // Observation only: relay state must never cancel the original emitter.
        EffectOrigin.Source previous=EffectOrigin.enter(entity,TrailSample.HBM,false);
        try { original.call(self); }
        finally { EffectOrigin.restore(previous); }
    }
}
