package com.seibel.distanthorizons.forge17.effects;

import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiRenderParam;
import com.seibel.distanthorizons.api.objects.math.DhApiMat4f;
import com.seibel.distanthorizons.core.util.math.DhVec3d;
import java.lang.reflect.Field;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;

/** Exercise the actor's real camera snapshot with a deliberately stale RenderManager. */
final class ActorCameraOriginChecks {
    private static void set(Object object,String name,Object value) throws Exception {
        Field field=object.getClass().getDeclaredField(name);field.setAccessible(true);field.set(object,value);
    }
    private static double read(Object object,String name) throws Exception {
        Field field=object.getClass().getDeclaredField(name);field.setAccessible(true);return field.getDouble(object);
    }
    static int run() {
        try { return check(); } catch(Exception error) { throw new AssertionError("Actor camera-origin fixture",error); }
    }
    private static int check() throws Exception {
        Field access=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");access.setAccessible(true);
        sun.misc.Unsafe allocator=(sun.misc.Unsafe)access.get(null);
        ActorRenderer renderer=(ActorRenderer)allocator.allocateInstance(ActorRenderer.class);
        set(renderer,"frame",new DhApiRenderParam());DhApiMat4f captured=new DhApiMat4f();set(renderer,"nativeMvp",captured);
        Entity camera=(Entity)allocator.allocateInstance(net.minecraft.entity.passive.EntityPig.class);
        RenderManager manager=RenderManager.instance;
        double oldX=manager.viewerPosX,oldY=manager.viewerPosY,oldZ=manager.viewerPosZ;
        int checks=0;
        try {
            DhApiRenderParam input=new DhApiRenderParam();
            // Translation includes a third-person/eye offset. Never subtract that a second time.
            input.mcModelViewMatrix.m00=input.mcModelViewMatrix.m11=input.mcModelViewMatrix.m22=input.mcModelViewMatrix.m33=1;
            input.mcModelViewMatrix.m03=2;input.mcModelViewMatrix.m13=-1.62F;input.mcModelViewMatrix.m23=-4;
            input.dhModelViewMatrix.set(input.mcModelViewMatrix);
            for(double step:new double[]{-3,-.2,0,.2,3}) for(float partial:new float[]{0,.25F,.75F,1}) for(double fov:new double[]{70,30,8}) {
                input.partialTicks=partial;
                input.mcProjectionMatrix.m00=(float)(1/Math.tan(Math.toRadians(fov)/2)/(16.0/9));
                input.mcProjectionMatrix.m11=(float)(1/Math.tan(Math.toRadians(fov)/2));
                input.mcProjectionMatrix.m22=-1.001F;input.mcProjectionMatrix.m23=-.1F;input.mcProjectionMatrix.m32=-1;
                input.dhProjectionMatrix.set(input.mcProjectionMatrix);
                camera.lastTickPosX=9793;camera.lastTickPosY=185;camera.lastTickPosZ=-2179;
                camera.posX=9793+step;camera.posY=185+step*2;camera.posZ=-2179-step;
                manager.viewerPosX=9790;manager.viewerPosY=180;manager.viewerPosZ=-2180;
                double x=9793+step*partial,y=185+step*2*partial,z=-2179-step*partial;
                DhVec3d terrainOrigin=new DhVec3d(x+2,y+1,z-4);
                require(renderer.snapshotCamera(input,camera,terrainOrigin),"current actor camera accepted");checks++;
                require(Math.abs(read(renderer,"nx")-x)<1e-8 && Math.abs(read(renderer,"ny")-y)<1e-8 && Math.abs(read(renderer,"nz")-z)<1e-8,
                    "actor snapshot uses current interpolation despite stale entity-render cache");checks++;
                require(read(renderer,"dx")==terrainOrigin.x && read(renderer,"dy")==terrainOrigin.y && read(renderer,"dz")==terrainOrigin.z,
                    "terrain keeps its independently captured origin");checks++;
                // Independent projection oracle for a stationary world-space vehicle.
                double vx=9797.5-x,vy=178.35-y,vz=-2997.5-z;
                double w=-(vz-4);
                double expectedX=input.mcProjectionMatrix.m00*(vx+2)/w;
                double expectedY=input.mcProjectionMatrix.m11*(vy-1.62F)/w;
                double actualW=captured.m30*vx+captured.m31*vy+captured.m32*vz+captured.m33;
                double actualX=(captured.m00*vx+captured.m01*vy+captured.m02*vz+captured.m03)/actualW;
                double actualY=(captured.m10*vx+captured.m11*vy+captured.m12*vz+captured.m13)/actualW;
                require(Math.abs(actualX-expectedX)<1e-7 && Math.abs(actualY-expectedY)<1e-7,
                    "stationary vehicle projection follows current camera at normal and zoomed FOV");checks++;
                require(manager.viewerPosX==9790 && manager.viewerPosY==180 && manager.viewerPosZ==-2180,
                    "actor snapshot does not overwrite shared native render state");checks++;
            }
            require(!renderer.snapshotCamera(input,null,new DhVec3d(0,0,0)),"missing view cannot reuse stale cache");checks++;
            input.partialTicks=Float.NaN;
            require(!renderer.snapshotCamera(input,camera,new DhVec3d(0,0,0)),"invalid interpolation withheld");checks++;
            // Guard the real terrain callback, not just the helper: it must use this
            // snapshot and must never regress to reading the later entity-render cache.
            try(java.io.InputStream stream=ActorRenderer.class.getResourceAsStream("ActorRenderer.class")) {
                org.spongepowered.asm.lib.tree.ClassNode node=new org.spongepowered.asm.lib.tree.ClassNode();
                new org.spongepowered.asm.lib.ClassReader(stream).accept(node,0);boolean used=false;
                for(org.spongepowered.asm.lib.tree.MethodNode method:node.methods) if(method.name.equals("snapshot"))
                    for(org.spongepowered.asm.lib.tree.AbstractInsnNode insn:method.instructions.toArray()) {
                        if(insn instanceof org.spongepowered.asm.lib.tree.FieldInsnNode) {
                            org.spongepowered.asm.lib.tree.FieldInsnNode field=(org.spongepowered.asm.lib.tree.FieldInsnNode)insn;
                            require(!field.owner.equals("net/minecraft/client/renderer/entity/RenderManager") || !field.name.startsWith("viewerPos"),"terrain callback cannot read stale RenderManager origin");
                        }
                        if(insn instanceof org.spongepowered.asm.lib.tree.MethodInsnNode)
                            used|=((org.spongepowered.asm.lib.tree.MethodInsnNode)insn).name.equals("snapshotCamera");
                    }
                require(used,"real terrain callback uses the tested camera snapshot");checks++;
            }
            return checks;
        } finally { manager.viewerPosX=oldX;manager.viewerPosY=oldY;manager.viewerPosZ=oldZ; }
    }
    private static void require(boolean value,String message) { if(!value) throw new AssertionError(message); }
}
