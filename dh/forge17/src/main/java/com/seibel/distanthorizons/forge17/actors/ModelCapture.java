package com.seibel.distanthorizons.forge17.actors;

import com.seibel.distanthorizons.api.objects.math.DhApiMat4f;
import com.seibel.distanthorizons.common.render.openGl.glObject.GLState;
import com.seibel.distanthorizons.common.render.openGl.glObject.shader.GlShaderProgram;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.nio.FloatBuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import static com.seibel.distanthorizons.lwjgl.LWJGLServiceProvider.LWJGL;

/** Render-thread-owned full-resolution model/self-depth capture, shared between actors.
 * Callers must establish terrain coverage and a valid view before submitting a model.
 */
@SideOnly(Side.CLIENT)
public final class ModelCapture implements AutoCloseable {
    // Two RGBA/depth targets remain bounded while supporting a full-resolution 5K view.
    public static final long MAX_PIXELS=16777216L;
    public static boolean supportsSize(int width,int height) { return width>0 && height>0 && (long)width*height<=MAX_PIXELS; }
    private int framebuffer,colour,depth,width,height,vao,vbo;
    private int savedRead,savedDraw,savedMatrixMode;
    private int left,bottom,right,top;
    private GLState captureState;
    private SamplerBindings captureSamplers;
    private boolean ready;
    private GlShaderProgram shader;
    private int inverseUniform,dhUniform,nativeUniform,reverseUniform,useNativeUniform,writeDepthUniform,reverseModelUniform,offsetUniform;
    private int viewUniform,cloudSamplerUniform,cloudEnabledUniform,cloudRangeUniform;
    private int sceneDepthUniform,storeSceneDepthUniform;
    private int auxiliaryUniform,auxiliaryHeatUniform,auxiliaryDepthUniform,markerUniform,markerDepthUniform,markerTerrainDepthUniform,markerAnchorUniform;
    private boolean opaqueDepthOnly;private int opaqueDepthUniform;
    public void opaqueDepthOnly(boolean value) { opaqueDepthOnly=value; }
    private int occludeWorldUniform;
    private int overlayDepthCopy,overlayDepthWidth,overlayDepthHeight;
    private boolean auxiliary,auxiliaryHeat;
    private float auxiliaryZ,auxiliaryOffset,auxiliaryEye,auxiliaryStart;

    private final int[] samplerUniforms=new int[4];
    private final FloatBuffer matrix=BufferUtils.createFloatBuffer(16);
    private final float[] rows=new float[16];

