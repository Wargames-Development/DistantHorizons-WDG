package com.seibel.distanthorizons.forge17.effects.mixin;
import com.seibel.distanthorizons.forge17.effects.*;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Pseudo
@Mixin(targets="com.hbm.particle.helper.ExplosionCreator",remap=false)
public class MixinHbmExplosionSource {
    @Inject(method="composeEffect",at=@At("RETURN"),remap=false,require=1)
    private static void dh$explosion(World world,double x,double y,double z,int clouds,float scale,float speed,float wave,
        int debris,int size,int retry,float velocity,float deviation,float offset,float sound,CallbackInfo ci) {
        TrailRelay.INSTANCE.explosion(world,x,y,z,clouds,scale,speed,wave,Math.max(300,sound));
    }
}
