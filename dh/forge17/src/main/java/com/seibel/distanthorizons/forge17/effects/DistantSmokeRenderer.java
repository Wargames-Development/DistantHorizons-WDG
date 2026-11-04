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
    private final EffectQuadBuffer quads=new EffectQuadBuffer();
    private EffectQuadBuffer hbmNear;
    private int lateNearCaptured,lateNearDrawn,lateNearFallbacks;
    private final DhApiRenderParam frame=new DhApiRenderParam();
    private final DhApiMat4f nativeMatrix=new DhApiMat4f();
    private final EntityFX[] owners=new EntityFX[8];
    private final int[] scopeQuads=new int[8];
    private int depth, observed, captured, rendered, batches, rejected, cullingHandoffs;
    private final int[] seen=new int[4];
    private boolean frameReady,failed;
    private Field mchCamera;
    private boolean cameraChecked,alphaResolved;
    private Method alphaEnabled,alphaState,alphaReference;
    private double dhX,dhY,dhZ;
    private GlShaderProgram shader;
    private int vao,vbo;
    private final LightMapWrapper lightmap=new LightMapWrapper();
    private final ByteBuffer upload=org.lwjgl.BufferUtils.createByteBuffer(EffectQuadBuffer.CAPACITY*6*32);
    private static final int[] TRIANGLES={0,1,2,0,2,3};
    private int extendedMvp,nativeMvp,dhOffset,useLightmap,alphaCutoff,reverseDepth,nearPass;
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
                if(iris!=null && iris.isShaderPackInUse()) return;
                frame.update(event.value);
                nativeMatrix.set(frame.mcProjectionMatrix);nativeMatrix.multiply(frame.mcModelViewMatrix);
                DhVec3d p=MinecraftRenderWrapper.INSTANCE.getCameraExactPosition();dhX=p.x;dhY=p.y;dhZ=p.z;
                frameReady=true;
            }
        });
    }
    @SubscribeEvent
    public void startFrame(TickEvent.RenderTickEvent event)
    {
        if(event.phase!=TickEvent.Phase.START) return;
        if(hbmNear!=null) hbmNear.clear();lateNearCaptured=0;lateNearDrawn=0;lateNearFallbacks=0;
        NtmTrace.frame();
        frameReady=false;quads.clear();depth=0;observed=0;rejected=0;cullingHandoffs=0;nativeNearQuads=0;nativeStateRead=false;
        for(int i=0;i<seen.length;i++) seen[i]=0;
    }
    /** Reviewed visible effects bypass terrain-viewport culling; native depth still occludes near quads. */
    public static boolean needsExtendedDispatch(EntityFX particle)
    {
        DistantSmokeRenderer r=INSTANCE;
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
        DistantSmokeRenderer r=INSTANCE;
        if(r==null || !EffectSources.enabled() || r.failed || r.depth>=r.owners.length) return false;
        // Overlapping compatibility wrappers must not capture a source quad twice.
        if(r.depth>0 && r.owners[r.depth-1]==particle) return false;
        int family=EffectSources.family(particle);
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
        if(cpw.mods.fml.common.Loader.isModLoaded("mcheli") && !normalMchCamera(owner)) return;
        if(GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)!=Minecraft.getMinecraft().getFramebuffer().framebufferObject) return;
        int[] raw=data.dh$getRawBuffer();
        NtmTrace.geometry(owner,raw,start,end,nativeMatrix);
        int limit=Math.min(EffectQuadBuffer.CAPACITY,Config.Client.Advanced.Graphics.GenericRendering.distantSmokeParticleBudget.get());
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
            if(!far) { nativeNearQuads++;continue; }
            if(scopeQuads[depth-1]>=2) break;
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
            quads.lit[q]=data.dh$hasBrightness();quads.alphaCutoff[q]=cutoff;quads.family[q]=family;
            quads.ox[q]=(float)(ox-dhX);quads.oy[q]=(float)(oy-dhY);quads.oz[q]=(float)(oz-dhZ);
            quads.distance[q]=distance;quads.fade[q]=(float)Math.min(1,Math.max(0,(max-Math.sqrt(distance))/32));
        }
        relocateHbmNear(owner,data,raw,start,end,distance,ox,oy,oz);
    }
    /** ParticleRocketFlame only appends ten atlas quads. Move its near fragments after DH's
     * late fade instead of letting the fade erase them. Every other source keeps its path. */
    private void relocateHbmNear(EntityFX owner,MixinTessellatorAccess data,int[] raw,int start,int end,
        double distance,double ox,double oy,double oz)
    {
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
    private boolean normalMchCamera(EntityFX owner)
    {
        try
        {
            if(!cameraChecked)
            {
                cameraChecked=true;
                mchCamera=Class.forName("mcheli.MCH_Camera",false,owner.getClass().getClassLoader()).getField("currentCameraMode");
            }
            return mchCamera!=null && mchCamera.getInt(null)==0;
        }
        catch(ReflectiveOperationException unsupported) { return false; }
    }
    public static void afterWorldLast(float partialTicks)
    {
        DistantSmokeRenderer r=INSTANCE;
        if(r==null) return;
        try { r.render(partialTicks); }
        finally { r.quads.clear();if(r.hbmNear!=null) r.hbmNear.clear(); }
    }
    private void render(float partialTicks)
    {
        captured=quads.size;rendered=0;batches=0;lateNearCaptured=hbmNear==null?0:hbmNear.size;lateNearDrawn=0;
        for(int i=0;i<quads.size;i++) if(quads.family[i]==TrailSample.HBM) NtmTrace.selected(quads.traceBin[i],quads.traceOrigin[i]);
        if(!EffectSources.enabled()) { status="disabled";return; }
        if(failed) { status="disabled after render error (see log)";return; }
        if(!frameReady) { status="no DH terrain frame";return; }
        lastFar=(float)(frame.mcProjectionMatrix.m23/(frame.mcProjectionMatrix.m22+1.0D));
        IIrisAccessor iris=ModAccessorInjector.INSTANCE.get(IIrisAccessor.class);
        if(iris!=null && iris.isShaderPackInUse()) { status="shader pack unsupported";return; }
        Minecraft mc=Minecraft.getMinecraft();
        if(GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)!=mc.getFramebuffer().framebufferObject)
        { status="non-main framebuffer";return; }
        if(DhApiRenderProxy.activeOpenGlDhDepthTextureId<=0) { status="no DH depth texture";return; }
        if(quads.size==0 && lateNearCaptured==0) { status="ready; no captured geometry";return; }
        try { draw(mc,partialTicks);status="active"; }
        catch(RuntimeException | LinkageError error)
        {
            failed=true;status="render error";
            LogManager.getLogger("DistantHorizons").warn("Distant effects disabled for this session; original effects unchanged",error);
        }
    }
    private void draw(Minecraft mc,float partial)
    {
        try(GLState saved=new GLState())
        {
            if(shader==null)
            {
                shader=new GlShaderProgram("assets/distanthorizons/shaders/distant_smoke.vert","assets/distanthorizons/shaders/distant_smoke.frag",
                    new String[]{"aPosition","aUv","aColor","aLight"});
                nearPass=shader.getUniformLocation("uNearPass");
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
            shader.bind();shader.setUniform(extendedMvp,frame.dhMvmProjMatrix);shader.setUniform(nativeMvp,nativeMatrix);
            shader.setUniform(reverseDepth,com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector.INSTANCE
                .get(com.seibel.distanthorizons.core.wrapperInterfaces.render.AbstractDhRenderApiDefinition.class).getDepthDirection()
                ==com.seibel.distanthorizons.api.enums.config.EDhApiDepthDirection.REVERSE_Z);
            LWJGL.glActiveTexture(GL13.GL_TEXTURE1);LWJGL.glBindTexture(GL11.GL_TEXTURE_2D,lightmap.getOpenGlId());
            LWJGL.glActiveTexture(GL13.GL_TEXTURE2);LWJGL.glBindTexture(GL11.GL_TEXTURE_2D,DhApiRenderProxy.activeOpenGlDhDepthTextureId);
            LWJGL.glActiveTexture(GL13.GL_TEXTURE0);
            LWJGL.glEnable(GL11.GL_BLEND);LWJGL.glEnable(GL11.GL_DEPTH_TEST);LWJGL.glDepthFunc(GL11.GL_LEQUAL);
            LWJGL.glDepthMask(false);LWJGL.glDisable(GL11.GL_CULL_FACE);
            quads.sortBackToFront();
            drawBuffer(quads,false);
            if(hbmNear!=null) drawBuffer(hbmNear,true); // Preserve the original native append order.
        }
    }
    private void drawBuffer(EffectQuadBuffer buffer,boolean near)
    {
        shader.setUniform(nearPass,near);
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
                do { i++;if(near) lateNearDrawn++;else rendered++; } while(i<end && buffer.sameState(q,buffer.order[i]));
                LWJGL.glDrawArrays(GL11.GL_TRIANGLES,(first-chunk)*6,(i-first)*6);batches++;
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
            +"; NTM post-fade near="+lateNearCaptured+"/"+lateNearDrawn+" fallback="+lateNearFallbacks+"; culling handoffs="+cullingHandoffs+"; captured/drawn="+captured+"/"+rendered+" quads in "+batches+" batches; budget drops="+rejected;
    }
}