    public void prepareCompositor() { try(GLState state=new GLState()) { ensureShader(); } }
    public void prepare(int width,int height) {
        int read=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING),draw=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        if(!supportsSize(width,height)) throw new IllegalArgumentException("Actor target budget: "+width+"x"+height);
        try(GLState state=new GLState()) { ensure(width,height);ensureShader(); }
        finally { GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,read);GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,draw); }
    }
    /** The same unscaled XY projection is retained; only model near/far depth changes. */
    public boolean begin(int width,int height,int left,int bottom,int right,int top,DhApiMat4f projection) {
        if(captureState!=null) throw new IllegalStateException("Nested model capture");
        ready=false;
        if(width<=0 || height<=0 || (long)width*height>MAX_PIXELS || left<0 || bottom<0 || right>width || top>height
                || right<=left || top<=bottom || !LWJGL.isOpenGLVersionSupported(3,0)) return false;
        this.left=left;this.bottom=bottom;this.right=right;this.top=top;
        savedRead=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        savedDraw=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        savedMatrixMode=GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        captureState=new GLState();
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glMatrixMode(GL11.GL_PROJECTION);GL11.glPushMatrix();
        try {
            captureSamplers=new SamplerBindings(4);
            ensure(width,height);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,framebuffer);
            setupCapture(width,height,left,bottom,right,top,projection);
            return true;
        } catch(RuntimeException | LinkageError error) { end(false);throw error; }
    }
    /** Always call in finally, including when a companion renderer throws. */
    public void end(boolean completed) {
        if(captureState==null) return;
        try {
            GL11.glMatrixMode(GL11.GL_PROJECTION);GL11.glPopMatrix();
            GL11.glPopAttrib();captureState.close();
        } finally {
            if(captureSamplers!=null) { captureSamplers.close();captureSamplers=null; }
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,savedRead);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,savedDraw);
            GL11.glMatrixMode(savedMatrixMode);captureState=null;ready=completed;
        }
    }
    /** Composite into a caller-owned DH-depth actor target or the final native scene.
     * Terrain/native depth arguments must be immutable copies, never draw attachments.
     */
    public void composite(DhApiMat4f inverseCapture,DhApiMat4f dhMvp,DhApiMat4f nativeMvp,
                          float offsetX,float offsetY,float offsetZ,int terrainDepth,int nativeDepth,
                          boolean reverseDepth,boolean writeDhDepth) {
        if(!ready || captureState!=null) throw new IllegalStateException("No completed model capture");
        compositeSource(colour,depth,width,height,left,bottom,right,top,false,inverseCapture,dhMvp,nativeMvp,
            offsetX,offsetY,offsetZ,terrainDepth,nativeDepth,reverseDepth,writeDhDepth,nativeMvp,0,0,false,false);
    }
    /** Merge captured physical surfaces into the private actor depth buffer. */
    public void compositeActorScene(DhApiMat4f inverseCapture,DhApiMat4f dhMvp,DhApiMat4f nativeMvp,
                                    int terrainDepth,int nativeDepth,boolean reverseTerrainDepth) {
        if(!ready || captureState!=null) throw new IllegalStateException("No completed model capture");
        compositeSource(colour,depth,width,height,left,bottom,right,top,false,inverseCapture,dhMvp,nativeMvp,
            0,0,0,terrainDepth,nativeDepth,reverseTerrainDepth,false,nativeMvp,0,0,false,true);
    }
    public void compositeSource(int sourceColour,int sourceDepth,int sourceWidth,int sourceHeight,
                                int x0,int y0,int x1,int y1,boolean sourceReverseDepth,
                                DhApiMat4f inverseCapture,DhApiMat4f dhMvp,DhApiMat4f nativeMvp,
                                float offsetX,float offsetY,float offsetZ,int terrainDepth,int nativeDepth,
                                boolean reverseDepth,boolean writeDhDepth,DhApiMat4f nativeView,int cloudTexture,float cloudDistance,boolean sceneDepth,boolean storeSceneDepth) {
        if(captureState!=null || sourceColour<=0 || sourceDepth<=0 || sourceWidth<=0 || sourceHeight<=0
                || (long)sourceWidth*sourceHeight>MAX_PIXELS || x0<0 || y0<0 || x1>sourceWidth || y1>sourceHeight || x0>=x1 || y0>=y1)
            throw new IllegalArgumentException("Invalid model composite input");
        int draw=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        if((framebuffer!=0 && draw==framebuffer) || (!auxiliary && terrainDepth<=0)) throw new IllegalArgumentException("Invalid model composite target");
        checkDepthFeedback(draw,sourceDepth,terrainDepth,nativeDepth);
        int read=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        boolean normalBlend=GL30.glIsEnabledi(GL11.GL_BLEND,2);
        // GLState restores units 0..3 only. Preserve the additional cloud unit explicitly.
        int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL13.glActiveTexture(GL13.GL_TEXTURE4);int cloudBinding=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);GL13.glActiveTexture(active);
        try(GLState state=new GLState();SamplerBindings samplers=new SamplerBindings(5)) {
            GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
            try {
                ensureShader();shader.bind();
                shader.setUniform(opaqueDepthUniform,opaqueDepthOnly);shader.setUniform(occludeWorldUniform,true);shader.setUniform(markerUniform,false);
                shader.setUniform(auxiliaryUniform,auxiliary);shader.setUniform(auxiliaryHeatUniform,auxiliaryHeat);
                GL20.glUniform4f(auxiliaryDepthUniform,auxiliaryZ,auxiliaryOffset,auxiliaryEye,auxiliaryStart);
                shader.setUniform(inverseUniform,inverseCapture);shader.setUniform(dhUniform,dhMvp);shader.setUniform(nativeUniform,nativeMvp);
                shader.setUniform(sceneDepthUniform,sceneDepth);
                shader.setUniform(storeSceneDepthUniform,storeSceneDepth);
                shader.setUniform(reverseUniform,reverseDepth);
                shader.setUniform(useNativeUniform,nativeDepth>0);
                shader.setUniform(writeDepthUniform,writeDhDepth);
                shader.setUniform(reverseModelUniform,sourceReverseDepth);
                shader.setUniform(viewUniform,nativeView);shader.setUniform(cloudSamplerUniform,4);
                shader.setUniform(cloudEnabledUniform,cloudTexture>0 && cloudDistance>0);
                shader.setUniform(cloudRangeUniform,cloudDistance);
                GL13.glActiveTexture(GL13.GL_TEXTURE4);GL11.glBindTexture(GL11.GL_TEXTURE_2D,cloudTexture);
                GL20.glUniform3f(offsetUniform,offsetX,offsetY,offsetZ);
                bindTexture(0,sourceColour);bindTexture(1,sourceDepth);bindTexture(2,terrainDepth);bindTexture(3,nativeDepth);
                GL11.glViewport(0,0,sourceWidth,sourceHeight);GL11.glEnable(GL11.GL_SCISSOR_TEST);GL11.glScissor(x0,y0,x1-x0,y1-y0);
                GL11.glDisable(GL11.GL_LIGHTING);GL11.glDisable(GL11.GL_FOG);GL11.glDisable(GL11.GL_ALPHA_TEST);GL11.glDisable(GL11.GL_CULL_FACE);
                GL11.glEnable(GL11.GL_BLEND);GL11.glBlendFunc(GL11.GL_ONE,GL11.GL_ONE_MINUS_SRC_ALPHA);
                GL30.glDisablei(GL11.GL_BLEND,2);
                GL11.glColorMask(!opaqueDepthOnly,!opaqueDepthOnly,!opaqueDepthOnly,!opaqueDepthOnly);GL11.glDepthMask(true);
                GL11.glEnable(GL11.GL_DEPTH_TEST);GL11.glDepthRange(0,1);
                GL11.glDepthFunc(storeSceneDepth || (writeDhDepth && reverseDepth)?GL11.GL_GEQUAL:GL11.GL_LEQUAL);
                GL30.glBindVertexArray(vao);GL11.glDrawArrays(GL11.GL_TRIANGLES,0,6);
            } finally { GL11.glPopAttrib(); }
        } finally {
            if(normalBlend) GL30.glEnablei(GL11.GL_BLEND,2);else GL30.glDisablei(GL11.GL_BLEND,2);
            GL13.glActiveTexture(GL13.GL_TEXTURE4);GL11.glBindTexture(GL11.GL_TEXTURE_2D,cloudBinding);GL13.glActiveTexture(active);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,read);GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,draw);
        }
    }
    public OverlayPass overlays(int target,int width,int height,DhApiMat4f dhMvp,DhApiMat4f nativeMvp,
                                int terrain,int nativeDepth,boolean reverse) {
        return new OverlayPass(target,width,height,dhMvp,nativeMvp,terrain,nativeDepth,reverse);
    }

    /** One render-thread pass with a fixed scene/depth source. Callbacks draw into the current target. */
    public final class OverlayPass implements AutoCloseable {
        private final int target,width,height,terrain;
        private final DhApiMat4f dhMvp,nativeMvp;
        private final boolean reverse;
        private int nativeDepth,program,previousVao,arrayBuffer,elementBuffer,mode,activeTexture;
        private int texture0,texture1,texture2,texture3;
        private int left,bottom,right,top;
        private boolean drawing,uniformsReady,normalBlend;
        private SamplerBindings samplers;

        private OverlayPass(int target,int width,int height,DhApiMat4f dhMvp,DhApiMat4f nativeMvp,
                            int terrain,int nativeDepth,boolean reverse) {
            this.target=target;this.width=width;this.height=height;this.dhMvp=dhMvp;this.nativeMvp=nativeMvp;
            this.terrain=terrain;this.nativeDepth=nativeDepth;this.reverse=reverse;
        }

        public boolean begin(int left,int bottom,int right,int top,DhApiMat4f projection) {
            if(samplers==null) {
                if(!LWJGL.isOpenGLVersionSupported(3,0)) return false;
                mode=GL11.glGetInteger(GL11.GL_MATRIX_MODE);
                program=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
                previousVao=GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
                arrayBuffer=GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
                elementBuffer=GL11.glGetInteger(GL15.GL_ELEMENT_ARRAY_BUFFER_BINDING);
                normalBlend=GL30.glIsEnabledi(GL11.GL_BLEND,2);
                samplers=new SamplerBindings(4);
                activeTexture=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
                texture0=textureBinding(0);texture1=textureBinding(1);
                texture2=textureBinding(2);texture3=textureBinding(3);
                GL13.glActiveTexture(activeTexture);
                if(nativeDepth<=0) nativeDepth=copyOverlayDepth(target,width,height);
                checkDepthFeedback(target,depth,terrain,nativeDepth);
            }
            this.left=left;this.bottom=bottom;this.right=right;this.top=top;
            // Legacy callbacks can change fixed-function state. Keep that isolation local to each draw.
            GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
            GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glPushMatrix();
            GL11.glMatrixMode(GL11.GL_PROJECTION);GL11.glPushMatrix();
            drawing=true;
            try {
                if(framebuffer==0 || width!=ModelCapture.this.width || height!=ModelCapture.this.height) {
                    GL11.glPushAttrib(GL11.GL_TEXTURE_BIT);
                    try { ensure(width,height); }
                    finally { GL11.glPopAttrib(); }
                } else GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,framebuffer);
                setupCapture(width,height,left,bottom,right,top,projection);
                GL20.glUseProgram(0);
                return true;
            } catch(RuntimeException | LinkageError error) { end();throw error; }
        }

        public void end() {
            if(!drawing) return;
            drawing=false;
            GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_PROJECTION);GL11.glPopMatrix();
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,target);
            GL11.glPopAttrib();
        }

        public void marker(DhApiMat4f inverse,float front,float centre,float x,float y) {
            compositeOverlay(inverse,true,true,front,centre,x,y);
        }

        public void beam(DhApiMat4f inverse,boolean occlude) {
            compositeOverlay(inverse,false,occlude,0,0,0,0);
        }

        private void compositeOverlay(DhApiMat4f inverse,boolean marker,boolean occlude,float front,float centre,float x,float y) {
            GL11.glPushAttrib(GL11.GL_ENABLE_BIT|GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT
                |GL11.GL_SCISSOR_BIT|GL11.GL_VIEWPORT_BIT);
            try {
                ensureShader();shader.bind();
                if(!uniformsReady) {
                    shader.setUniform(dhUniform,dhMvp);shader.setUniform(nativeUniform,nativeMvp);
                    shader.setUniform(reverseUniform,reverse);shader.setUniform(useNativeUniform,true);
                    shader.setUniform(opaqueDepthUniform,false);shader.setUniform(auxiliaryUniform,false);
                    shader.setUniform(sceneDepthUniform,false);shader.setUniform(storeSceneDepthUniform,false);
                    shader.setUniform(writeDepthUniform,false);shader.setUniform(reverseModelUniform,false);
                    shader.setUniform(cloudSamplerUniform,4);shader.setUniform(cloudEnabledUniform,false);
                    GL20.glUniform3f(offsetUniform,0,0,0);
                    for(int unit=0;unit<4;unit++) shader.setUniform(samplerUniforms[unit],unit);
                    uniformsReady=true;
                }
                shader.setUniform(inverseUniform,inverse);shader.setUniform(markerUniform,marker);
                shader.setUniform(occludeWorldUniform,occlude);
                if(marker) {
                    shader.setUniform(markerDepthUniform,front);shader.setUniform(markerTerrainDepthUniform,centre);
                    GL20.glUniform2f(markerAnchorUniform,x,y);
                }
                bindUnit(0,colour);bindUnit(1,depth);bindUnit(2,terrain);bindUnit(3,nativeDepth);
                GL11.glViewport(0,0,width,height);GL11.glEnable(GL11.GL_SCISSOR_TEST);
                GL11.glScissor(left,bottom,right-left,top-bottom);
                GL11.glDisable(GL11.GL_LIGHTING);GL11.glDisable(GL11.GL_FOG);GL11.glDisable(GL11.GL_ALPHA_TEST);GL11.glDisable(GL11.GL_CULL_FACE);
                GL11.glEnable(GL11.GL_BLEND);GL11.glBlendFunc(GL11.GL_ONE,GL11.GL_ONE_MINUS_SRC_ALPHA);
                GL30.glDisablei(GL11.GL_BLEND,2);
                GL11.glColorMask(true,true,true,true);GL11.glDepthMask(false);GL11.glDisable(GL11.GL_DEPTH_TEST);
                GL30.glBindVertexArray(vao);GL11.glDrawArrays(GL11.GL_TRIANGLES,0,6);
            } finally {
                GL11.glPopAttrib();
                if(normalBlend) GL30.glEnablei(GL11.GL_BLEND,2);else GL30.glDisablei(GL11.GL_BLEND,2);
                bindUnit(0,texture0);bindUnit(1,texture1);bindUnit(2,texture2);bindUnit(3,texture3);
                GL13.glActiveTexture(activeTexture);
                restoreBuffers();
            }
        }

        private void restoreBuffers() {
            GL30.glBindVertexArray(previousVao);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,arrayBuffer);
            GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER,elementBuffer);
        }

        @Override public void close() {
            if(samplers==null) return;
            end();
            if(!uniformsReady) restoreBuffers();
            GL20.glUseProgram(program);
            GL11.glMatrixMode(mode);
            samplers.close();samplers=null;
        }
    }

    private int copyOverlayDepth(int target,int width,int height) {
        int read=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        GL11.glPushAttrib(GL11.GL_TEXTURE_BIT);
        try {
            if(read!=target) GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,target);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            if(overlayDepthCopy==0) {
                overlayDepthCopy=GL11.glGenTextures();
                GL11.glBindTexture(GL11.GL_TEXTURE_2D,overlayDepthCopy);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);
            } else GL11.glBindTexture(GL11.GL_TEXTURE_2D,overlayDepthCopy);
            if(width!=overlayDepthWidth || height!=overlayDepthHeight) {
                GL11.glCopyTexImage2D(GL11.GL_TEXTURE_2D,0,GL14.GL_DEPTH_COMPONENT24,0,0,width,height,0);
                overlayDepthWidth=width;overlayDepthHeight=height;
            } else GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D,0,0,0,0,0,width,height);
            return overlayDepthCopy;
        } finally {
            GL11.glPopAttrib();
            if(read!=target) GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,read);
        }
    }

    private static void checkDepthFeedback(int target,int sourceDepth,int terrainDepth,int nativeDepth) {
        if(target!=0 && GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER,GL30.GL_DEPTH_ATTACHMENT,
                GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE)==GL11.GL_TEXTURE) {
            int attachment=GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER,GL30.GL_DEPTH_ATTACHMENT,
                GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
            if(attachment==terrainDepth || attachment==nativeDepth || attachment==sourceDepth) throw new IllegalArgumentException("Depth feedback loop");
        }
    }
    /** Composite physical model depth into FMUR's existing isolated-camera depth law. */
    public void compositeAuxiliary(DhApiMat4f inverseCapture,DhApiMat4f mvp,DhApiMat4f view,
                                   DhApiMat4f projection,float start,boolean heat) {
        if(!ready || captureState!=null || !Float.isFinite(start) || start<1 || start>=4096) throw new IllegalArgumentException("Invalid auxiliary capture");
        auxiliary=true;auxiliaryHeat=heat;auxiliaryZ=projection.m22;auxiliaryOffset=projection.m23;auxiliaryEye=view.m23;auxiliaryStart=start;
        try { compositeSource(colour,depth,width,height,left,bottom,right,top,false,inverseCapture,mvp,mvp,
                0,0,0,0,0,false,false,view,0,0,false,false); }
        finally { auxiliary=false;auxiliaryHeat=false; }
    }
    private void bindTexture(int unit,int texture) {
        bindUnit(unit,texture);
        shader.setUniform(samplerUniforms[unit],unit);
    }
    private static void bindUnit(int unit,int texture) {
        GL13.glActiveTexture(GL13.GL_TEXTURE0+unit);GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);
    }
    private static int textureBinding(int unit) {
        GL13.glActiveTexture(GL13.GL_TEXTURE0+unit);
        return GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
    }
    private void setupCapture(int width,int height,int left,int bottom,int right,int top,DhApiMat4f projection) {
        GL11.glViewport(0,0,width,height);GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(left,bottom,right-left,top-bottom);
        GL11.glColorMask(true,true,true,true);GL11.glDepthMask(true);
        GL11.glClearColor(0,0,0,0);GL11.glClearDepth(1);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glEnable(GL11.GL_DEPTH_TEST);GL11.glDepthFunc(GL11.GL_LEQUAL);GL11.glDepthRange(0,1);
        columnMajor(projection);GL11.glLoadMatrix(matrix);GL11.glMatrixMode(GL11.GL_MODELVIEW);
    }
    private void columnMajor(DhApiMat4f value) {
        value.putValuesInArray(rows);matrix.clear();
        for(int column=0;column<4;column++) for(int row=0;row<4;row++) matrix.put(rows[row*4+column]);
        matrix.flip();
    }
    private void ensure(int width,int height) {
        if(this.width==width && this.height==height && framebuffer!=0) return;
        if(Math.max(width,height)>GL11.glGetInteger(GL11.GL_MAX_TEXTURE_SIZE))
            throw new IllegalArgumentException("Model capture exceeds texture limit");
        releaseTarget();
        framebuffer=GL30.glGenFramebuffers();colour=texture(width,height,GL30.GL_RGBA16F,GL11.GL_RGBA,GL11.GL_FLOAT);
        depth=texture(width,height,GL14.GL_DEPTH_COMPONENT24,GL11.GL_DEPTH_COMPONENT,GL11.GL_UNSIGNED_INT);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,framebuffer);
        GL30.glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,colour,0);
        GL30.glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER,GL30.GL_DEPTH_ATTACHMENT,GL11.GL_TEXTURE_2D,depth,0);
        GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
        if(GL30.glCheckFramebufferStatus(GL30.GL_DRAW_FRAMEBUFFER)!=GL30.GL_FRAMEBUFFER_COMPLETE) {
            releaseTarget();throw new IllegalStateException("Incomplete actor capture framebuffer");
        }
        this.width=width;this.height=height;
    }
    private static int texture(int width,int height,int internal,int format,int type) {
        int id=GL11.glGenTextures();GL13.glActiveTexture(GL13.GL_TEXTURE0);GL11.glBindTexture(GL11.GL_TEXTURE_2D,id);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_S,GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_T,GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,internal,width,height,0,format,type,(java.nio.ByteBuffer)null);return id;
    }
    private void ensureShader() {
        if(shader!=null) return;
        shader=new GlShaderProgram("assets/distanthorizons/shaders/actor_model.vert","assets/distanthorizons/shaders/actor_model.frag",
            new String[]{"aPosition"},new String[]{"fragColor","fragActorMaterial","fragActorNormal"});
        opaqueDepthUniform=shader.getUniformLocation("uOpaqueDepthOnly");
        occludeWorldUniform=shader.getUniformLocation("uOccludeWorld");
        inverseUniform=shader.getUniformLocation("uInverseModelMvp");dhUniform=shader.getUniformLocation("uDhMvp");
        nativeUniform=shader.getUniformLocation("uNativeMvp");reverseUniform=shader.getUniformLocation("uDhReverseDepth");
        useNativeUniform=shader.getUniformLocation("uUseNativeDepth");writeDepthUniform=shader.getUniformLocation("uWriteDhDepth");
        reverseModelUniform=shader.getUniformLocation("uModelReverseDepth");offsetUniform=shader.getUniformLocation("uDhOriginOffset");
        viewUniform=shader.getUniformLocation("uNativeView");cloudSamplerUniform=shader.getUniformLocation("uCloudDepth");
        cloudEnabledUniform=shader.getUniformLocation("uUseCloudDepth");cloudRangeUniform=shader.getUniformLocation("uCloudRenderDistance");
        sceneDepthUniform=shader.getUniformLocation("uSceneDepth");storeSceneDepthUniform=shader.getUniformLocation("uStoreSceneDepth");
        markerUniform=shader.getUniformLocation("uMarkerOverlay");markerDepthUniform=shader.getUniformLocation("uMarkerDepth");markerTerrainDepthUniform=shader.getUniformLocation("uMarkerTerrainDepth");markerAnchorUniform=shader.getUniformLocation("uMarkerAnchor");
        auxiliaryUniform=shader.getUniformLocation("uAuxiliary");auxiliaryHeatUniform=shader.getUniformLocation("uAuxiliaryHeat");auxiliaryDepthUniform=shader.getUniformLocation("uAuxiliaryDepth");
        String[] names={"uModelColour","uModelDepth","uDhDepth","uNativeDepth"};
        for(int unit=0;unit<4;unit++) samplerUniforms[unit]=shader.getUniformLocation(names[unit]);
        vao=GL30.glGenVertexArrays();vbo=GL15.glGenBuffers();GL30.glBindVertexArray(vao);GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,vbo);
        FloatBuffer vertices=BufferUtils.createFloatBuffer(12);vertices.put(new float[]{-1,-1,1,-1,1,1,-1,-1,1,1,-1,1}).flip();
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER,vertices,GL15.GL_STATIC_DRAW);
        GL20.glVertexAttribPointer(0,2,GL11.GL_FLOAT,false,0,0);GL20.glEnableVertexAttribArray(0);
    }
    private void releaseTarget() {
        if(framebuffer!=0) GL30.glDeleteFramebuffers(framebuffer);if(colour!=0) GL11.glDeleteTextures(colour);if(depth!=0) GL11.glDeleteTextures(depth);
        framebuffer=colour=depth=width=height=0;ready=false;
    }
    public void close() {
        if(captureState!=null) end(false);releaseTarget();
        if(overlayDepthCopy!=0) GL11.glDeleteTextures(overlayDepthCopy);
        overlayDepthCopy=overlayDepthWidth=overlayDepthHeight=0;
        if(shader!=null) { shader.free();shader=null; }if(vao!=0) GL30.glDeleteVertexArrays(vao);if(vbo!=0) GL15.glDeleteBuffers(vbo);vao=vbo=0;
    }
}
