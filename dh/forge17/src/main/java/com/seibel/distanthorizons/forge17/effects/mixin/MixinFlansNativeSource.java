package com.seibel.distanthorizons.forge17.effects.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.seibel.distanthorizons.forge17.effects.*;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.*;

@Pseudo
@Mixin(targets="com.flansmod.common.guns.EntityBullet",remap=false)
public class MixinFlansNativeSource
{
    @WrapMethod(method="spawnParticles",remap=false,require=0)
    private void dh$source(Operation<Void> original)
    {
        EffectOrigin.Source previous=EffectOrigin.enter((Entity)(Object)this,TrailSample.FLANS,false);
        try { original.call(); }
        finally { EffectOrigin.restore(previous); }
    }
}
