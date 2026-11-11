package com.seibel.distanthorizons.forge17.effects;

import java.nio.FloatBuffer;
import net.minecraft.entity.Entity;

/** Optional render-thread-only contract for FMUR's isolated colour/heat cameras. */
@cpw.mods.fml.relauncher.SideOnly(cpw.mods.fml.relauncher.Side.CLIENT)
public final class ActorCameraApi {
    public static final int API_VERSION=1;
    private ActorCameraApi() { }
    public static boolean isOwned() { return ActorRenderer.INSTANCE!=null && ActorRenderer.INSTANCE.cameraOwned(); }
    public static boolean begin(Object world,FloatBuffer projection,FloatBuffer view,double x,double y,double z,
                                int width,int height,float partial,float transitionStart,boolean heat) {
        return ActorRenderer.INSTANCE!=null && ActorRenderer.INSTANCE.cameraPass.begin(world,projection,view,x,y,z,width,height,partial,transitionStart,heat);
    }
    public static boolean renderNative(Entity entity) { return ActorRenderer.INSTANCE!=null && ActorRenderer.INSTANCE.cameraPass.nativeEntity(entity); }
    public static void renderDistant() { if(ActorRenderer.INSTANCE!=null) ActorRenderer.INSTANCE.cameraPass.distant(); }
    public static void end() { if(ActorRenderer.INSTANCE!=null) ActorRenderer.INSTANCE.cameraPass.end(); }
}
