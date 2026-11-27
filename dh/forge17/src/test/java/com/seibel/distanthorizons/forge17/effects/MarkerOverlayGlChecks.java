package com.seibel.distanthorizons.forge17.effects;

import com.seibel.distanthorizons.api.objects.math.DhApiMat4f;
import com.seibel.distanthorizons.common.render.openGl.glObject.GLState;
import com.seibel.distanthorizons.forge17.actors.ModelCapture;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

final class MarkerOverlayGlChecks {
    static void runIfAvailable() {
        String natives=System.getenv("DH_TEST_LWJGL_NATIVES");
        if(natives==null) return;
        System.setProperty("org.lwjgl.librarypath",natives);
        Pbuffer context;
        try {
            context=new Pbuffer(16,16,new PixelFormat(),null,null);
            context.makeCurrent();
        } catch(org.lwjgl.LWJGLException error) { throw new AssertionError(error); }
        try { checkCapture(); }
        finally { context.destroy(); }
        System.out.println("Marker overlay and terrain-copy GL state checks passed.");
    }

    private static void checkCapture() {
        int target=GL30.glGenFramebuffers();
        int colour=texture(GL11.GL_RGBA8,GL11.GL_RGBA,null);
        FloatBuffer depth=BufferUtils.createFloatBuffer(16*16);
        for(int i=0;i<16*16;i++) depth.put(.5F);
        depth.flip();
        int depthTexture=texture(GL30.GL_R32F,GL11.GL_RED,depth);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,target);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,colour,0);
        GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
        GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
        check(GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER)==GL30.GL_FRAMEBUFFER_COMPLETE,"fixture framebuffer");
        GL11.glClearColor(0,0,0,1);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
        DhApiMat4f identity=new DhApiMat4f();
        identity.m00=identity.m11=identity.m22=identity.m33=1;
        try(ModelCapture capture=new ModelCapture()) {
            try(ModelCapture.OverlayPass pass=capture.overlays(target,16,16,identity,identity,depthTexture,depthTexture,false)) {
                fill(pass,identity,0,4,1,0);
                fill(pass,identity,4,8,0,1);
                fill(pass,identity,8,12,0,1);
                pass.startMarkers(identity);
                pass.marker(0,0,4,16,.25F,.25F,0,0);
                pass.marker(4,0,8,16,.25F,.25F,0,0);
                pass.marker(8,0,12,16,.75F,.75F,0,0);
                pass.endComposite();
                fill(pass,identity,2,6,.5F,0);
                pass.startMarkers(identity);
                pass.marker(2,0,6,16,.25F,.25F,0,0);
                pass.endComposite();
            }
            check(GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)==target,"target restored");
            check(GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING)==target,"read target preserved");
            check(GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)==0 && !GL11.glIsEnabled(GL11.GL_SCISSOR_TEST),"program and scissor restored");
            check(GL11.glGetError()==GL11.GL_NO_ERROR,"GL command sequence");
            pixel(1,255,0);
            pixel(3,255,0);
            pixel(5,128,128);
            pixel(7,0,255);
            pixel(9,0,0);
            try(ModelCapture.OverlayPass pass=capture.overlays(target,16,16,identity,identity,depthTexture,depthTexture,false)) {
                check(pass.begin(0,0,4,16,identity),"partial capture started");
            }
            check(GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)==target,"partial capture restores target");
            try(GLState state=new GLState(false)) {
                GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,0);
            }
            check(GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)==target,"unfinished terrain copy restores target");
            try(GLState state=new GLState(false)) {
                GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,0);
                state.keepFramebufferBinding();
            }
            check(GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)==0,"completed terrain copy keeps target");
        } finally {
            GL30.glDeleteFramebuffers(target);
            GL11.glDeleteTextures(colour);
            GL11.glDeleteTextures(depthTexture);
        }
    }

    private static void fill(ModelCapture.OverlayPass pass,DhApiMat4f identity,int left,int right,float red,float green) {
        check(pass.begin(left,0,right,16,identity),"capture started");
        GL11.glClearColor(red,green,0,red+green);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
        pass.end();
    }

    private static int texture(int internal,int format,FloatBuffer data) {
        int id=GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D,id);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,internal,16,16,0,format,GL11.GL_FLOAT,data);
        return id;
    }

    private static void pixel(int x,int red,int green) {
        ByteBuffer colour=BufferUtils.createByteBuffer(4);
        GL11.glReadPixels(x,8,1,1,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,colour);
        check(Math.abs((colour.get(0)&255)-red)<=1 && Math.abs((colour.get(1)&255)-green)<=1,"pixel "+x);
    }

    private static void check(boolean value,String message) {
        if(!value) throw new AssertionError(message);
    }
}
