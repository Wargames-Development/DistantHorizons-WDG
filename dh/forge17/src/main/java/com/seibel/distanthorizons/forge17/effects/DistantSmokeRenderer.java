package com.seibel.distanthorizons.forge17.effects;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteOrder;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import com.seibel.distanthorizons.common.render.openGl.glObject.GLState;
import com.seibel.distanthorizons.common.wrappers.misc.LightMapWrapper;
import static com.seibel.distanthorizons.lwjgl.LWJGLServiceProvider.LWJGL;
import com.seibel.distanthorizons.api.objects.math.DhApiMat4f;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiBeforeRenderCleanupEvent;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.*;
import com.seibel.distanthorizons.common.render.openGl.glObject.shader.GlShaderProgram;
import com.seibel.distanthorizons.common.wrappers.minecraft.MinecraftRenderWrapper;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.dependencyInjection.ModAccessorInjector;
import com.seibel.distanthorizons.core.render.DhApiRenderProxy;
import com.seibel.distanthorizons.core.util.math.DhVec3d;
import com.seibel.distanthorizons.core.wrapperInterfaces.modAccessor.IIrisAccessor;
import com.seibel.distanthorizons.coreapi.DependencyInjection.ApiEventInjector;
import com.seibel.distanthorizons.forge17.mixin.MixinTessellatorAccess;
import cpw.mods.fml.common.eventhandler.*;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.client.renderer.Tessellator;
import org.apache.logging.log4j.LogManager;
import org.lwjgl.opengl.*;

/** Replays bounded copies of real source geometry, without re-running renderers or particle simulation. */
public final class DistantSmokeRenderer
{
    public static DistantSmokeRenderer INSTANCE;
    private static int cameraParticlePass;

    /** Optional render-thread bridge: private cameras draw directly, never into the primary replay. */
    public static void renderCameraParticles(Runnable particles,boolean thermalMask) {
        if(cameraParticlePass!=0) throw new IllegalStateException("Recursive camera particles");
        cameraParticlePass=thermalMask?2:1;
        try { particles.run(); }
        finally { cameraParticlePass=0; }
    }
    static boolean isCameraParticlePass() { return cameraParticlePass!=0; }
    static boolean isCameraThermalMask() { return cameraParticlePass==2; }
    private final EffectQuadBuffer quads=new EffectQuadBuffer();
    private EffectQuadBuffer hbmNear;
    private int lateNearCaptured,lateNearDrawn,lateNearFallbacks;
    private final DhApiRenderParam frame=new DhApiRenderParam();
    private final DhApiMat4f nativeMatrix=new DhApiMat4f();
    private final DhApiMat4f shaderTerrainMatrix=new DhApiMat4f();
    private final EntityFX[] owners=new EntityFX[8];
    private final int[] scopeQuads=new int[8];
    private final EffectQuadBuffer plumeQuads=new EffectQuadBuffer(8192);
    private final EffectQuadBuffer missileQuads=new EffectQuadBuffer(32768);
    private int depth, observed, captured, rendered, batches, rejected, cullingHandoffs;
    private final int[] seen=new int[4];
    private boolean frameReady,failed,projectionKnown;
    private long renderFrame,fadeDistanceFrame=-1;
    private double vanillaFadeStart;
    private Field mchCamera;
    private boolean cameraChecked,alphaResolved;
    private Method alphaEnabled,alphaState,alphaReference;
    private double dhX,dhY,dhZ;
    private GlShaderProgram shader;
    private int vao,vbo;
    private final LightMapWrapper lightmap=new LightMapWrapper();
    private final ByteBuffer upload=org.lwjgl.BufferUtils.createByteBuffer(EffectQuadBuffer.CAPACITY*6*32);
    private static final int[] TRIANGLES={0,1,2,0,2,3};
    private int extendedMvp,nativeMvp,dhOffset,useLightmap,alphaCutoff,reverseDepth,nearPass,wholeModel,shaderPass,nativeDepthSampler;
    private final ShaderCloudOcclusion shaderClouds=new ShaderCloudOcclusion();
    private int cloudDepthSampler,cloudRange,cloudEnabled,nativeView;
    private int shaderFramebuffer;
    private boolean drawingShaderPass,shaderFailed,lastShaderActive;
    private long shaderDrawFrame=-1;
    private Method mchFlush;
    private boolean mchFlushResolved;
    private String shaderPipeline="not sampled";
    private final ShaderEffectsProbe shaderProbe=new ShaderEffectsProbe();
    private int diagnosticStage;
    private boolean probeFrame,probing;
    private final ByteBuffer colourMask=org.lwjgl.BufferUtils.createByteBuffer(4);
    private final IntBuffer shaderDrawBuffers=org.lwjgl.BufferUtils.createIntBuffer(2);
    private String status="waiting for frame";
    private float lastFar;
    private int nativeNearQuads;
    private boolean nativeStateRead;
    private String nativeState="not sampled";
    private Method fogStateGetter,fogEnabledGetter,fogEnabled,fogEnd,fogMode,projectionGetter,projection22,projection32;
    private boolean nativeStateResolved;
    private void readNativeState()
    {
        if(nativeStateRead) return;
        nativeStateRead=true;
        try {
            if(!nativeStateResolved) {
                nativeStateResolved=true;
                Class<?> state=Class.forName("com.gtnewhorizons.angelica.glsm.GLStateManager");
                fogStateGetter=state.getMethod("getFogState");fogEnabledGetter=state.getMethod("getFogMode");
                fogEnabled=fogEnabledGetter.getReturnType().getMethod("isEnabled");
                fogEnd=fogStateGetter.getReturnType().getMethod("getEnd");fogMode=fogStateGetter.getReturnType().getMethod("getFogMode");
                projectionGetter=state.getMethod("getProjectionMatrix");
                projection22=projectionGetter.getReturnType().getMethod("m22");projection32=projectionGetter.getReturnType().getMethod("m32");
            }
            if(fogStateGetter==null) return;
            Object fog=fogStateGetter.invoke(null),projection=projectionGetter.invoke(null);
            double a=((Number)projection22.invoke(projection)).doubleValue(),b=((Number)projection32.invoke(projection)).doubleValue();
            nativeState="fog="+fogEnabled.invoke(fogEnabledGetter.invoke(null))+" mode="+fogMode.invoke(fog)
                +" end="+Math.round(((Number)fogEnd.invoke(fog)).doubleValue())+"m drawFar="+Math.round(b/(a+1))+"m";
        } catch(ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
            nativeState="state unavailable: "+unavailable.getClass().getSimpleName();fogStateGetter=null;
        }
    }

