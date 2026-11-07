package com.seibel.distanthorizons.forge17.effects;

import com.seibel.distanthorizons.core.config.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.entity.Entity;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/** Runs the real layer-3 entry point without a GL context or a Minecraft world constructor. */
final class MissileCameraOriginChecks {
    private static int checks;
    public static void main(String[] args) { System.out.println("PASS "+run()+" missile camera-origin checks"); }
    static int run() {
        try { return checkRenderOrigin(); }
        catch(ReflectiveOperationException error) { throw new AssertionError(error); }
    }
    private static int checkRenderOrigin() throws ReflectiveOperationException {
        Field unsafeField=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");unsafeField.setAccessible(true);
        sun.misc.Unsafe unsafe=(sun.misc.Unsafe)unsafeField.get(null);
        Minecraft mc=(Minecraft)unsafe.allocateInstance(Minecraft.class);
        Field singleton=null;
        for(Field field:Minecraft.class.getDeclaredFields())
            if(field.getType()==Minecraft.class && Modifier.isStatic(field.getModifiers())) singleton=field;
        if(singleton==null) throw new AssertionError("Minecraft singleton not found");
        singleton.setAccessible(true);Object previousMinecraft=singleton.get(null);
        DistantSmokeRenderer previousRenderer=DistantSmokeRenderer.INSTANCE;
        boolean enabled=Config.Client.Advanced.Graphics.GenericRendering.enableDistantMcheliSmoke.get();
        double oldX=EntityFX.interpPosX,oldY=EntityFX.interpPosY,oldZ=EntityFX.interpPosZ;
        try {
            singleton.set(null,mc);DistantSmokeRenderer.INSTANCE=null;
            Config.Client.Advanced.Graphics.GenericRendering.enableDistantMcheliSmoke.setWithoutFiringEvents(true);
            mc.renderViewEntity=(net.minecraft.entity.EntityLivingBase)unsafe.allocateInstance(net.minecraft.entity.passive.EntityPig.class);
            Entity view=mc.renderViewEntity;
            OriginWorld world=(OriginWorld)unsafe.allocateInstance(OriginWorld.class);
            MissileVisual missile=(MissileVisual)unsafe.allocateInstance(MissileVisual.class);missile.worldObj=world;
            // Layer 3 starts with the previous frame's particle origin. Include
            // lateral reversals, vertical movement, and large world coordinates.
            for(double step:new double[]{-3,-0.2,0,0.2,3}) for(float partial:new float[]{0,0.25F,0.75F,1}) {
                view.lastTickPosX=9793;view.lastTickPosY=385;view.lastTickPosZ=-2179;
                view.posX=view.lastTickPosX+step;view.posY=view.lastTickPosY+step*2;view.posZ=view.lastTickPosZ-step;
                EntityFX.interpPosX=9790;EntityFX.interpPosY=380;EntityFX.interpPosZ=-2180;
                world.calls=0;world.fail=false;
                missile.renderParticle(null,partial,0,0,0,0,0);
                check(world.calls>0,"real missile draw reached its native-binding check");
                check(Math.abs(world.x-(9793+step*partial))<1e-8 && Math.abs(world.y-(385+step*2*partial))<1e-8
                    && Math.abs(world.z-(-2179-step*partial))<1e-8,
                    "layer-3 missile uses this frame's interpolated view before model/capture work");
                check(EntityFX.interpPosX==9790 && EntityFX.interpPosY==380 && EntityFX.interpPosZ==-2180,
                    "missile draw restores the original particle origin for other effects");
            }
            world.fail=true;
            try { missile.renderParticle(null,0.5F,0,0,0,0,0);throw new AssertionError("Expected draw failure"); }
            catch(OriginFailure expected) {
                check(EntityFX.interpPosX==9790 && EntityFX.interpPosY==380 && EntityFX.interpPosZ==-2180,
                    "failed missile draw also restores the shared particle origin");
            }
            mc.renderViewEntity=null;world.fail=false;
            missile.renderParticle(null,0.5F,0,0,0,0,0);
            check(world.x==9790 && world.y==380 && world.z==-2180,"missing view retains the caller's origin");
            return checks;
        } finally {
            EntityFX.interpPosX=oldX;EntityFX.interpPosY=oldY;EntityFX.interpPosZ=oldZ;
            Config.Client.Advanced.Graphics.GenericRendering.enableDistantMcheliSmoke.setWithoutFiringEvents(enabled);
            DistantSmokeRenderer.INSTANCE=previousRenderer;singleton.set(null,previousMinecraft);
        }
    }
    private static void check(boolean result,String message) { if(!result) throw new AssertionError(message);checks++; }
    private static final class OriginFailure extends RuntimeException { }
    private static final class OriginWorld extends WorldClient {
        double x,y,z;int calls;boolean fail;
        private OriginWorld() { super(null,null,0,null,null); }
        @Override public Entity getEntityByID(int id) {
            calls++;x=EntityFX.interpPosX;y=EntityFX.interpPosY;z=EntityFX.interpPosZ;
            if(fail) throw new OriginFailure();
            return null;
        }
    }
}
