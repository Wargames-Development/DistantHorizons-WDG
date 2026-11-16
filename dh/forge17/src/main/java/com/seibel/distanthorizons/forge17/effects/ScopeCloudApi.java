package com.seibel.distanthorizons.forge17.effects;

import com.seibel.distanthorizons.common.render.openGl.generic.GlGenericObjectRenderer;
import com.seibel.distanthorizons.common.render.openGl.glObject.GLState;
import com.seibel.distanthorizons.common.wrappers.minecraft.MinecraftGLWrapper;
import com.seibel.distanthorizons.common.wrappers.minecraft.ProfilerWrapper;
import com.seibel.distanthorizons.common.wrappers.misc.LightMapWrapper;
import com.seibel.distanthorizons.core.api.internal.SharedApi;
import com.seibel.distanthorizons.core.api.internal.ClientApi;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.jar.EPlatform;
import com.seibel.distanthorizons.core.level.IDhClientLevel;
import com.seibel.distanthorizons.core.render.RenderParams;
import com.seibel.distanthorizons.core.util.RenderUtil;
import com.seibel.distanthorizons.core.util.math.DhMat4f;
import com.seibel.distanthorizons.core.util.math.DhVec3d;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftRenderWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.misc.ILightMapWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IClientLevelWrapper;
import com.seibel.distanthorizons.core.world.IDhClientWorld;
import com.seibel.distanthorizons.common.wrappers.world.ClientLevelWrapper;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.nio.FloatBuffer;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;

import static com.seibel.distanthorizons.lwjgl.LWJGLServiceProvider.LWJGL;

/** Draws native DH clouds into an isolated shaderless camera's bound target. */
@SideOnly(Side.CLIENT)
public final class ScopeCloudApi {
    private ScopeCloudApi() { }

    private static IDhClientLevel level(Object world) {
        if (world == null || world != Minecraft.getMinecraft().theWorld || EPlatform.get() == EPlatform.MACOS
                || !Config.Client.Advanced.Graphics.GenericRendering.enableGenericRendering.get()
                || !Config.Client.Advanced.Graphics.GenericRendering.enableCloudRendering.get()) return null;
        IClientLevelWrapper wrapper = ClientApi.RENDER_STATE.clientLevelWrapper;
        if (!(wrapper instanceof ClientLevelWrapper) || ((ClientLevelWrapper)wrapper).getLevel() != world) return null;
        IDhClientWorld dhWorld = SharedApi.tryGetDhClientWorld();
        IDhClientLevel level = dhWorld == null ? null : dhWorld.getClientLevel(wrapper);
        return level != null && level.isRendering() && level.getGenericRenderer() instanceof GlGenericObjectRenderer
            && Config.Client.Advanced.Graphics.GenericRendering.dimensionEnabledCloudRenderingCsv.get()
                .contains(wrapper.getDimensionType().getName()) ? level : null;
    }

    public static float farPlane(Object world) {
        return level(world) == null ? 0 : RenderUtil.getFarClipPlaneDistanceInBlocks();
    }

    public static void render(Object world, FloatBuffer projection, FloatBuffer modelView,
                              double x, double y, double z, float partialTicks) {
        IDhClientLevel level = level(world);
        if (level == null) return;
        ILightMapWrapper lightmap = SingletonInjector.INSTANCE.get(IMinecraftRenderWrapper.class)
            .getLightmapWrapper(ClientApi.RENDER_STATE.clientLevelWrapper);
        if (!(lightmap instanceof LightMapWrapper)) return;
        int lightTexture = ((LightMapWrapper)lightmap).getOpenGlId();
        if (lightTexture <= 0) return;

        RenderParams params = new RenderParams();
        params.partialTicks = partialTicks;
        params.exactCameraPosition = new DhVec3d(x, y, z);
        params.dhProjectionMatrix.set(new DhMat4f(readRows(projection)));
        params.dhModelViewMatrix.set(new DhMat4f(readRows(modelView)));
        MinecraftGLWrapper gl = MinecraftGLWrapper.INSTANCE;
        int readFramebuffer = LWJGL.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        try (GLState ignored = new GLState()) {
            gl.enableDepthTest();
            gl.glDepthFunc(GL11.GL_LEQUAL);
            gl.enableDepthMask();
            gl.glActiveTexture(GL13.GL_TEXTURE0 + LightMapWrapper.GL_BOUND_INDEX);
            gl.glBindTexture(lightTexture);
            ((GlGenericObjectRenderer)level.getGenericRenderer()).renderClouds(params,
                new ProfilerWrapper(Minecraft.getMinecraft().mcProfiler));
        } finally {
            gl.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
        }
    }

    private static float[] readRows(FloatBuffer source) {
        float[] rows = new float[16];
        for (int row = 0; row < 4; row++) {
            for (int column = 0; column < 4; column++) rows[row * 4 + column] = source.get(column * 4 + row);
        }
        return rows;
    }
}