    public DistantSmokeRenderer()
    {
        INSTANCE=this;
        ApiEventInjector.INSTANCE.bind(DhApiBeforeRenderCleanupEvent.class,new DhApiBeforeRenderCleanupEvent()
        {
            public void beforeCleanup(DhApiEventParam<DhApiRenderParam> event)
            {
                if(!EffectSources.enabled()) return;
                IIrisAccessor iris=ModAccessorInjector.INSTANCE.get(IIrisAccessor.class);
                if(iris!=null && iris.isRenderingShadowPass()) return;
                frame.update(event.value);
                nativeMatrix.set(frame.mcProjectionMatrix);nativeMatrix.multiply(frame.mcModelViewMatrix);projectionKnown=true;
                DhVec3d p=MinecraftRenderWrapper.INSTANCE.getCameraExactPosition();dhX=p.x;dhY=p.y;dhZ=p.z;
                frameReady=true;ShaderEffectsTarget.invalidate();
            }
        });
    }
    @SubscribeEvent
    public void startFrame(TickEvent.RenderTickEvent event)
    {
        if(event.phase!=TickEvent.Phase.START) return;
        if(hbmNear!=null) hbmNear.clear();lateNearCaptured=0;lateNearDrawn=0;lateNearFallbacks=0;
        IIrisAccessor activeIris=ModAccessorInjector.INSTANCE.get(IIrisAccessor.class);
        boolean activeShaders=activeIris!=null && activeIris.isShaderPackInUse();
        if(activeShaders!=lastShaderActive) { shaderFailed=false;lastShaderActive=activeShaders; }
        renderFrame++;ShaderEffectsTarget.invalidate();shaderClouds.clear();
        NtmTrace.frame();
        frameReady=false;quads.clear();missileQuads.clear();plumeQuads.clear();depth=0;observed=0;rejected=0;cullingHandoffs=0;nativeNearQuads=0;nativeStateRead=false;
        for(int i=0;i<seen.length;i++) seen[i]=0;
    }
    /** Reviewed visible effects bypass terrain-viewport culling; native depth still occludes near quads. */
    public static boolean needsExtendedDispatch(EntityFX particle)
    {
        DistantSmokeRenderer r=INSTANCE;
        if(isCameraParticlePass()) {
            double x=particle.posX-EntityFX.interpPosX,y=particle.posY-EntityFX.interpPosY,z=particle.posZ-EntityFX.interpPosZ;
            double range=Config.Client.Advanced.Graphics.GenericRendering.distantSmokeDistance.get();
            return !particle.isDead && EffectSources.enabled(EffectSources.family(particle))
                && Double.isFinite(x*x+y*y+z*z) && x*x+y*y+z*z<=range*range;
        }
        IIrisAccessor iris=ModAccessorInjector.INSTANCE.get(IIrisAccessor.class);
        if(iris!=null && iris.isRenderingShadowPass()) return false;
        if(r==null || !r.frameReady || r.failed || particle.isDead
            || !cpw.mods.fml.common.Loader.isModLoaded("angelica")
            || !EffectSources.enabled(EffectSources.family(particle))) return false;
        double x=particle.posX-EntityFX.interpPosX,y=particle.posY-EntityFX.interpPosY,z=particle.posZ-EntityFX.interpPosZ;
        double range=Config.Client.Advanced.Graphics.GenericRendering.distantSmokeDistance.get();
        if(!Double.isFinite(x*x+y*y+z*z) || x*x+y*y+z*z>range*range) return false;
        if(!EffectFrustum.withinExtendedView(r.nativeMatrix,x,y,z,64)) return false;
        r.cullingHandoffs++;
        return true;
    }
    public static boolean canRenderBodies() {
        DistantSmokeRenderer r=INSTANCE;
        if(isCameraParticlePass()) return r!=null && EffectSources.enabled(TrailSample.HBM);
        if(r==null || r.failed || !EffectSources.enabled(TrailSample.HBM) || DhApiRenderProxy.activeOpenGlDhDepthTextureId<=0) return false;
        IIrisAccessor iris=ModAccessorInjector.INSTANCE.get(IIrisAccessor.class);
        return iris==null || !iris.isShaderPackInUse() || (!r.shaderFailed && !iris.isRenderingShadowPass() && ShaderEffectsTarget.current()!=null);
    }
    static long frameId() { return INSTANCE==null?-1:INSTANCE.renderFrame; }
    static boolean nativeModelVisible(net.minecraft.entity.Entity entity,double radius) {
        DistantSmokeRenderer r=INSTANCE;
        Minecraft mc=Minecraft.getMinecraft();
        net.minecraft.entity.Entity camera=mc.renderViewEntity;
        if(camera!=null && !MissileViewRules.insideClientChunks(entity.posX-camera.posX,entity.posZ-camera.posZ,
            mc.gameSettings.renderDistanceChunks,radius)) return false;
        if(r==null || !r.projectionKnown) return true; // actual native draw observation still guards the handoff
        IIrisAccessor iris=ModAccessorInjector.INSTANCE.get(IIrisAccessor.class);
        boolean fading=(iris==null || !iris.isShaderPackInUse()) && Config.Client.Advanced.Graphics.Quality.vanillaFadeMode.get()
            !=com.seibel.distanthorizons.api.enums.config.EDhApiMcRenderingFadeMode.NONE;
        if(fading) {
            if(r.fadeDistanceFrame!=r.renderFrame) {
                // Match GlDhVanillaFadeShader, including client RD, aspect ratio,
                // overdraw settings, altitude and fast-camera adjustments.
                r.vanillaFadeStart=MissileViewRules.fadeStart(com.seibel.distanthorizons.core.util.RenderUtil.getNearClipPlaneInBlocks());
                r.fadeDistanceFrame=r.renderFrame;
            }
            double dx=entity.posX-r.dhX,dy=entity.posY-r.dhY,dz=entity.posZ-r.dhZ;
            if(!MissileViewRules.beforeNativeFade(Math.sqrt(dx*dx+dy*dy+dz*dz),radius,r.vanillaFadeStart,true)) return false;
        }
        double x=entity.posX-EntityFX.interpPosX,y=entity.posY-EntityFX.interpPosY,z=entity.posZ-EntityFX.interpPosZ;
        double zc=r.nativeMatrix.m20*x+r.nativeMatrix.m21*y+r.nativeMatrix.m22*z+r.nativeMatrix.m23;
        double wc=r.nativeMatrix.m30*x+r.nativeMatrix.m31*y+r.nativeMatrix.m32*z+r.nativeMatrix.m33;
        double a=r.nativeMatrix.m30-r.nativeMatrix.m20,b=r.nativeMatrix.m31-r.nativeMatrix.m21,c=r.nativeMatrix.m32-r.nativeMatrix.m22;
        return MissileViewRules.nativeOwns(wc,zc,a,b,c,radius);
    }
    /** Loaded entities can remain tracked beyond the native projection's far plane. */
    public static boolean insideNativeProjection(double x,double y,double z) {
        DistantSmokeRenderer r=INSTANCE;
        if(r==null || !r.frameReady) return false;
        x-=EntityFX.interpPosX;y-=EntityFX.interpPosY;z-=EntityFX.interpPosZ;
        double clipZ=r.nativeMatrix.m20*x+r.nativeMatrix.m21*y+r.nativeMatrix.m22*z+r.nativeMatrix.m23;
        double clipW=r.nativeMatrix.m30*x+r.nativeMatrix.m31*y+r.nativeMatrix.m32*z+r.nativeMatrix.m33;
        return clipW>0 && clipZ<=clipW;
    }
    /** Final dispatch target installed after mixin injection, so culling redirects cannot orphan it. */
    public static void renderParticle(EntityFX particle,Tessellator tess,float partial,
        float a,float b,float c,float d,float e)
    {
        boolean capture=begin(particle);
        int start=capture?((MixinTessellatorAccess)tess).dh$getRawBufferIndex():0;
        try { particle.renderParticle(tess,partial,a,b,c,d,e); }
        finally { if(capture) finish(tess,start); }
    }
    public static boolean begin(EntityFX particle)
    {
        if(isCameraParticlePass()) return false;
        DistantSmokeRenderer r=INSTANCE;
        if(r==null || !EffectSources.enabled() || r.failed || r.depth>=r.owners.length) return false;
        // Overlapping compatibility wrappers must not capture a source quad twice.
        if(r.depth>0 && r.owners[r.depth-1]==particle) return false;
        int family=EffectSources.family(particle);
        IIrisAccessor iris=ModAccessorInjector.INSTANCE.get(IIrisAccessor.class);
        if(iris!=null && iris.isRenderingShadowPass()) return false;
        if(!EffectSources.enabled(family)) return false;
        r.readNativeState();
        NtmTrace.dispatched(particle);
        r.observed++;r.seen[family]++;
        r.scopeQuads[r.depth]=0;r.owners[r.depth++]=particle;return true;
    }
    public static void finish(Tessellator tess,int start)
    {
        DistantSmokeRenderer r=INSTANCE;
        if(r==null || r.depth==0) return;
        EntityFX owner=r.owners[r.depth-1];
        try
        {
            // Layer 3 submits its own draws; other layers append into vanilla's shared batch.
            if(owner.getFXLayer()!=3) r.safeCapture(tess,start);
        }
        finally { r.owners[--r.depth]=null; }
    }
    public static void captureDraw(Tessellator tess)
    {
        DistantSmokeRenderer r=INSTANCE;
        if(r!=null && r.depth>0 && r.owners[r.depth-1].getFXLayer()==3) r.safeCapture(tess,0);
    }
    private void safeCapture(Tessellator tess,int start)
    {
        try { capture(tess,start); }
        catch(RuntimeException | LinkageError problem)
        {
            failed=true;
            LogManager.getLogger("DistantHorizons").warn("Distant effect capture disabled; original particles unchanged",problem);
        }
    }
    private void capture(Tessellator tess,int start)
    {
        if(!frameReady || failed) return;
        EntityFX owner=owners[depth-1];
        if(owner.isDead || owner.worldObj!=Minecraft.getMinecraft().theWorld) return;
        MixinTessellatorAccess data=(MixinTessellatorAccess)tess;
        int end=data.dh$getRawBufferIndex();
        if(end>data.dh$getRawBuffer().length) return;
        if(data.dh$getDrawMode()!=GL11.GL_QUADS || !data.dh$hasTexture() || !data.dh$hasColor()
            || start<0 || end<start || (end-start)%32!=0) return;
        double max=Config.Client.Advanced.Graphics.GenericRendering.distantSmokeDistance.get();
        double x=owner.posX-EntityFX.interpPosX,y=owner.posY-EntityFX.interpPosY,z=owner.posZ-EntityFX.interpPosZ;
        double distance=x*x+y*y+z*z;
        if(!Double.isFinite(distance) || distance>max*max) return;
        int family=EffectSources.family(owner);
        if(cpw.mods.fml.common.Loader.isModLoaded("mcheli") && !nonThermalMchCamera(owner)) return;
        IIrisAccessor iris=ModAccessorInjector.INSTANCE.get(IIrisAccessor.class);
        if((iris==null || !iris.isShaderPackInUse()) && GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)!=Minecraft.getMinecraft().getFramebuffer().framebufferObject) return;
        boolean shaderScene=iris!=null && iris.isShaderPackInUse();
        ShaderEffectsTarget shaderTarget=shaderScene && !shaderFailed?ShaderEffectsTarget.current():null;
        // Do not remove native vertices unless the replacement pass is available.
        if(shaderScene && shaderTarget==null) return;
        boolean shaderRelay=shaderScene && EffectSources.isRelayed(owner);
        if(shaderScene) shaderClouds.observe(shaderTarget);
        boolean nativePlume=owner.getClass().getName().equals("com.hbm.particle.ParticleRocketFlame");
        EffectQuadBuffer quads=owner instanceof MissileVisual?missileQuads:nativePlume?plumeQuads:this.quads;
        if((owner instanceof MissileVisual || nativePlume) && quads.size+(end-start)/32>quads.capacity) return;
        int[] raw=data.dh$getRawBuffer();
        NtmTrace.geometry(owner,raw,start,end,nativeMatrix);
        int limit=owner instanceof MissileVisual?missileQuads.capacity:nativePlume?plumeQuads.capacity:Math.min(EffectQuadBuffer.CAPACITY,Config.Client.Advanced.Graphics.GenericRendering.distantSmokeParticleBudget.get());
        int texture=-1,src=0,dst=0,srcAlpha=0,dstAlpha=0;
        float cutoff=0;
        double ox=EntityFX.interpPosX,oy=EntityFX.interpPosY,oz=EntityFX.interpPosZ;
        String type=owner.getClass().getSimpleName();
        if(type.equals("ParticleContrail") || type.equals("ParticleSmokePlume"))
        {
            net.minecraft.entity.player.EntityPlayer p=Minecraft.getMinecraft().thePlayer;
            if(p==null) return;
            ox=p.lastTickPosX+(p.posX-p.lastTickPosX)*frame.partialTicks;
            oy=p.lastTickPosY+(p.posY-p.lastTickPosY)*frame.partialTicks;
            oz=p.lastTickPosZ+(p.posZ-p.lastTickPosZ)*frame.partialTicks;
        }
        for(int v=start;v+31<end;v+=32)
        {
            boolean far=false;
            for(int corner=0;corner<4;corner++)
            {
                int i=v+corner*8;
                float px=Float.intBitsToFloat(raw[i]),py=Float.intBitsToFloat(raw[i+1]),pz=Float.intBitsToFloat(raw[i+2]);
                double clipZ=nativeMatrix.m20*px+nativeMatrix.m21*py+nativeMatrix.m22*pz+nativeMatrix.m23;
                double clipW=nativeMatrix.m30*px+nativeMatrix.m31*py+nativeMatrix.m32*pz+nativeMatrix.m33;
                far|=clipW>0 && clipZ>clipW;
            }
            if(!far && !(owner instanceof MissileVisual) && !shaderRelay) { nativeNearQuads++;continue; }
            if(scopeQuads[depth-1]>=(owner instanceof MissileVisual?4096:owner.getClass().getSimpleName().equals("ParticleRocketFlame")?10:2)) break;
            int ordinal=scopeQuads[depth-1]++;
            int rank=owner.getEntityId()*0x9e3779b9+ordinal*0x7f4a7c15;
            rank^=rank>>>16;rank&=0x7fffffff;
            int q=quads.admit(raw,v,limit,rank);
            if(q<0) { rejected++;continue; }
            if(texture<0)
            {
                texture=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
                src=LWJGL.glGetInteger(GL14.GL_BLEND_SRC_RGB);dst=LWJGL.glGetInteger(GL14.GL_BLEND_DST_RGB);
                srcAlpha=GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);dstAlpha=GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
                cutoff=sourceAlphaCutoff();
            }
            quads.alphaSourceBlend[q]=srcAlpha;quads.alphaDestinationBlend[q]=dstAlpha;
            quads.texture[q]=texture;quads.sourceBlend[q]=src;quads.destinationBlend[q]=dst;
            quads.traceBin[q]=NtmTrace.quadBin(raw,v,nativeMatrix);quads.traceOrigin[q]=NtmTrace.origin(owner);
            quads.lit[q]=!(owner instanceof MissileVisual) && data.dh$hasBrightness();quads.alphaCutoff[q]=cutoff;quads.family[q]=family;
            quads.ox[q]=(float)(ox-dhX);quads.oy[q]=(float)(oy-dhY);quads.oz[q]=(float)(oz-dhZ);
            quads.distance[q]=distance;quads.fade[q]=(float)Math.min(1,Math.max(0,(max-Math.sqrt(distance))/32));
            if(shaderRelay) for(int corner=0;corner<4;corner++) raw[v+corner*8+5]&=java.nio.ByteOrder.nativeOrder()==java.nio.ByteOrder.LITTLE_ENDIAN?0x00ffffff:0xffffff00;
        }
        relocateHbmNear(owner,data,raw,start,end,distance,ox,oy,oz);
    }
    /** ParticleRocketFlame only appends ten atlas quads. Move its near fragments after DH's
     * late fade instead of letting the fade erase them. Every other source keeps its path. */
    private void relocateHbmNear(EntityFX owner,MixinTessellatorAccess data,int[] raw,int start,int end,
        double distance,double ox,double oy,double oz)
    {
        IIrisAccessor iris=ModAccessorInjector.INSTANCE.get(IIrisAccessor.class);
        if(iris!=null && iris.isShaderPackInUse()) return; // shader pack owns native particles and their compositing
        if(!owner.getClass().getName().equals("com.hbm.particle.ParticleRocketFlame")
            || Config.Client.Advanced.Graphics.Quality.vanillaFadeMode.get()
                ==com.seibel.distanthorizons.api.enums.config.EDhApiMcRenderingFadeMode.NONE
            || DhApiRenderProxy.activeOpenGlDhDepthTextureId<=0) return;
        int needed=0;
        for(int v=start;v+31<end;v+=32) if(hasNativeCorner(raw,v)) needed++;
        if(needed==0) return;
        int used=hbmNear==null?0:hbmNear.size;
        if(!HbmDrawRules.canRelocate(owner.getClass().getName(),owner.getFXLayer(),start,end,
            data.dh$getVertexCount(),data.dh$getAddedVertices(),used,needed)) { lateNearFallbacks++;return; }
        if(hbmNear==null) hbmNear=new EffectQuadBuffer(HbmDrawRules.NEAR_CAPACITY);
        int texture=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int src=LWJGL.glGetInteger(GL14.GL_BLEND_SRC_RGB),dst=LWJGL.glGetInteger(GL14.GL_BLEND_DST_RGB);
        int srcAlpha=GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA),dstAlpha=GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        float cutoff=sourceAlphaCutoff();
        for(int v=start;v+31<end;v+=32) if(hasNativeCorner(raw,v)) {
            int q=hbmNear.admit(raw,v,HbmDrawRules.NEAR_CAPACITY,0);
            hbmNear.texture[q]=texture;hbmNear.sourceBlend[q]=src;hbmNear.destinationBlend[q]=dst;
            hbmNear.alphaSourceBlend[q]=srcAlpha;hbmNear.alphaDestinationBlend[q]=dstAlpha;
            hbmNear.lit[q]=data.dh$hasBrightness();hbmNear.alphaCutoff[q]=cutoff;hbmNear.family[q]=TrailSample.HBM;
            hbmNear.fade[q]=1;hbmNear.distance[q]=distance;
            hbmNear.ox[q]=(float)(ox-dhX);hbmNear.oy[q]=(float)(oy-dhY);hbmNear.oz[q]=(float)(oz-dhZ);
        }
        // All required near copies are secured before changing the native batch. Far-only
        // pieces were clipped there anyway and keep the existing bounded far replay.
        data.dh$setRawBufferIndex(start);
        data.dh$setVertexCount(data.dh$getVertexCount()-(end-start)/8);
        data.dh$setAddedVertices(data.dh$getAddedVertices()-(end-start)/8);
    }
    private boolean hasNativeCorner(int[] raw,int v)
    {
        for(int c=0;c<4;c++) {
            int i=v+c*8;double x=Float.intBitsToFloat(raw[i]),y=Float.intBitsToFloat(raw[i+1]),z=Float.intBitsToFloat(raw[i+2]);
            double w=nativeMatrix.m30*x+nativeMatrix.m31*y+nativeMatrix.m32*z+nativeMatrix.m33;
            if(w>0 && nativeMatrix.m20*x+nativeMatrix.m21*y+nativeMatrix.m22*z+nativeMatrix.m23<=w) return true;
        }
        return false;
    }
    private float sourceAlphaCutoff()
    {
        if(cpw.mods.fml.common.Loader.isModLoaded("angelica"))
        {
            try
            {
                if(!alphaResolved)
                {
                    alphaResolved=true;
                    Class<?> state=Class.forName("com.gtnewhorizons.angelica.glsm.GLStateManager");
                    alphaEnabled=state.getMethod("isEffectiveAlphaTestEnabled");alphaState=state.getMethod("getAlphaState");
                    alphaReference=alphaState.getReturnType().getMethod("getReference");
                }
                return alphaEnabled!=null && (Boolean)alphaEnabled.invoke(null)?(Float)alphaReference.invoke(alphaState.invoke(null)):0;
            }
            catch(ReflectiveOperationException | RuntimeException | LinkageError unsupported)
            { alphaEnabled=null;alphaState=null;alphaReference=null;return 0; }
        }
        // Fixed-function alpha state does not exist in a Core context.
        if((LWJGL.glGetInteger(GL32.GL_CONTEXT_PROFILE_MASK)&GL32.GL_CONTEXT_CORE_PROFILE_BIT)!=0) return 0;
        return GL11.glIsEnabled(GL11.GL_ALPHA_TEST)?GL11.glGetFloat(GL11.GL_ALPHA_TEST_REF):0;
    }
    private boolean nonThermalMchCamera(EntityFX owner)
    {
        try
        {
            if(!cameraChecked)
            {
                cameraChecked=true;
                mchCamera=Class.forName("mcheli.MCH_Camera",false,owner.getClass().getClassLoader()).getField("currentCameraMode");
            }
            // Night vision processes the completed scene and still needs its distant effects.
            int mode=mchCamera==null?-1:mchCamera.getInt(null);
            return mode==0 || mode==1;
        }
        catch(ReflectiveOperationException unsupported) { return false; }
    }
    public static void afterWorldLast(float partialTicks)
    {
        DistantSmokeRenderer r=INSTANCE;
        if(r==null) return;
        IIrisAccessor iris=ModAccessorInjector.INSTANCE.get(IIrisAccessor.class);
        // Angelica has already composited and finalized here. Never borrow this FBO.
        if(iris!=null && iris.isShaderPackInUse()) return;
        try { r.render(partialTicks); }
        finally { r.quads.clear();if(r.hbmNear!=null) r.hbmNear.clear(); }
    }
    /** Called immediately before Angelica's actual finalizeLevelRendering invocation. */
    public static void beforeShaderComposite() {
        DistantSmokeRenderer r=INSTANCE;
        if(r==null || r.shaderDrawFrame==r.renderFrame) return;
        IIrisAccessor iris=ModAccessorInjector.INSTANCE.get(IIrisAccessor.class);
        if(iris==null || !iris.isShaderPackInUse() || iris.isRenderingShadowPass()) return;
        r.shaderDrawFrame=r.renderFrame;
        ShaderEffectsTarget.invalidate();
        // WDG MCHeli normally flushes this queue at Forge world-last, after shader
        // finalization. Its native method clears the queue; that event then no-ops.
        if(EffectSources.enabled(TrailSample.MCH) && cpw.mods.fml.common.Loader.isModLoaded("mcheli")) {
            try {
                if(!r.mchFlushResolved) {
                    r.mchFlushResolved=true;
                    r.mchFlush=Class.forName("mcheli.particles.MCH_EntityParticleSmoke").getMethod("renderQueued",float.class);
                }
                if(r.mchFlush!=null) r.mchFlush.invoke(null,r.frame.partialTicks);
            } catch(ReflectiveOperationException | RuntimeException | LinkageError error) {
                LogManager.getLogger("DistantHorizons").warn("Cannot flush optional MCHeli smoke before shader composites",error);
                r.mchFlush=null;
            }
        }
        try { r.render(r.frame.partialTicks); }
        finally { r.quads.clear();r.missileQuads.clear();r.plumeQuads.clear(); }
    }
    private void render(float partialTicks)
    {
        shaderProbe.poll();
        captured=quads.size+missileQuads.size+plumeQuads.size;rendered=0;batches=0;lateNearCaptured=hbmNear==null?0:hbmNear.size;lateNearDrawn=0;
        traceSelected(quads);traceSelected(missileQuads);traceSelected(plumeQuads);
        if(!EffectSources.enabled()) { status="disabled";return; }
        if(failed) { status="disabled after render error (see log)";return; }
        if(!frameReady) { status="no DH terrain frame";return; }
        lastFar=(float)(frame.mcProjectionMatrix.m23/(frame.mcProjectionMatrix.m22+1.0D));
        IIrisAccessor iris=ModAccessorInjector.INSTANCE.get(IIrisAccessor.class);
        if(iris!=null && iris.isRenderingShadowPass()) { status="shadow pass skipped";return; }
        if(iris!=null && iris.isShaderPackInUse() && shaderFailed) { status="shader effect target disabled after render failure";return; }
        ShaderEffectsTarget shaderTarget=iris!=null && iris.isShaderPackInUse()?ShaderEffectsTarget.current():null;
        if(iris!=null && iris.isShaderPackInUse() && shaderTarget==null) { status="shader DH target unavailable";return; }
        Minecraft mc=Minecraft.getMinecraft();
        if(shaderTarget==null && GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)!=mc.getFramebuffer().framebufferObject)
        { status="non-main framebuffer";return; }
        if(DhApiRenderProxy.activeOpenGlDhDepthTextureId<=0) { status="no DH depth texture";return; }
        if(quads.size==0 && missileQuads.size==0 && plumeQuads.size==0 && lateNearCaptured==0) { status="ready; no captured geometry";return; }
        try { draw(mc,partialTicks,shaderTarget);status=shaderTarget==null?"active":"active in shader scene before composites"; }
        catch(RuntimeException | LinkageError error)
        {
            if(shaderTarget!=null) shaderFailed=true;else failed=true;
            status="render error";
            LogManager.getLogger("DistantHorizons").warn(shaderTarget!=null
                ?"Shader distant effects disabled until shaders are toggled; shaderless and native effects retained"
                :"Distant effects disabled for this session; original effects unchanged",error);
        }
    }
    private static void traceSelected(EffectQuadBuffer buffer) {
        for(int i=0;i<buffer.size;i++) if(buffer.family[i]==TrailSample.HBM) NtmTrace.selected(buffer.traceBin[i],buffer.traceOrigin[i]);
    }
    private void draw(Minecraft mc,float partial,ShaderEffectsTarget target)
    {
        boolean materialTarget=target!=null && target.particleMaterial>0;
        boolean materialBlend=materialTarget && GL30.glIsEnabledi(GL11.GL_BLEND,1);
        int readFramebuffer=LWJGL.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int activeTexture=LWJGL.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        LWJGL.glActiveTexture(GL13.GL_TEXTURE4);int oldTexture4=LWJGL.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);LWJGL.glActiveTexture(activeTexture);
        if(target!=null) shaderClouds.refreshAtDraw(target);
        try(GLState saved=new GLState())
        {
            drawingShaderPass=target!=null;
            if(target!=null) bindShaderTarget(target);
            if(shader==null)
            {
                shader=new GlShaderProgram("assets/distanthorizons/shaders/distant_smoke.vert","assets/distanthorizons/shaders/distant_smoke.frag",
                    new String[]{"aPosition","aUv","aColor","aLight"},new String[]{"fragColor","fragParticleMaterial"});
                nearPass=shader.getUniformLocation("uNearPass");
                wholeModel=shader.getUniformLocation("uWholeModel");
                diagnosticStage=shader.getUniformLocation("uDiagnosticStage");
                cloudDepthSampler=shader.getUniformLocation("uCloudDepth");cloudRange=shader.getUniformLocation("uCloudRenderDistance");
                cloudEnabled=shader.getUniformLocation("uUseCloudDepth");nativeView=shader.getUniformLocation("uNativeView");
                shaderPass=shader.getUniformLocation("uShaderPass");nativeDepthSampler=shader.getUniformLocation("uNativeDepth");
                extendedMvp=shader.getUniformLocation("uExtendedMvp");nativeMvp=shader.getUniformLocation("uNativeMvp");
                dhOffset=shader.getUniformLocation("uDhOriginOffset");useLightmap=shader.getUniformLocation("uUseLightmap");
                alphaCutoff=shader.getUniformLocation("uAlphaCutoff");reverseDepth=shader.getUniformLocation("uDhReverseDepth");
                shader.setUniform(shader.getUniformLocation("uSmoke"),0);shader.setUniform(shader.getUniformLocation("uLightmap"),1);
                shader.setUniform(shader.getUniformLocation("uDhDepth"),2);
                vao=LWJGL.glGenVertexArrays();vbo=LWJGL.glGenBuffers();
                LWJGL.glBindVertexArray(vao);LWJGL.glBindBuffer(GL15.GL_ARRAY_BUFFER,vbo);
                LWJGL.glVertexAttribPointer(0,3,GL11.GL_FLOAT,false,32,0);
                LWJGL.glVertexAttribPointer(1,2,GL11.GL_FLOAT,false,32,12);
                LWJGL.glVertexAttribPointer(2,4,GL11.GL_UNSIGNED_BYTE,true,32,20);
                LWJGL.glVertexAttribPointer(3,2,GL11.GL_SHORT,false,32,28);
                for(int i=0;i<4;i++) LWJGL.glEnableVertexAttribArray(i);
            }
            shader.bind();shader.setUniform(diagnosticStage,0);shader.setUniform(nativeView,frame.mcModelViewMatrix);
            shader.setUniform(cloudDepthSampler,4);shader.setUniform(cloudRange,shaderClouds.distance);
            shader.setUniform(cloudEnabled,target!=null && shaderClouds.texture>0);
            if(target!=null && shaderClouds.texture>0) { LWJGL.glActiveTexture(GL13.GL_TEXTURE4);LWJGL.glBindTexture(GL11.GL_TEXTURE_2D,shaderClouds.texture); }
            shader.setUniform(shaderPass,target!=null);shader.setUniform(nativeDepthSampler,3);
            if(target!=null) {
                // The actual pack terrain projection owns both sampled depth
                // textures and the live depth attachment. Keep DH's existing
                // camera-origin conversion/model view, changing only projection.
                shaderTerrainMatrix.set(target.terrainProjection);shaderTerrainMatrix.multiply(frame.dhModelViewMatrix);
            }
            shader.setUniform(extendedMvp,target==null?frame.dhMvmProjMatrix:shaderTerrainMatrix);shader.setUniform(nativeMvp,nativeMatrix);
            shader.setUniform(reverseDepth,com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector.INSTANCE
                .get(com.seibel.distanthorizons.core.wrapperInterfaces.render.AbstractDhRenderApiDefinition.class).getDepthDirection()
                ==com.seibel.distanthorizons.api.enums.config.EDhApiDepthDirection.REVERSE_Z);
            LWJGL.glActiveTexture(GL13.GL_TEXTURE1);LWJGL.glBindTexture(GL11.GL_TEXTURE_2D,lightmap.getOpenGlId());
            LWJGL.glActiveTexture(GL13.GL_TEXTURE2);LWJGL.glBindTexture(GL11.GL_TEXTURE_2D,target==null?DhApiRenderProxy.activeOpenGlDhDepthTextureId:target.terrainDepth);
            if(target!=null) { LWJGL.glActiveTexture(GL13.GL_TEXTURE3);LWJGL.glBindTexture(GL11.GL_TEXTURE_2D,target.nativeSceneDepth); }
            LWJGL.glActiveTexture(GL13.GL_TEXTURE0);
            LWJGL.glEnable(GL11.GL_BLEND);LWJGL.glEnable(GL11.GL_DEPTH_TEST);LWJGL.glDepthFunc(target!=null && com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector.INSTANCE
                .get(com.seibel.distanthorizons.core.wrapperInterfaces.render.AbstractDhRenderApiDefinition.class).getDepthDirection()
                ==com.seibel.distanthorizons.api.enums.config.EDhApiDepthDirection.REVERSE_Z?GL11.GL_GEQUAL:GL11.GL_LEQUAL);
            // Metadata must replace, including for additive explosion waves.
            // Scene-colour blending retains each source renderer's original state.
            if(materialTarget) GL30.glDisablei(GL11.GL_BLEND,1);
            LWJGL.glDepthMask(false);LWJGL.glDisable(GL11.GL_CULL_FACE);
            probeFrame=target!=null && (missileQuads.size>0 || plumeQuads.size>0 || quads.size>0)
                && shaderProbe.start(mc.theWorld,shaderPipeline);
            LWJGL.glEnable(GL11.GL_CULL_FACE);LWJGL.glCullFace(GL11.GL_BACK);
            missileQuads.sortBackToFront();drawWithProbe(missileQuads,0);
            LWJGL.glDisable(GL11.GL_CULL_FACE);
            plumeQuads.sortBackToFront();drawWithProbe(plumeQuads,1);
            quads.sortBackToFront();
            drawWithProbe(quads,2);
            if(hbmNear!=null) drawBuffer(hbmNear,true); // Preserve the original native append order.
        } finally {
            if(materialTarget) { if(materialBlend) GL30.glEnablei(GL11.GL_BLEND,1);else GL30.glDisablei(GL11.GL_BLEND,1); }
            drawingShaderPass=false;LWJGL.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,readFramebuffer);
            LWJGL.glActiveTexture(GL13.GL_TEXTURE4);LWJGL.glBindTexture(GL11.GL_TEXTURE_2D,oldTexture4);LWJGL.glActiveTexture(activeTexture);
        }
    }
    private void drawWithProbe(EffectQuadBuffer buffer,int kind) {
        if(!probeFrame || buffer.size==0) { drawBuffer(buffer,false);return; }
        shaderProbe.begin(kind,3);
        try { drawBuffer(buffer,false); } finally { shaderProbe.end(); }
        colourMask.clear();GL11.glGetBoolean(GL11.GL_COLOR_WRITEMASK,colourMask);
        probing=true;
        try {
            GL11.glColorMask(false,false,false,false);
            for(int stage=1;stage<=3;stage++) {
                shader.setUniform(diagnosticStage,stage);
                if(stage==1) LWJGL.glDisable(GL11.GL_DEPTH_TEST);else LWJGL.glEnable(GL11.GL_DEPTH_TEST);
                shaderProbe.begin(kind,stage-1);
                try { drawBuffer(buffer,false); } finally { shaderProbe.end(); }
            }
        } finally {
            probing=false;shader.setUniform(diagnosticStage,0);LWJGL.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glColorMask(colourMask.get(0)!=0,colourMask.get(1)!=0,colourMask.get(2)!=0,colourMask.get(3)!=0);
        }
    }
    /** Use the colortex0 input of the upcoming composites, independently of the
     * last hand/weather program's output layout and this frame's parity. */
    private void bindShaderTarget(ShaderEffectsTarget target) {
        int source=LWJGL.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int oldColour=source==0?0:LWJGL.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
        shaderPipeline="pre-composite frame="+renderFrame+" sourceFbo="+source+" sourceColour="+oldColour
            +" scene="+target.sceneColour+" alt="+target.colourFlipped+" depth/live/copy/native="+target.sceneDepth+"/"+target.terrainDepth+"/"+target.nativeSceneDepth
            +" cloud="+shaderClouds.texture+" resolved="+target.cloudDepth+" alt="+target.cloudFlipped+" range="+shaderClouds.distance
            +" particleMaterial="+target.particleMaterial
            +" projectionNear/core="+target.terrainProjection.m23/(target.terrainProjection.m22-1)+"/"+frame.dhProjectionMatrix.m23/(frame.dhProjectionMatrix.m22-1)
            +" quads/body/plume="+quads.size+"/"+missileQuads.size+"/"+plumeQuads.size;
        if(shaderFramebuffer==0 || !GL30.glIsFramebuffer(shaderFramebuffer)) shaderFramebuffer=LWJGL.glGenFramebuffers();
        LWJGL.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,shaderFramebuffer);
        LWJGL.glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,target.sceneColour,0);
        // BountifulStars' native particles write mask 254 into colortex6, opting
        // out of terrain-history TAA. Only that pack family gets this attachment.
        LWJGL.glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT1,GL11.GL_TEXTURE_2D,target.particleMaterial,0);
        LWJGL.glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER,GL30.GL_DEPTH_ATTACHMENT,GL11.GL_TEXTURE_2D,target.sceneDepth,0);
        shaderDrawBuffers.clear();shaderDrawBuffers.put(GL30.GL_COLOR_ATTACHMENT0);
        if(target.particleMaterial>0) shaderDrawBuffers.put(GL30.GL_COLOR_ATTACHMENT1);
        shaderDrawBuffers.flip();GL20.glDrawBuffers(shaderDrawBuffers);
        if(LWJGL.glCheckFramebufferStatus(GL30.GL_DRAW_FRAMEBUFFER)!=GL30.GL_FRAMEBUFFER_COMPLETE)
            throw new IllegalStateException("Shader effect framebuffer is incomplete");
    }
    private void drawBuffer(EffectQuadBuffer buffer,boolean near)
    {
        shader.setUniform(nearPass,near);
        shader.setUniform(wholeModel,buffer==missileQuads);
        LWJGL.glDepthMask(!probing && drawingShaderPass && buffer==missileQuads);
        for(int chunk=0;chunk<buffer.size;chunk+=EffectQuadBuffer.CAPACITY) {
            int end=Math.min(buffer.size,chunk+EffectQuadBuffer.CAPACITY);
            upload.clear();IntBuffer words=upload.asIntBuffer();
            for(int i=chunk;i<end;i++) append(words,buffer,buffer.order[i]);
            upload.limit(words.position()*4);
            LWJGL.glBindVertexArray(vao);LWJGL.glBindBuffer(GL15.GL_ARRAY_BUFFER,vbo);
            LWJGL.glBufferData(GL15.GL_ARRAY_BUFFER,upload,GL15.GL_STREAM_DRAW);
            for(int i=chunk;i<end;) {
                int first=i,q=buffer.order[i];
                LWJGL.glBindTexture(GL11.GL_TEXTURE_2D,buffer.texture[q]);
                LWJGL.glBlendFuncSeparate(buffer.sourceBlend[q],buffer.destinationBlend[q],buffer.alphaSourceBlend[q],buffer.alphaDestinationBlend[q]);
                shader.setUniform(useLightmap,buffer.lit[q]);shader.setUniform(alphaCutoff,buffer.alphaCutoff[q]);
                LWJGL.glUniform3f(dhOffset,buffer.ox[q],buffer.oy[q],buffer.oz[q]);
                do { i++;if(!probing) { if(near) lateNearDrawn++;else rendered++; } } while(i<end && buffer.sameState(q,buffer.order[i]));
                LWJGL.glDrawArrays(GL11.GL_TRIANGLES,(first-chunk)*6,(i-first)*6);if(!probing) batches++;
            }
        }
    }
    private void append(IntBuffer words,EffectQuadBuffer buffer,int q)
    {
        for(int c:TRIANGLES)
        {
            int p=q*32+c*8,color=buffer.vertices[p+5];
            if(ByteOrder.nativeOrder()==ByteOrder.BIG_ENDIAN) color=Integer.reverseBytes(color);
            float fade=buffer.fade[q],rgb=buffer.sourceBlend[q]==GL11.GL_ONE?fade:1;
            int r=Math.round((color&255)*rgb),g=Math.round(((color>>>8)&255)*rgb),b=Math.round(((color>>>16)&255)*rgb),a=Math.round(((color>>>24)&255)*fade);
            color=r|g<<8|b<<16|a<<24;
            if(ByteOrder.nativeOrder()==ByteOrder.BIG_ENDIAN) color=Integer.reverseBytes(color);
            words.put(buffer.vertices,p,5);words.put(color);words.put(buffer.vertices[p+6]);words.put(buffer.vertices[p+7]);
        }
    }
    /** Camera-depth coordinate used consistently by bounded pipeline diagnostics. */
    public static double cameraDepth(double x,double y,double z)
    {
        DistantSmokeRenderer r=INSTANCE;
        if(r==null) return Double.NaN;
        x-=EntityFX.interpPosX;y-=EntityFX.interpPosY;z-=EntityFX.interpPosZ;
        return r.nativeMatrix.m30*x+r.nativeMatrix.m31*y+r.nativeMatrix.m32*z+r.nativeMatrix.m33;
    }
    public String diagnostic()
    {
        return "DH effects: "+status+"; native far="+Math.round(lastFar)+"m; native near quads="+nativeNearQuads+"; "+nativeState+"; observed M/H/F="+seen[1]+"/"+seen[2]+"/"+seen[3]
            +"; NTM post-fade near="+lateNearCaptured+"/"+lateNearDrawn+" fallback="+lateNearFallbacks+"; culling handoffs="+cullingHandoffs+"; captured/drawn="+captured+"/"+rendered+" quads in "+batches+" batches; budget drops="+rejected
            +(lastShaderActive?"; shader pipeline: "+shaderPipeline+"; last probe: "+shaderProbe.diagnostic():"");
    }
}
