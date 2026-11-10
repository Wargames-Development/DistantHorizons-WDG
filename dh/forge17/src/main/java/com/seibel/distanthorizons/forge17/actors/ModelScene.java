package com.seibel.distanthorizons.forge17.actors;

import com.seibel.distanthorizons.api.objects.math.DhApiMat4f;
import com.seibel.distanthorizons.common.render.openGl.glObject.GLState;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import org.lwjgl.opengl.*;

/** One shared actor scene retains per-pixel inter-actor depth before native compositing. */
@SideOnly(Side.CLIENT)
public final class ModelScene implements AutoCloseable {
    private final ModelCapture compositor=new ModelCapture();
    private int framebuffer,colour,depth,width,height,read,draw;
    private boolean reverse,ready;
    private GLState saved;
    public void prepare(int width,int height) {
        if(width<=0 || height<=0 || (long)width*height>8388608L) throw new IllegalArgumentException("Actor scene budget");
        int oldRead=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING),oldDraw=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        try(GLState state=new GLState()) { ensure(width,height);compositor.prepareCompositor(); }
        finally { GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,oldRead);GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,oldDraw); }
    }
    public void begin(int width,int height,boolean reverse) {
        if(saved!=null) throw new IllegalStateException("Nested actor scene");
        read=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);draw=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        saved=new GLState();GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);ready=false;this.reverse=reverse;
        try {
            if(width<=0 || height<=0 || (long)width*height>8388608L) throw new IllegalArgumentException("Actor scene budget");
            ensure(width,height);GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,framebuffer);GL11.glViewport(0,0,width,height);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);GL11.glColorMask(true,true,true,true);GL11.glDepthMask(true);
            GL11.glClearColor(0,0,0,0);GL11.glClearDepth(reverse?0:1);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);
        } catch(RuntimeException | LinkageError error) { end(false);throw error; }
    }
    public void end(boolean completed) {
        if(saved==null) return;
        try { GL11.glPopAttrib();saved.close(); }
        finally {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,read);GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,draw);
            saved=null;ready=completed;
        }
    }
    public void composite(DhApiMat4f inverseDh,DhApiMat4f dhMvp,DhApiMat4f nativeFromDh,int terrainDepth,int nativeDepth,boolean shaderScene) {
        if(!ready || saved!=null) throw new IllegalStateException("Incomplete actor scene");
        compositor.compositeSource(colour,depth,width,height,0,0,width,height,reverse,inverseDh,dhMvp,nativeFromDh,
            0,0,0,terrainDepth,nativeDepth,reverse,shaderScene);
    }
    private void ensure(int width,int height) {
        if(framebuffer!=0 && this.width==width && this.height==height) return;
        if(width>GL11.glGetInteger(GL11.GL_MAX_TEXTURE_SIZE) || height>GL11.glGetInteger(GL11.GL_MAX_TEXTURE_SIZE)) throw new IllegalArgumentException("Actor scene texture limit");
        release();framebuffer=GL30.glGenFramebuffers();colour=texture(width,height,GL11.GL_RGBA8,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE);
        depth=texture(width,height,GL14.GL_DEPTH_COMPONENT24,GL11.GL_DEPTH_COMPONENT,GL11.GL_UNSIGNED_INT);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,framebuffer);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,colour,0);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_DEPTH_ATTACHMENT,GL11.GL_TEXTURE_2D,depth,0);
        GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
        if(GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER)!=GL30.GL_FRAMEBUFFER_COMPLETE) { release();throw new IllegalStateException("Incomplete actor scene"); }
        this.width=width;this.height=height;
    }
    private static int texture(int width,int height,int internal,int format,int type) {
        int id=GL11.glGenTextures();GL13.glActiveTexture(GL13.GL_TEXTURE0);GL11.glBindTexture(GL11.GL_TEXTURE_2D,id);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_S,GL12.GL_CLAMP_TO_EDGE);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_T,GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,internal,width,height,0,format,type,(java.nio.ByteBuffer)null);return id;
    }
    private void release() {
        if(framebuffer!=0) GL30.glDeleteFramebuffers(framebuffer);if(colour!=0) GL11.glDeleteTextures(colour);if(depth!=0) GL11.glDeleteTextures(depth);
        framebuffer=colour=depth=width=height=0;ready=false;
    }
    public void close() { if(saved!=null) end(false);release();compositor.close(); }
}
