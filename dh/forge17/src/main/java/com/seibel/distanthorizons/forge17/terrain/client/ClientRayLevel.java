package com.seibel.distanthorizons.forge17.terrain.client;
import com.seibel.distanthorizons.common.wrappers.world.ClientLevelWrapper;
import com.seibel.distanthorizons.core.render.RenderThreadTaskHandler;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.ILevelWrapper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.world.World;
@cpw.mods.fml.relauncher.SideOnly(cpw.mods.fml.relauncher.Side.CLIENT)
public final class ClientRayLevel {
    private ClientRayLevel() { }
    public static ILevelWrapper wrapper(World world) {
        return RenderThreadTaskHandler.INSTANCE.isCurrentThread() && world==Minecraft.getMinecraft().theWorld && world instanceof WorldClient
            ?ClientLevelWrapper.getWrapper((WorldClient)world):null;
    }
}
