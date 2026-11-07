package com.seibel.distanthorizons.forge17.effects.mixin;
import com.seibel.distanthorizons.forge17.effects.TrailClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Pseudo
@Mixin(targets={"com.hbm.render.entity.rocket.RenderMissileGeneric","com.hbm.render.entity.rocket.RenderMissileStrong",
    "com.hbm.render.entity.rocket.RenderMissileHuge","com.hbm.render.entity.rocket.RenderMissileNuclear"},remap=false)
public class MixinHbmMissileRender {
    @Inject(method={"doRender","func_76986_a"},at=@At("HEAD"),cancellable=true,remap=false,require=1)
    private void dh$singleOwner(Entity entity,double x,double y,double z,float yaw,float partial,CallbackInfo ci) {
        if(TrailClient.ownsMissile(entity)) ci.cancel();
    }
    @Inject(method={"doRender","func_76986_a"},at=@At("TAIL"),remap=false,require=1)
    private void dh$nativeDrawn(Entity entity,double x,double y,double z,float yaw,float partial,CallbackInfo ci) {
        TrailClient.nativeMissileRendered(entity);
    }
}
