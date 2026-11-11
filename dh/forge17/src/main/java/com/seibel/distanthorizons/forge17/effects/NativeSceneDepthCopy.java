package com.seibel.distanthorizons.forge17.effects;

import com.seibel.distanthorizons.common.render.openGl.glObject.GLState;
import org.lwjgl.opengl.*;

/** Immutable native scene depth, including hands/cockpit, before detached models draw. */
final class NativeSceneDepthCopy {
    private int framebuffer,texture;
    int capture(int source) {
        if(source<=0) return 0;
        int read=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING),draw=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        try(GLState saved=new GLState()) {
            GL13.glActiveTexture(GL13.GL_TEXTURE0);GL11.glBindTexture(GL11.GL_TEXTURE_2D,source);
            int width=GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_WIDTH);
            int height=GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_HEIGHT);
            if(width<=0 || height<=0 || (long)width*height>16_777_216L) return 0;
            if(framebuffer==0) framebuffer=GL30.glGenFramebuffers();
            if(texture==0) texture=GL11.glGenTextures();
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,framebuffer);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_DEPTH_ATTACHMENT,GL11.GL_TEXTURE_2D,source,0);
            GL11.glDrawBuffer(GL11.GL_NONE);GL11.glReadBuffer(GL11.GL_NONE);
            if(GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER)!=GL30.GL_FRAMEBUFFER_COMPLETE)
                throw new IllegalStateException("Incomplete native actor depth source");
            GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_S,GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_T,GL12.GL_CLAMP_TO_EDGE);
            GL11.glCopyTexImage2D(GL11.GL_TEXTURE_2D,0,GL30.GL_DEPTH_COMPONENT32F,0,0,width,height,0);
            return texture;
        } finally {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,read);GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,draw);
        }
    }
}
