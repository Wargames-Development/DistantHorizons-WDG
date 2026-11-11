package com.seibel.distanthorizons.forge17.effects;

import java.util.ArrayList;
import java.util.UUID;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/** Standalone Java 8 checks; uses existing Forge/Netty dependencies, no new test framework. */
public final class EffectCoreChecks
{
    private static int checks;
    private static void checkPrivateCameraScope() {
        check(!DistantSmokeRenderer.isCameraParticlePass(),"primary frame starts outside private particle scope");
        for(boolean heat:new boolean[]{false,true}) {
            DistantSmokeRenderer.renderCameraParticles(() -> {
                check(DistantSmokeRenderer.isCameraParticlePass(),"private particle scope is active");
                check(DistantSmokeRenderer.isCameraThermalMask()==heat,"private camera distinguishes colour and heat");
                check(!DistantSmokeRenderer.begin(null),"private particles cannot enter primary capture");
                try {
                    DistantSmokeRenderer.renderCameraParticles(() -> {},false);
                    throw new AssertionError("Expected recursive camera rejection");
                } catch(IllegalStateException expected) {
                    check(DistantSmokeRenderer.isCameraThermalMask()==heat,"rejected recursion preserves outer scope");
                }
            },heat);
            check(!DistantSmokeRenderer.isCameraParticlePass(),"private scope resets after successful draw");
            try {
                DistantSmokeRenderer.renderCameraParticles(() -> { throw new IllegalArgumentException("fixture"); },heat);
                throw new AssertionError("Expected particle failure");
            } catch(IllegalArgumentException expected) {
                check(!DistantSmokeRenderer.isCameraParticlePass(),"private scope resets after failed draw");
            }
        }
    }
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
    /** Exercise the hook against Angelica's real callback bytecode, not a recreated
     * renderWorld fixture. Optional file args also check the installed release jar. */
    private static void checkShaderStage(java.io.InputStream input) throws java.io.IOException {
        org.spongepowered.asm.lib.tree.ClassNode node=new org.spongepowered.asm.lib.tree.ClassNode();
        new org.spongepowered.asm.lib.ClassReader(input).accept(node,0);
        org.spongepowered.asm.lib.tree.MethodNode callback=null;
        for(org.spongepowered.asm.lib.tree.MethodNode method:node.methods)
            if(method.name.equals("iris$endLevelRender")) callback=method;
        check(callback!=null,"Angelica's actual end-level callback exists");
        boolean forgeBefore=false;
        for(org.spongepowered.asm.lib.tree.AnnotationNode annotation:callback.visibleAnnotations) {
            if(!annotation.desc.endsWith("/Inject;")) continue;
            for(int i=0;i<annotation.values.size();i+=2) if(annotation.values.get(i).equals("at")) {
                for(Object value:(java.util.List<?>)annotation.values.get(i+1)) {
                    org.spongepowered.asm.lib.tree.AnnotationNode at=(org.spongepowered.asm.lib.tree.AnnotationNode)value;
                    boolean forge=false,after=false;
                    for(int a=0;a<at.values.size();a+=2) {
                        Object key=at.values.get(a),v=at.values.get(a+1);
                        if(key.equals("target")) forge=v.toString().contains("ForgeHooksClient;dispatchRenderLast");
                        if(key.equals("shift") && v instanceof String[]) after=((String[])v)[1].equals("AFTER");
                    }
                    forgeBefore|=forge && !after;
                }
            }
        }
        check(forgeBefore,"Angelica finalization callback runs before Forge world-last");
        check(com.seibel.distanthorizons.forge17.EffectCaptureTransformer.patchShaderFinalization(node)==1,
            "real Angelica callback receives exactly one pre-composite hook");
        boolean adjacent=false;
        for(org.spongepowered.asm.lib.tree.AbstractInsnNode instruction:callback.instructions.toArray()) {
            if(!(instruction instanceof org.spongepowered.asm.lib.tree.MethodInsnNode)) continue;
            org.spongepowered.asm.lib.tree.MethodInsnNode call=(org.spongepowered.asm.lib.tree.MethodInsnNode)instruction;
            if(!call.name.equals("finalizeLevelRendering")) continue;
            org.spongepowered.asm.lib.tree.MethodInsnNode before=(org.spongepowered.asm.lib.tree.MethodInsnNode)instruction.getPrevious();
            adjacent=before.name.equals("beforeShaderComposite") && before.desc.equals("()V");
        }
        check(adjacent,"effect draw is immediately before actual finalization with pipeline receiver preserved");
        check(com.seibel.distanthorizons.forge17.EffectCaptureTransformer.patchShaderFinalization(node)==0,
            "pre-composite hook is idempotent");
        // Writing the patched class checks instruction/frame serialization too.
        org.spongepowered.asm.lib.ClassWriter writer=new org.spongepowered.asm.lib.ClassWriter(0);
        node.accept(writer);check(writer.toByteArray().length>0,"patched real callback serializes");
    }
    private static void checkProjectionSupplier(java.util.zip.ZipFile zip) throws java.io.IOException {
        org.spongepowered.asm.lib.tree.ClassNode node=new org.spongepowered.asm.lib.tree.ClassNode();
        try(java.io.InputStream input=zip.getInputStream(zip.getEntry("net/coderbot/iris/compat/dh/DHCompat.class"))) {
            new org.spongepowered.asm.lib.ClassReader(input).accept(node,0);
        }
        boolean found=false,near=false,far=false,perspective=false;
        for(org.spongepowered.asm.lib.tree.MethodNode method:node.methods) if(method.name.equals("getProjection")) {
            found=(method.access&org.spongepowered.asm.lib.Opcodes.ACC_PUBLIC)!=0 && (method.access&org.spongepowered.asm.lib.Opcodes.ACC_STATIC)!=0;
            for(org.spongepowered.asm.lib.tree.AbstractInsnNode instruction:method.instructions.toArray()) if(instruction instanceof org.spongepowered.asm.lib.tree.MethodInsnNode) {
                String name=((org.spongepowered.asm.lib.tree.MethodInsnNode)instruction).name;
                near|=name.equals("getNearPlane");far|=name.equals("getFarPlane");perspective|=name.equals("setPerspective");
            }
        }
        check(found && near && far && perspective,"Angelica exposes the actual separately generated terrain projection");
        node=new org.spongepowered.asm.lib.tree.ClassNode();
        try(java.io.InputStream input=zip.getInputStream(zip.getEntry("net/coderbot/iris/uniforms/MatrixUniforms.class"))) {
            new org.spongepowered.asm.lib.ClassReader(input).accept(node,0);
        }
        boolean supplier=false;
        for(org.spongepowered.asm.lib.tree.MethodNode method:node.methods) for(org.spongepowered.asm.lib.tree.AbstractInsnNode instruction:method.instructions.toArray()) {
            if(!(instruction instanceof org.spongepowered.asm.lib.tree.InvokeDynamicInsnNode)) continue;
            for(Object arg:((org.spongepowered.asm.lib.tree.InvokeDynamicInsnNode)instruction).bsmArgs) if(arg instanceof org.spongepowered.asm.lib.Handle) {
                org.spongepowered.asm.lib.Handle handle=(org.spongepowered.asm.lib.Handle)arg;
                supplier|=handle.getOwner().equals("net/coderbot/iris/compat/dh/DHCompat") && handle.getName().equals("getProjection");
            }
        }
        check(supplier,"the pack dhProjection uniform is supplied by the exact bridge method");
    }
    private static void checkShaderStages(String[] files) {
        java.util.List<String> fixtures=new java.util.ArrayList<String>(java.util.Arrays.asList(files));
        String compileFixture=System.getProperty("dh.effects.angelicaFixture");
        check(compileFixture!=null,"compile-time Angelica jar is available for stage checks");
        fixtures.add(compileFixture);
        for(String file:fixtures) try(java.util.zip.ZipFile zip=new java.util.zip.ZipFile(file);
            java.io.InputStream input=zip.getInputStream(zip.getEntry("com/gtnewhorizons/angelica/mixins/early/shaders/MixinEntityRenderer.class"))) {
            checkShaderStage(input);checkProjectionSupplier(zip);checks+=ShaderCloudContractChecks.run(zip);checks+=NativeCoverageChecks.run(file);
            System.out.println("Checked actual Angelica shader stage/projection/cloud/coverage API: "+file);
        } catch(java.io.IOException error) { throw new AssertionError(error); }
        check(com.seibel.distanthorizons.forge17.EffectCaptureTransformer.patchShaderFinalization(new org.spongepowered.asm.lib.tree.ClassNode())==0,
            "shaderless renderer receives no optional pipeline hook");
    }
    private static void checkProjectionCopy() {
        java.nio.FloatBuffer buffer=java.nio.FloatBuffer.allocate(16);
        for(int i=0;i<16;i++) buffer.put(i,i+1);
        com.seibel.distanthorizons.api.objects.math.DhApiMat4f copy=ShaderEffectsTarget.readColumnMajorProjection(buffer);
        check(copy.m00==1 && copy.m01==5 && copy.m03==13 && copy.m23==15 && copy.m32==12,
            "runtime JOML column-major projection is copied into DH row-major fields without transposing depth coefficients");
        buffer.put(6,Float.NaN);boolean rejected=false;
        try { ShaderEffectsTarget.readColumnMajorProjection(buffer); } catch(IllegalArgumentException expected) { rejected=true; }
        check(rejected,"nonfinite runtime projection fails closed before native suppression");
    }
    private static final class ProbeQueries implements ShaderEffectsProbe.Queries {
        String fail;boolean ready;int reads,calls;
        private void call(String name) {
            calls++;
            if(name.equals(fail)) throw new NoSuchMethodError("injected diagnostic failure: "+name);
        }
        public int current() { call("current");return 0; }
        public int create() { call("create");return 1; }
        public void begin(int id) { call("begin"); }
        public void end() { call("end"); }
        public boolean available(int id) { call("available");return ready; }
        public int result(int id) { call("result");reads++;return 1234; }
    }
    private static void checkProbeFailureIsolation() {
        ProbeQueries api=new ProbeQueries();ShaderEffectsProbe probe=new ShaderEffectsProbe(api);
        Object world=new Object();
        check(probe.start(world,"fixture"),"automatic probe can start without touching simulation");
        probe.begin(1,3);probe.end();probe.poll();
        check(api.reads==0,"pending probe never reads unavailable GPU results");
        api.ready=true;probe.poll();
        check(api.reads==1 && probe.diagnostic().contains("plume=-/-/-/1234"),"available probe result is collected once");
        probe.poll();check(api.reads==1,"completed probe is not read twice");
        for(String failure:new String[]{"current","create","begin","end","available","result"}) {
            api=new ProbeQueries();api.fail=failure;api.ready=true;probe=new ShaderEffectsProbe(api);
            if(probe.start(world,"failure fixture")) { probe.begin(0,3);probe.end();probe.poll(); }
            check(probe.diagnostic().contains("diagnostics disabled: NoSuchMethodError"),"query "+failure+" linkage failure is contained");
            int calls=api.calls;
            probe.poll();probe.begin(0,0);probe.end();
            check(!probe.start(world,"retry") && calls==api.calls,"disabled "+failure+" probe makes no more GL calls");
        }
    }
    /** Layer 3 uses a model texture; vanilla atlas setters throw for this layer.
     * Inspect bytecode without booting the game, as in the native-emitter check. */
    private static void checkBodyTextureContract() {
        org.spongepowered.asm.lib.tree.ClassNode node=new org.spongepowered.asm.lib.tree.ClassNode();
        try(java.io.InputStream input=EffectCoreChecks.class.getResourceAsStream("/com/seibel/distanthorizons/forge17/effects/MissileVisual.class")) {
            if(input==null) throw new AssertionError("Missing compiled missile visual");
            new org.spongepowered.asm.lib.ClassReader(input).accept(node,0);
        } catch(java.io.IOException error) { throw new AssertionError(error); }
        boolean layerThree=false,atlasCall=false;
        for(org.spongepowered.asm.lib.tree.MethodNode method:node.methods) {
            if(method.name.equals("getFXLayer") || method.name.equals("func_70537_b")) {
                for(org.spongepowered.asm.lib.tree.AbstractInsnNode instruction:method.instructions.toArray())
                    if(instruction.getOpcode()==org.spongepowered.asm.lib.Opcodes.ICONST_3) layerThree=true;
            }
            for(org.spongepowered.asm.lib.tree.AbstractInsnNode instruction:method.instructions.toArray()) {
                if(!(instruction instanceof org.spongepowered.asm.lib.tree.MethodInsnNode)) continue;
                String name=((org.spongepowered.asm.lib.tree.MethodInsnNode)instruction).name;
                if(name.equals("setParticleTextureIndex") || name.equals("func_70536_a")
                    || name.equals("setParticleIcon") || name.equals("func_110125_a")) atlasCall=true;
            }
        }
        check(layerThree,"missile body owns its model texture on layer 3");
        check(!atlasCall,"layer 3 body never invokes a vanilla atlas texture setter (X25 crash regression)");
    }
    private static void checkFlightSmoothing() {
        FlightSmoother flight=new FlightSmoother();double[] pose=new double[6];
        flight.offer(100,0,0,0,10,0,0);
        for(int tick=101;tick<=112;tick++) {
            if((tick&1)==0) flight.offer(tick,(tick-100)*10,0,0,10,0,0);
            flight.advance();
            if(tick>=106) {
                flight.sample(0,pose);double start=pose[0];
                flight.sample(0.5,pose);check(Math.abs(pose[0]-start-5)<1e-8,"constant-speed motion is smooth between packet and client ticks");
            }
        }
        flight.offer(110,-999,0,0,10,0,0);flight.sample(0,pose);
        check(pose[0]>=0,"out-of-order flight sample cannot rewind the missile");
        for(int i=0;i<30;i++) flight.advance();flight.sample(0.9,pose);
        check(Math.abs(pose[0]-120)<1e-8,"missing packets hold last observed position without extrapolation");
        flight.offer(200,5000,0,0,0,1,0);flight.sample(0,pose);
        check(pose[0]==5000,"teleport or long gap resets interpolation instead of sweeping across the world");
    }
    private static void checkExhaustClockTransitions() {
        MissileExhaustClock clock=new MissileExhaustClock();
        check(clock.admit(false,5400,26165600),"remote exhaust admits the server-world presentation tick");
        check(clock.admit(true,5401,26165601),"binding a tracked overhead missile cannot compare local tick 5401 against world tick 26165600");
        check(!clock.admit(true,5401,26165601),"tracked exhaust emits at most once in a client tick");
        check(clock.admit(true,5402,26165601),"tracked pose can advance while the relay playhead holds between packets");
        check(clock.admit(false,5403,26165601),"losing native tracking resumes the independent relay clock");
        check(!clock.admit(false,5404,26165601),"held relay pose does not manufacture repeated exhaust");
        check(clock.admit(false,5405,26165602),"fresh relay pose resumes exhaust after a packet gap");

        clock=new MissileExhaustClock();
        check(clock.admit(true,10000,10),"native-first flight accepts a local client tick");
        check(clock.admit(false,10001,11),"reverse epoch ordering also permits native-to-relay transition");
        check(clock.admit(true,10002,12),"rebinding uses only the previous native tick");

        clock=new MissileExhaustClock();int admitted=0;
        // A missile becomes client-tracked high above the impact chunk, long
        // before native particle range. Exhaust must continue across that event.
        for(int step=0;step<200;step++) {
            boolean tracked=step>=40 && step<170;
            if(clock.admit(tracked,5000+step,26000000+step)) admitted++;
            check(!clock.admit(tracked,5000+step,26000000+step),"same-pose duplicate remains rejected across tracking changes");
        }
        check(admitted==200,"every advancing flight pose retains exhaust before, during and after native tracking");
    }
    private static void checkTrailTimeline() {
        TrailBirthQueue queue=new TrailBirthQueue();
        TrailSample segment=sample();segment.tick=102;segment.x0=0;segment.x=20;
        segment.y0=segment.y=segment.z0=segment.z=0;
        java.util.List<TrailSample> born=new java.util.ArrayList<TrailSample>();
        queue.add(segment,8,0);
        queue.drain(0,puff -> 100,puff -> born.add(puff));
        check(born.isEmpty(),"trail cannot spawn ahead of delayed missile playhead");
        queue.drain(1,puff -> 100.5,puff -> born.add(puff));
        check(born.size()==2 && born.get(1).x<5,"births spread along observed segment up to the missile time");
        queue.drain(2,puff -> 101,puff -> born.add(puff));
        check(born.size()==4 && born.get(3).x<10,"next tick emits next part of trail rather than whole packet burst");
        queue.drain(3,puff -> 102,puff -> born.add(puff));
        check(born.size()==8,"scheduling preserves total native puff count");
        queue.drain(4,puff -> 102,puff -> born.add(puff));
        check(born.size()==8,"scheduled births are never replayed twice");
        queue.add(segment,8,0);queue.drain(20,puff -> 102,puff -> born.add(puff));
        check(born.size()==8,"expired queue does not release stale trail bursts");
        queue.add(segment,8,0);queue.clear();queue.drain(1,puff -> 102,puff -> born.add(puff));
        check(born.size()==8,"world cleanup removes pending trail births");
        queue.add(segment,8,0);queue.drain(1,puff -> Double.NaN,puff -> born.add(puff));
        check(born.size()==12,"trail-only sources also spread delivery across client ticks");
    }
    private static void checkNativeTrailSpacing() {
        TrailBirthQueue queue=new TrailBirthQueue();TrailSample segment=sample();
        segment.family=TrailSample.HBM;segment.style="missileContrail";segment.tick=102;
        segment.x0=0;segment.x=40;segment.y0=segment.y=segment.z0=segment.z=0;segment.scale=0.5F;
        java.util.List<TrailSample> born=new java.util.ArrayList<TrailSample>();
        queue.add(segment,16,0);queue.drain(0,puff -> 100.9,puff -> born.add(puff));
        check(born.isEmpty(),"native puff group waits for its emission tick on body timeline");
        queue.drain(1,puff -> 101,puff -> born.add(puff));
        check(born.size()==10,"native speed cap emits ten puffs per tick even above ten blocks per tick");
        for(int i=0;i<10;i++) check(born.get(i).x==i && born.get(i).scale==0.5F,"native one-block spacing and small HE scale preserved");
        queue.drain(2,puff -> 102,puff -> born.add(puff));
        check(born.size()==20 && born.get(10).x==20 && born.get(19).x==29,"next native tick starts from next previous position without stretching the plume");
        queue.clear();born.clear();segment.x=5;queue.add(segment,1,0);
        queue.drain(2,puff -> 102,puff -> born.add(puff));
        check(born.size()==6 && born.get(2).x==2 && born.get(3).x==2.5,"fractional speed uses native ceiling count and unit spacing");
    }
    private static void checkLargeBufferSorting() {
        EffectQuadBuffer buffer=new EffectQuadBuffer(8192);int[] quad=new int[32];
        for(int i=0;i<8192;i++) { int q=buffer.admit(quad,0,8192,i);buffer.distance[q]=(i*97)%100; }
        buffer.sortBackToFront();boolean[] seen=new boolean[8192];
        for(int i=0;i<8192;i++) {
            int q=buffer.order[i];check(!seen[q],"large-buffer sort preserves each admitted quad once");seen[q]=true;
            if(i>0) check(buffer.distance[buffer.order[i-1]]>=buffer.distance[q],"large-buffer sort is back-to-front");
        }
        buffer.clear();buffer.sortBackToFront();check(buffer.size==0,"empty frame sorts without stale geometry");
    }
    private static void checkWaitingTrailOwner() {
        TrailBirthQueue queue=new TrailBirthQueue();TrailSample sample=sample();sample.family=TrailSample.HBM;
        sample.style="missileContrail";sample.tick=102;sample.x0=0;sample.x=20;sample.y0=sample.y=sample.z0=sample.z=0;
        java.util.List<TrailSample> born=new java.util.ArrayList<TrailSample>();queue.add(sample,20,0);
        queue.drain(1,puff -> Double.NEGATIVE_INFINITY,puff -> born.add(puff));
        check(born.isEmpty(),"no future plume while body owner packet is pending");
        queue.drain(2,puff -> 101,puff -> born.add(puff));
        check(born.size()==10 && born.get(9).x==9,"owner arrival releases only emissions up to its delayed timeline");
        queue.drain(3,puff -> 101,puff -> born.add(puff));
        check(born.size()==10,"stalled body cannot release newer plume");
        queue.drain(4,puff -> 102,puff -> born.add(puff));
        check(born.size()==20,"body and trail progress together after a stall");
    }
    private static void checkEngineAnchoring() {
        TrailSample template=sample();template.scale=0.5F;template.my=1;template.y=999;
        java.util.List<TrailSample> born=new java.util.ArrayList<TrailSample>();
        double[] previous={0,100,0,0,5,0},ascending={0,120,0,0,5,0};
        NativeTrailEmitter.emit(template,previous,ascending,0,0,0,puff -> born.add(puff));
        check(born.size()==10,"displayed engine retains native ten-puff cap at high speed");
        for(int i=0;i<born.size();i++) {
            TrailSample puff=born.get(i);
            check(puff.y==100+i && puff.y<=ascending[1],"ascending flame originates behind displayed engine, not stale packet position");
            check(puff.my==-1 && puff.scale==0.5F,"ascending flame moves away from nose with native scale");
        }
        born.clear();double[] descending={0,80,0,0,-5,0};
        NativeTrailEmitter.emit(template,previous,descending,0,0,0,puff -> born.add(puff));
        for(int i=0;i<born.size();i++) {
            TrailSample puff=born.get(i);
            check(puff.y==100-i && puff.y>=descending[1],"descending flame stays above the engine at its native spacing");
            check(puff.my==1,"descending flame direction follows displayed model rather than packet direction");
        }
        born.clear();double[] stationary={0,100,0,0,1,0};
        NativeTrailEmitter.emit(template,stationary,stationary,0,0,0,puff -> born.add(puff));
        check(born.size()==1 && born.get(0).y==100,"stationary engine has no artificial ten-block offset");
        born.clear();NativeTrailEmitter.emit(template,previous,ascending,1,0,-1,puff -> born.add(puff));
        check(born.get(0).x==1 && born.get(0).z==-1,"multiple nozzles retain their explicit offset from displayed engine");
    }
    private static void checkNativeNearOwnership() {
        check(MissileViewRules.nativeOwns(100,99,0,0,0.01,16),"native missile stays authoritative while complete model is inside view");
        check(!MissileViewRules.nativeOwns(100,99.9,0,0,0.01,16),"whole model changes ownership before native clips part of it");
        check(!MissileViewRules.nativeOwns(100,101,0,0,0.01,16),"far missile uses LOD even while entity is still tracked");
        check(!MissileViewRules.nativeOwns(-100,-101,0,0,0.01,16),"behind-camera model does not count as a visible native missile");
        check(!MissileViewRules.nativeOwns(Double.NaN,99,0,0,0.01,16),"invalid projection does not claim native ownership");
    }
    private static void checkForgeSpawnIdentity() {
        String type="com.hbm.entity.missile.EntityMissileTier1$EntityMissileGeneric";
        check(MissileBinding.matches(38,"EntityMissileGeneric",38,type),"Forge spawn entity ID binds native missile without cross-side UUID equality");
        check(!MissileBinding.matches(38,"EntityMissileGeneric",39,type),"different native entity ID cannot claim a relay body");
        check(!MissileBinding.matches(38,"EntityMissileGeneric",38,"com.hbm.entity.missile.EntityMissileTier2$EntityMissileStrong"),"entity ID alone cannot bind the wrong missile type");
        check(!MissileBinding.matches(38,"EntityMissileGeneric",38,"other.mod.EntityMissileGeneric"),"unrelated mod type cannot bind the same ID");
        check(!MissileBinding.matches(38,"EntityMissileGeneric",38,null),"missing native entity does not bind");
        // Check the actual compiled native lookup paths as well as the matching rule.
        for(String owner:new String[]{"MissileVisual","TrailClient"}) {
            org.spongepowered.asm.lib.tree.ClassNode node=new org.spongepowered.asm.lib.tree.ClassNode();
            try(java.io.InputStream input=EffectCoreChecks.class.getResourceAsStream("/com/seibel/distanthorizons/forge17/effects/"+owner+".class")) {
                if(input==null) throw new AssertionError("missing native binding code");
                new org.spongepowered.asm.lib.ClassReader(input).accept(node,0);
            } catch(java.io.IOException error) { throw new AssertionError(error); }
            boolean checked=false;
            for(org.spongepowered.asm.lib.tree.MethodNode method:node.methods) {
                if(!(method.name.equals("trackedEntity") || method.name.equals("matchesNative") || method.name.equals("ownsMissile"))) continue;
                checked=true;boolean uuidLookup=false;
                for(org.spongepowered.asm.lib.tree.AbstractInsnNode instruction:method.instructions.toArray()) {
                    if(!(instruction instanceof org.spongepowered.asm.lib.tree.MethodInsnNode)) continue;
                    String name=((org.spongepowered.asm.lib.tree.MethodInsnNode)instruction).name;
                    if(name.equals("getUniqueID") || name.equals("func_110124_au")) uuidLookup=true;
                }
                check(!uuidLookup,"compiled native binding never compares independent client/server UUIDs: "+method.name);
            }
            check(checked,"native ownership lookup covered: "+owner);
        }
    }
    private static void checkClientDistanceHandoff() {
        check(MissileViewRules.insideClientChunks(20,0,8,8),"near native missile fits client chunk view");
        check(!MissileViewRules.insideClientChunks(60,0,2,8),"two-chunk client hands off before server eight-chunk distance");
        check(!MissileViewRules.insideClientChunks(60,0,3,8),"three-chunk client hands off before projection far plane");
        check(MissileViewRules.insideClientChunks(60,0,8,8),"same missile remains native for eight-chunk client");
        check(!MissileViewRules.insideClientChunks(124,0,8,8),"eight-chunk client hands off before its chunk edge, not 256-block projection limit");
        check(MissileViewRules.insideClientChunks(0,0,2,8),"high-altitude missile in same chunk is governed separately by projection depth");
        check(!MissileViewRules.drawProxy(true,true,42,42),"native renderer completed this frame: no duplicate LOD body");
        check(MissileViewRules.drawProxy(true,true,41,42),"native culling before expected far plane: proxy fills missing body immediately");
        check(MissileViewRules.drawProxy(true,false,41,42),"untracked or out-of-view missile uses LOD");
        check(!MissileViewRules.drawProxy(false,false,41,42),"unavailable proxy never suppresses native fallback");
    }
    private static void checkVanillaFadeHandoff() {
        double start=MissileViewRules.fadeStart(60);
        check(start==114,"missile handoff uses DH vanilla-fade shader's exact start distance");
        check(MissileViewRules.beforeNativeFade(100,8,start,true),"complete nearby missile stays native before fade");
        check(!MissileViewRules.beforeNativeFade(107,8,start,true),"missile switches before any model fragment enters fade zone");
        check(!MissileViewRules.beforeNativeFade(130,8,start,true),"native draw completion inside fade does not suppress proxy");
        check(MissileViewRules.beforeNativeFade(130,8,start,false),"disabled vanilla fade leaves ordinary distance ownership intact");
        double lowStart=MissileViewRules.fadeStart(5);
        check(lowStart==31.5 && !MissileViewRules.beforeNativeFade(25,8,lowStart,true),"low client view distance uses its smaller fade transition");
        check(!MissileViewRules.beforeNativeFade(Double.NaN,8,start,true),"invalid fade distance cannot retain an invisible native model");
    }
    public static void main(String[] args)
    {
        for(String style:new String[]{"dhBody","dhBlast"}) {
            TrailSample visual=sample();visual.family=TrailSample.HBM;visual.style=style;visual.slot=style.equals("dhBody")?7:6;visual.count=1;
            ByteBuf wire=Unpooled.buffer();
            try {
                visual.write(wire);TrailSample decoded=TrailSample.read(wire);
                check(decoded.valid() && decoded.style.equals(style) && decoded.slot==visual.slot,"visual sample round trip: "+style);
                check(!wire.isReadable(),"visual sample consumes exact frame: "+style);
            } finally { wire.release(); }
        }
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
        for (int server=0;server<8;server++) {
            int allowed=EffectSupportPolicy.allowedFlags((server & 1)!=0,(server & 2)!=0,(server & 4)!=0);
            for (int requested=0;requested<16;requested++) {
                check(EffectSupportPolicy.permits(requested & allowed,TrailSample.HBM)==((server & 1)!=0 && (requested & 4)!=0),"NTM server policy intersection");
                check(EffectSupportPolicy.permits(requested & allowed,TrailSample.MCH)==((server & 2)!=0 && (requested & 2)!=0),"MCHELI server policy intersection");
                check(EffectSupportPolicy.permits(requested & allowed,TrailSample.FLANS)==((server & 4)!=0 && (requested & 8)!=0),"FMUR server policy intersection");
            }
        }
        for (int invalid:new int[]{Integer.MIN_VALUE,-1,0,4,32,Integer.MAX_VALUE})
            check(!EffectSupportPolicy.permits(-1,invalid),"unknown visual families fail closed");
        checks+=TerrainCoverageChecks.run();
        checks+=com.seibel.distanthorizons.forge17.actors.PlayerProtocolChecks.run();
        checks+=com.seibel.distanthorizons.forge17.actors.VehicleProtocolChecks.run();
        checks+=ActorLifecycleChecks.run();
        checks+=com.seibel.distanthorizons.forge17.actors.ModelProjectionChecks.run();
        checks+=com.seibel.distanthorizons.forge17.actors.TerrainCorridorChecks.run();
        checks+=com.seibel.distanthorizons.forge17.actors.PlayerTrackChecks.run();
        checkNativeEmitter();
        checkPrivateCameraScope();
        checks+=MissileCameraOriginChecks.run();
        checks+=ActorCameraOriginChecks.run();
        checks+=ActorNearViewChecks.run();
        checks+=com.seibel.distanthorizons.forge17.actors.PlayerBodyAlignmentChecks.run();
        checks+=com.seibel.distanthorizons.forge17.terrain.TerrainRayChecks.run();
        checks+=com.seibel.distanthorizons.forge17.terrain.RayBlockShapeChecks.run();
        checks+=com.seibel.distanthorizons.forge17.terrain.RaySnapshotChecks.run() + ShaderActorJitterChecks.run();
        checkShaderStages(args);
        checkProjectionCopy();
        checkProbeFailureIsolation();
        checkBodyTextureContract();
        checkFlightSmoothing();
        checkExhaustClockTransitions();
        checkTrailTimeline();
        checkNativeTrailSpacing();
        checkLargeBufferSorting();
        checkWaitingTrailOwner();
        checkEngineAnchoring();
        checkNativeNearOwnership();
        checkForgeSpawnIdentity();
        checkClientDistanceHandoff();
        checkVanillaFadeHandoff();
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
