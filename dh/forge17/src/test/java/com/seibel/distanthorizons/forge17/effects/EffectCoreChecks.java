package com.seibel.distanthorizons.forge17.effects;

import java.util.ArrayList;
import java.util.UUID;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/** Standalone Java 8 checks; uses existing Forge/Netty dependencies, no new test framework. */
public final class EffectCoreChecks
{
    private static int checks;
    private static void check(boolean value,String name)
    {
        if(!value) throw new AssertionError(name);
        checks++;
    }
    private static TrailSample sample()
    {
        TrailSample s=new TrailSample();s.family=TrailSample.MCH;s.uuid=UUID.randomUUID();s.entityId=19;
        s.style="explode";s.count=6;s.tick=100;s.x0=100;s.y0=200;s.z0=-300;s.x=110;s.y=210;s.z=-310;s.scale=12;
        return s;
    }
    /** Inspect the compiled optional wrapper without loading Minecraft or HBM classes. */
    private static void checkNativeEmitter()
    {
        String path="/com/seibel/distanthorizons/forge17/effects/mixin/MixinHbmTrailSource.class";
        org.spongepowered.asm.lib.tree.ClassNode node=new org.spongepowered.asm.lib.tree.ClassNode();
        try(java.io.InputStream input=EffectCoreChecks.class.getResourceAsStream(path)) {
            if(input==null) throw new AssertionError("Missing compiled HBM wrapper");
            new org.spongepowered.asm.lib.ClassReader(input).accept(node,0);
        } catch(java.io.IOException e) { throw new AssertionError(e); }
        boolean found=false;
        for(org.spongepowered.asm.lib.tree.MethodNode method:node.methods) {
            if(!method.name.equals("dh$nativeSource")) continue;
            found=true;int calls=0;boolean earlyExit=false;
            for(org.spongepowered.asm.lib.tree.AbstractInsnNode instruction:method.instructions.toArray()) {
                int opcode=instruction.getOpcode();
                if(calls==0 && (instruction instanceof org.spongepowered.asm.lib.tree.JumpInsnNode
                    || instruction instanceof org.spongepowered.asm.lib.tree.TableSwitchInsnNode
                    || instruction instanceof org.spongepowered.asm.lib.tree.LookupSwitchInsnNode
                    || (opcode>=org.spongepowered.asm.lib.Opcodes.IRETURN && opcode<=org.spongepowered.asm.lib.Opcodes.RETURN))) earlyExit=true;
                if(instruction instanceof org.spongepowered.asm.lib.tree.MethodInsnNode) {
                    org.spongepowered.asm.lib.tree.MethodInsnNode call=(org.spongepowered.asm.lib.tree.MethodInsnNode)instruction;
                    if(call.owner.equals("com/llamalad7/mixinextras/injector/wrapoperation/Operation") && call.name.equals("call")) calls++;
                }
            }
            check(calls==1 && !earlyExit,"HBM wrapper always calls the original emitter once without a relay or distance gate");
        }
        check(found,"compiled HBM source wrapper is covered by the preservation check");
    }
    public static void main(String[] args)
    {
        check(TrailRules.flansPrimary(false,20,false,0,"smoke","boost")==null,"smokeless FMUR bullet never gains a trail");
        check(TrailRules.flansBoost(false,20,true,"boost")==null,"smokeless accelerating bullet never gains a booster effect");
        check(TrailRules.flansPrimary(true,1,false,0,"smoke","boost")==null,"FMUR source startup delay preserved");
        check("boost".equals(TrailRules.flansPrimary(true,20,true,3,"smoke","boost")),"VLS launch phase uses native boost style");
        check("smoke".equals(TrailRules.flansPrimary(true,20,true,0,"smoke","boost")),"post-VLS phase uses native trail style");
        check(TrailRules.flansPrimary(true,20,true,3,"smoke",null)==null,"VLS without a boost style does not invent smoke");
        check(TrailRules.flansBoost(true,20,false,"boost")==null,"coasting does not gain booster flames");
        check(TrailRules.hbmFuel("XENON")==null,"HBM xenon stays smokeless");
        check(TrailRules.hbmFuel("unreviewed")==null,"unknown fuel stays unsupported");
        check("exHydrogen".equals(TrailRules.hbmFuel("HYDROGEN")),"HBM hydrogen style preserved");
        check("exSolid".equals(TrailRules.hbmFuel("SOLID")),"HBM solid fuel style preserved");
        check(!TrailRules.continuous(10,"smoke",13,"smoke"),"no connecting smoke across inactive emission ticks");
        check(!TrailRules.continuous(10,"boost",11,"smoke"),"phase change starts a new emission segment");
        check(TrailRules.continuous(10,"smoke",11,"smoke"),"continuous flight joins adjacent observed samples");

        check(TrailRules.coveredFraction(0,0,0,10,0,0,5,0,0)==0.5,"handover trims the native-covered half of a segment");
        check(TrailRules.coveredFraction(0,0,0,10,0,0,15,0,0)==1,"server segments behind native smoke are not replayed");
        check(TrailRules.coveredFraction(0,0,0,10,0,0,5,30,0)==0,"unrelated path is not mistaken for existing smoke");
        check(TrailRules.coveredFraction(0,0,0,20,0,0,0,0,0)==0,"stale native emission cannot suppress the advancing server trail");
        check(TrailRules.coveredFraction(0,0,0,20,0,0,5,0,0)==0.25,"lagging tracked missile trims only its actually covered trail");
        check(TrailRules.coveredFraction(0,200,0,0,180,0,0,190,0)==0.5,"descending native coverage trims only the covered half");
        checkNativeEmitter();
        TrailSample s=sample();ByteBuf b=Unpooled.buffer();s.write(b);TrailSample decoded=TrailSample.read(b);
        check(decoded.uuid.equals(s.uuid) && decoded.x0==s.x0 && decoded.z==s.z && decoded.tick==s.tick,"wire roundtrip preserves source identity, time and geometry");
        check(!b.isReadable(),"sample consumes exactly its own payload");b.release();
        s.scale=Float.NaN;check(!s.valid(),"NaN rejected");s.scale=1;s.count=33;check(!s.valid(),"unbounded puff count rejected");
        s.count=1;s.style="smoke;command";check(!s.valid(),"invalid style rejected");
        s.style="smoke";s.x=Double.POSITIVE_INFINITY;check(!s.valid(),"invalid positions rejected");
        s.x=s.x0+10000;check(!s.valid(),"teleport-sized visual segments rejected");

        ArrayList<TrailSample> list=new ArrayList<TrailSample>();
        for(int i=0;i<TrailRelay.MAX_BATCH;i++) list.add(sample());
        TrailRelay.Batch batch=new TrailRelay.Batch(-1,list);b=Unpooled.buffer();batch.toBytes(b);
        check(b.readableBytes()<=8192,"maximum batch stays inside wire-size budget");
        TrailRelay.Batch received=new TrailRelay.Batch();received.fromBytes(b);
        check(received.valid && received.dimension==-1 && received.samples.size()==TrailRelay.MAX_BATCH,"bounded batch roundtrip");b.release();
        b=Unpooled.buffer();b.writeByte(TrailRelay.VERSION).writeInt(0).writeByte(33);
        received=new TrailRelay.Batch();received.fromBytes(b);check(!received.valid,"oversized record count rejected");b.release();
        b=Unpooled.buffer();b.writeByte(99).writeInt(0).writeByte(0);
        received=new TrailRelay.Batch();received.fromBytes(b);check(!received.valid,"incompatible protocol rejected");b.release();
        b=Unpooled.buffer();b.writeByte(TrailRelay.VERSION).writeInt(0).writeByte(1).writeByte(1);
        received=new TrailRelay.Batch();received.fromBytes(b);check(!received.valid,"truncated sample rejected without escaping decode");b.release();
        b=Unpooled.buffer();new TrailRelay.Batch(0,new ArrayList<TrailSample>()).toBytes(b);
        received=new TrailRelay.Batch();received.fromBytes(b);check(received.valid && received.samples.isEmpty(),"empty capability acknowledgement accepted");b.release();
        b=Unpooled.buffer();b.writeByte(TrailRelay.VERSION).writeByte(255).writeInt(8192);
        TrailRelay.Settings settings=new TrailRelay.Settings();settings.fromBytes(b);check(!settings.valid,"unknown client subscription flags rejected");b.release();

        com.seibel.distanthorizons.api.objects.math.DhApiMat4f projection=new com.seibel.distanthorizons.api.objects.math.DhApiMat4f();
        projection.m00=projection.m11=1;projection.m22=-181F/179F;projection.m23=-360F/179F;projection.m32=-1;
        check(EffectFrustum.withinExtendedView(projection,0,0,-100,0),"near visible smoke remains dispatchable when terrain viewport rejects it");
        check(EffectFrustum.withinExtendedView(projection,0,0,-182,0),"reported 182m transition remains dispatchable");
        check(EffectFrustum.withinExtendedView(projection,0,0,-247,0),"reported impact distance remains dispatchable");
        check(!EffectFrustum.withinExtendedView(projection,0,0,400,0),"near dispatch change retains behind-camera rejection");
        check(!EffectFrustum.withinExtendedView(projection,1000,0,-100,0),"near dispatch change retains side-plane rejection");
        check(!EffectFrustum.beyondNativeView(projection,0,0,-100,0),"near smoke keeps normal particle culling");
        check(EffectFrustum.beyondNativeView(projection,0,0,-400,0),"400m smoke survives native 180m far plane");
        check(EffectFrustum.beyondNativeView(projection,0,0,-4000,64),"long-range smoke reaches geometry capture");
        check(!EffectFrustum.beyondNativeView(projection,0,0,400,64),"particles behind camera stay culled");
        check(!EffectFrustum.beyondNativeView(projection,1000,0,-400,64),"offscreen smoke stays culled");
        check(!EffectFrustum.beyondNativeView(projection,0,1000,-400,64),"vertical side plane remains active");
        check(EffectFrustum.beyondNativeView(projection,420,0,-400,64),"billboards crossing a screen edge remain eligible");
        check(!EffectFrustum.beyondNativeView(projection,Double.NaN,0,-400,64),"nonfinite geometry rejected");
        // Turn the camera 90 degrees: far rejection must use camera depth, not a fixed world axis.
        com.seibel.distanthorizons.api.objects.math.DhApiMat4f yaw=new com.seibel.distanthorizons.api.objects.math.DhApiMat4f();
        yaw.m02=1;yaw.m11=1;yaw.m20=-1;yaw.m33=1;projection.multiply(yaw);
        check(EffectFrustum.beyondNativeView(projection,400,0,0,64),"rotated view admits the distant missile");
        check(!EffectFrustum.beyondNativeView(projection,-400,0,0,64),"rotated view rejects smoke behind camera");

        org.spongepowered.asm.lib.tree.ClassNode dispatch=new org.spongepowered.asm.lib.tree.ClassNode();
        String particle="net/minecraft/client/particle/EntityFX";
        String descriptor="(Lnet/minecraft/client/renderer/Tessellator;FFFFFF)V";
        for(String layer:new String[]{"renderParticles","renderLitParticles"})
        {
            org.spongepowered.asm.lib.tree.MethodNode method=new org.spongepowered.asm.lib.tree.MethodNode(
                0,"redirect$random$angelica$"+layer,"(L"+particle+";Lnet/minecraft/client/renderer/Tessellator;FFFFFF)V",null,null);
            method.instructions.add(new org.spongepowered.asm.lib.tree.MethodInsnNode(
                org.spongepowered.asm.lib.Opcodes.INVOKEVIRTUAL,particle,"func_70539_a",descriptor,false));
            method.instructions.add(new org.spongepowered.asm.lib.tree.InsnNode(org.spongepowered.asm.lib.Opcodes.RETURN));
            dispatch.methods.add(method);
        }
        int hooks=com.seibel.distanthorizons.forge17.EffectCaptureTransformer.patch(dispatch);
        check((hooks&65535)==2,"final merged particle calls are captured after other mods replace vanilla calls");
        check((hooks>>>16)==2,"both generated Angelica culling gates get extended-view dispatch");
        for(org.spongepowered.asm.lib.tree.MethodNode method:dispatch.methods)
        {
            check(method.instructions.getFirst().getOpcode()==org.spongepowered.asm.lib.Opcodes.ALOAD,"culling handoff runs before original viewport check");
            check(method.maxStack>=8,"culling handoff reserves the source renderer argument stack");
        }
        check(com.seibel.distanthorizons.forge17.EffectCaptureTransformer.patch(new org.spongepowered.asm.lib.tree.ClassNode())==0,
            "absent optional particle culling does not create hooks or require Angelica");

        check(NtmTrace.bin(127.99)==0 && NtmTrace.bin(128)==1,"trace preserves lower depth-bin boundary");
        check(NtmTrace.bin(255.99)==2 && NtmTrace.bin(256)==3,"trace separates both sides of native 256m boundary");
        check(NtmTrace.bin(320)==4 && NtmTrace.bin(512)==5,"trace separates far intervals");
        check(NtmTrace.bin(Double.NaN)==6,"unavailable camera depth is not reported as distant geometry");

        String rocket="com.hbm.particle.ParticleRocketFlame";
        check(HbmDrawRules.canRelocate(rocket,1,64,384,48,48,0,10),"reviewed complete NTM append can move after fade");
        check(!HbmDrawRules.canRelocate("com.hbm.particle.ParticleContrail",1,64,384,48,48,0,10),"unreviewed renderer keeps native path");
        check(!HbmDrawRules.canRelocate(rocket,3,64,384,48,48,0,10),"self-drawing layers cannot be withdrawn");
        check(!HbmDrawRules.canRelocate(rocket,1,64,352,48,48,0,9),"changed source topology fails to native rendering");
        check(!HbmDrawRules.canRelocate(rocket,1,64,384,39,48,0,10),"invalid native vertex accounting cannot be withdrawn");
        check(!HbmDrawRules.canRelocate(rocket,1,64,384,48,39,0,10),"invalid manually-added vertex accounting cannot be withdrawn");
        check(!HbmDrawRules.canRelocate(rocket,1,64,384,48,48,HbmDrawRules.NEAR_CAPACITY-9,10),"overflow preserves the original native append");
        check(HbmDrawRules.canRelocate(rocket,1,64,384,48,48,HbmDrawRules.NEAR_CAPACITY-10,10),"native relocation admits the exact capacity boundary");
        EffectQuadBuffer nativeGeometry=new EffectQuadBuffer(HbmDrawRules.NEAR_CAPACITY);
        int[] nativeQuad=new int[32];boolean nativeFull=true;
        for(int i=0;i<HbmDrawRules.NEAR_CAPACITY;i++) nativeFull &= nativeGeometry.add(nativeQuad,0,HbmDrawRules.NEAR_CAPACITY);
        check(nativeFull && !nativeGeometry.add(nativeQuad,0,HbmDrawRules.NEAR_CAPACITY),"native geometry has a separate bounded capacity without thinning");
        check(nativeGeometry.order[0]==0 && nativeGeometry.order[HbmDrawRules.NEAR_CAPACITY-1]==HbmDrawRules.NEAR_CAPACITY-1,"late native geometry preserves native append order");

        EffectQuadBuffer geometry=new EffectQuadBuffer();int[] raw=new int[32];raw[0]=123;
        check(geometry.add(raw,0,2),"first quad admitted");raw[0]=456;
        check(geometry.vertices[0]==123,"source tessellator data is copied, not retained");
        check(geometry.add(raw,0,2) && !geometry.add(raw,0,2),"configured geometry budget enforced");
        geometry.distance[0]=4;geometry.distance[1]=16;geometry.sortBackToFront();
        check(geometry.order[0]==1 && geometry.order[1]==0,"transparency order is back to front");
        geometry.texture[0]=1;geometry.texture[1]=2;
        check(!geometry.sameState(0,1),"different source textures never share a draw group");
        geometry.clear();check(geometry.size==0,"geometry never persists into another frame");
        boolean allAdmitted=true;
        for(int i=0;i<EffectQuadBuffer.CAPACITY;i++) allAdmitted &= geometry.add(raw,0,Integer.MAX_VALUE);
        check(allAdmitted,"hard-capacity geometry admission");
        check(!geometry.add(raw,0,Integer.MAX_VALUE),"hard geometry ceiling cannot be bypassed");
        geometry.clear();
        for(int i=0;i<8;i++) geometry.admit(raw,0,8,100+i);
        raw[0]=999;
        int selected=geometry.admit(raw,0,8,1);
        check(selected>=0 && geometry.vertices[selected*32]==999 && geometry.size==8,"later trail geometry can replace older reservoir entries without growing the budget");
        check(geometry.admit(raw,0,8,9999)<0,"worse reservoir candidates cannot evict selected geometry");
        System.out.println("PASS "+checks+" core checks (rules, protocol and frame geometry).");
    }
}
