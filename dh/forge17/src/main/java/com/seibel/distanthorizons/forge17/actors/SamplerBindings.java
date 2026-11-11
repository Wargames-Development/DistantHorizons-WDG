package com.seibel.distanthorizons.forge17.actors;
import org.lwjgl.opengl.*;
import static com.seibel.distanthorizons.lwjgl.LWJGLServiceProvider.LWJGL;
/** Sampler objects override depth texture parameters and are not covered by glPushAttrib/GLState. */
@cpw.mods.fml.relauncher.SideOnly(cpw.mods.fml.relauncher.Side.CLIENT)
public final class SamplerBindings implements AutoCloseable {
    private final int[] saved;
    private int count;
    public SamplerBindings(int units) {
        saved=new int[units];
        if(!LWJGL.isOpenGLVersionSupported(3,3)) return;
        int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        try {
            for(int unit=0;unit<units;unit++) {
                GL13.glActiveTexture(GL13.GL_TEXTURE0+unit);saved[unit]=GL11.glGetInteger(GL33.GL_SAMPLER_BINDING);
                count=unit+1;LWJGL.glBindSampler(unit,0);
            }
        } catch(RuntimeException | LinkageError failure) { close();throw failure; }
        finally { GL13.glActiveTexture(active); }
    }
    public void close() { for(int unit=0;unit<count;unit++) LWJGL.glBindSampler(unit,saved[unit]);count=0; }
}
