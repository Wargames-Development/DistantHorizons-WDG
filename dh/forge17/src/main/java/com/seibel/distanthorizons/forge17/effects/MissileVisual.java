package com.seibel.distanthorizons.forge17.effects;

import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;

/** Bounded cosmetic proxies. No collision, world queries or gameplay simulation. */
public final class MissileVisual extends EntityFX {

    private final java.util.UUID uuid;
    private final int sourceId;
    private String variant;
    private double ax,ay,az;
    private final FlightSmoother smoother=new FlightSmoother();
    private final double[] pose=new double[6];
    private final TrailSample[] exhaust=new TrailSample[8];
    private final int[] exhaustReceived=new int[8];
    private final double[] enginePrevious=new double[6],engineCurrent=new double[6],sourceCenter=new double[6];
    private double lastExhaustTick=Double.NEGATIVE_INFINITY;
    private int quiet;
    private boolean reportedDraw,reportedBinding;
    private long nativeDrawFrame=-1;
    public MissileVisual(World world,TrailSample s) {
        super(world,s.x,s.y,s.z,0,0,0);
        uuid=s.uuid;sourceId=s.entityId;
        noClip=true;particleMaxAge=Integer.MAX_VALUE;
        // Layer 3 binds the NTM model texture itself. Atlas setters reject this layer.
        refresh(s);
        prevPosX=posX;prevPosY=posY;prevPosZ=posZ;

    }
    void refresh(TrailSample s) {
        smoother.offer(s.tick,s.x,s.y,s.z,s.mx,s.my,s.mz);
        if(variant==null) setPosition(s.x,s.y,s.z);
        variant=s.style.substring("dhBody.".length());quiet=0;
    }
    @Override public void onUpdate() {
        prevPosX=posX;prevPosY=posY;prevPosZ=posZ;
        smoother.advance();smoother.sample(0,pose);setPosition(pose[0],pose[1],pose[2]);
        if(++quiet>10) setDead();
    }
    int sourceId() { return sourceId; }
    boolean matchesNative(Entity entity) {
        return entity!=null && !entity.isDead && entity.worldObj==worldObj
            && MissileBinding.matches(sourceId,variant,entity.getEntityId(),entity.getClass().getName());
    }
    private Entity trackedEntity() {
        Entity entity=worldObj.getEntityByID(sourceId);
        if(!matchesNative(entity)) return null;
        if(!reportedBinding) {
            reportedBinding=true;
            org.apache.logging.log4j.LogManager.getLogger("DistantHorizons").info("DH NTM native binding: id="+sourceId+" type="+variant+" (spawn-ID binding; UUID equality is not required)");
        }
        return entity;
    }
    void displayPose(float partial,double[] out) {
        Entity entity=trackedEntity();
        if(entity==null) { smoother.sample(partial,out);return; }
        out[0]=entity.lastTickPosX+(entity.posX-entity.lastTickPosX)*partial;
        out[1]=entity.lastTickPosY+(entity.posY-entity.lastTickPosY)*partial;
        out[2]=entity.lastTickPosZ+(entity.posZ-entity.lastTickPosZ)*partial;
        double pitch=Math.toRadians(entity.prevRotationPitch+(entity.rotationPitch-entity.prevRotationPitch)*partial);
        double yaw=Math.toRadians(entity.prevRotationYaw+(entity.rotationYaw-entity.prevRotationYaw)*partial-90);
        out[3]=-Math.sin(pitch)*Math.cos(yaw);out[4]=Math.cos(pitch);out[5]=Math.sin(pitch)*Math.sin(yaw);
    }
    void observeExhaust(TrailSample sample,int clientTick) {
        if(sample.slot<0 || sample.slot>=exhaust.length) return;
        exhaust[sample.slot]=sample.copy();exhaustReceived[sample.slot]=clientTick;
    }
    void emitExhaust(int clientTick,java.util.function.Consumer<TrailSample> spawn) {
        if(!ownsModel()) return;
        double time=trackedEntity()!=null?clientTick:smoother.presentationTick();
        if(time<=lastExhaustTick) return;
        lastExhaustTick=time;
        if(trackedEntity()!=null) { displayPose(0,enginePrevious);displayPose(1,engineCurrent); }
        else { smoother.sample(-1,enginePrevious);smoother.sample(0,engineCurrent); }
        for(int slot=0;slot<exhaust.length;slot++) {
            TrailSample sample=exhaust[slot];
            if(sample==null || clientTick-exhaustReceived[slot]>6) continue;
            // Resolve the server nozzle offset only; the flight position comes from
            // the same smoother used by the model. Slot zero is the base for V2.
            smoother.sample(sample.tick-smoother.presentationTick(),sourceCenter);
            double ox=sample.x-sourceCenter[0],oy=sample.y-sourceCenter[1],oz=sample.z-sourceCenter[2];
            // For the single-nozzle models the OBJ engine origin is exactly zero.
            if(variant.equals("EntityMissileGeneric") || variant.equals("EntityMissileDecoy")
                || variant.equals("EntityMissileIncendiary") || variant.equals("EntityMissileCluster")
                || variant.equals("EntityMissileBunkerBuster") || variant.contains("Strong") || variant.equals("EntityMissileStealth")) ox=oy=oz=0;
            NativeTrailEmitter.emit(sample,enginePrevious,engineCurrent,ox,oy,oz,spawn);

        }
    }
    boolean ownsModel() {
        if(isDead || !DistantSmokeRenderer.canRenderBodies() || !NativeMissileMesh.replacesNative(variant)) return false;
        Entity nativeEntity=trackedEntity();
        return nativeEntity==null || !DistantSmokeRenderer.nativeModelVisible(nativeEntity,NativeMissileMesh.radius(variant));
    }
    void nativeRendered(long frame) { nativeDrawFrame=frame; }
    private boolean shouldDrawProxy() {
        boolean available=!isDead && DistantSmokeRenderer.canRenderBodies() && NativeMissileMesh.replacesNative(variant);
        boolean nativeOwns=!ownsModel();
        boolean draw=MissileViewRules.drawProxy(available,nativeOwns,nativeDrawFrame,DistantSmokeRenderer.frameId());
        NtmTrace.body(sourceId,trackedEntity()!=null,nativeOwns,nativeDrawFrame==DistantSmokeRenderer.frameId(),draw);
        return draw;
    }
    double presentationTick() { return smoother.presentationTick(); }
    @Override public int getFXLayer() { return 3; }
    @Override public void renderParticle(Tessellator t,float partial,float rx,float rz,float ryz,float rxy,float rxz) {
        if(!EffectSources.enabled(TrailSample.HBM)) return;
        if(!shouldDrawProxy()) return;
        displayPose(partial,pose);
        double x=pose[0]-interpPosX,y=pose[1]-interpPosY,z=pose[2]-interpPosZ;
        double length=Math.sqrt(pose[3]*pose[3]+pose[4]*pose[4]+pose[5]*pose[5]);
        ax=length>0.001?pose[3]/length:0;ay=length>0.001?pose[4]/length:1;az=length>0.001?pose[5]/length:0;
        org.lwjgl.opengl.GL11.glPushAttrib(org.lwjgl.opengl.GL11.GL_ALL_ATTRIB_BITS);
        try {
            Minecraft.getMinecraft().getTextureManager().bindTexture(NativeMissileMesh.texture(variant));
            org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_LIGHTING);
            org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_CULL_FACE);
            org.lwjgl.opengl.GL11.glCullFace(org.lwjgl.opengl.GL11.GL_BACK);
            org.lwjgl.opengl.GL11.glColor4f(1,1,1,1);
            org.lwjgl.opengl.GL11.glBlendFunc(org.lwjgl.opengl.GL11.GL_ONE,org.lwjgl.opengl.GL11.GL_ZERO);
            // Only the complete-model DH pass draws this geometry. Capture still
            // runs at tess.draw(), while native fragment/depth writes are suppressed.
            org.lwjgl.opengl.GL11.glColorMask(false,false,false,false);
            org.lwjgl.opengl.GL11.glDepthMask(false);
            tessBody(t,x,y,z,rx,rz,ryz,rxy,rxz);
        } finally { org.lwjgl.opengl.GL11.glPopAttrib(); }
    }
    private void tessBody(Tessellator t,double x,double y,double z,float rx,float rz,float ryz,float rxy,float rxz) {
        if(!reportedDraw) { reportedDraw=true;org.apache.logging.log4j.LogManager.getLogger("DistantHorizons").info("DH NTM native body geometry submitted: "+uuid+" "+variant); }
        NativeMissileMesh.draw(t,variant,x,y,z,ax,ay,az);
    }
}
