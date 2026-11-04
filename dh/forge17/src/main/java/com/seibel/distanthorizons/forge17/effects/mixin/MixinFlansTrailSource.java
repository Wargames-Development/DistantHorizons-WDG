package com.seibel.distanthorizons.forge17.effects.mixin;

import com.seibel.distanthorizons.forge17.effects.TrailSources;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets="com.flansmod.common.guns.EntityBullet",remap=false)
public class MixinFlansTrailSource
{
    @Inject(method={"onUpdate","func_70071_h_"},at=@At("TAIL"),remap=false,require=0)
    private void dh$source(CallbackInfo ci) { TrailSources.flans((Entity)(Object)this); }
}
