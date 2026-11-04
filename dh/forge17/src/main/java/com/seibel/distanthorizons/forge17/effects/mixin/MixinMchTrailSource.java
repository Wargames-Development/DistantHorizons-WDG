package com.seibel.distanthorizons.forge17.effects.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.seibel.distanthorizons.forge17.effects.*;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

@Pseudo
@Mixin(targets="mcheli.weapon.MCH_EntityBaseBullet",remap=false)
public class MixinMchTrailSource
{
    @WrapMethod(method="spawnExplosionParticle",remap=false,require=0)
    private void dh$source(String name,int count,float size,Operation<Void> original)
    {
        Entity entity=(Entity)(Object)this;
        if(!entity.worldObj.isRemote)
        {
            original.call(name,count,size);
            TrailSources.mch(entity,name,count,size);
            return;
        }
        EffectOrigin.Source previous=EffectOrigin.enter(entity,TrailSample.MCH,false);
        try { original.call(name,count,size); }
        finally { EffectOrigin.restore(previous); }
    }
}
