/*
 * This file is part of the Distant Horizons mod
 * licensed under the GNU LGPL v3 License.
 * Copyright (C) 2020 James Seibel
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, version 3.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.seibel.distanthorizons.forge17;

import net.minecraft.client.Minecraft;
import com.seibel.distanthorizons.forge17.effects.DistantSmokeRenderer;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

import com.seibel.distanthorizons.common.AbstractModInitializer;
import com.seibel.distanthorizons.common.util.ProxyUtil;
import com.seibel.distanthorizons.common.wrappers.chunk.ChunkWrapper;
import com.seibel.distanthorizons.core.api.internal.ClientApi;
import com.seibel.distanthorizons.core.api.internal.ServerApi;
import com.seibel.distanthorizons.core.api.internal.SharedApi;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.logging.DhLogger;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.logging.f3.F3Screen;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftClientWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.ILevelWrapper;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.InputEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * This handles all events sent to the client.
 */
public class ForgeClientProxy implements AbstractModInitializer.IEventProxy 
{

    private static final IMinecraftClientWrapper MC = SingletonInjector.INSTANCE.get(IMinecraftClientWrapper.class);
    private static final DhLogger LOGGER = new DhLoggerBuilder().build();
	
	
	//=======//
	// setup //
	//=======//
	//region
	
    @Override
    public void registerEvents()
    {
        MinecraftForge.EVENT_BUS.register(this);
        DistantSmokeRenderer distantSmoke = new DistantSmokeRenderer();
        MinecraftForge.EVENT_BUS.register(distantSmoke);
        FMLCommonHandler.instance().bus().register(distantSmoke);
        com.seibel.distanthorizons.forge17.effects.TrailClient trails=new com.seibel.distanthorizons.forge17.effects.TrailClient();
        FMLCommonHandler.instance().bus().register(trails);
        FMLCommonHandler.instance().bus().register(new com.seibel.distanthorizons.forge17.actors.PlayerClient());
        if(cpw.mods.fml.common.Loader.isModLoaded("mcheli")) FMLCommonHandler.instance().bus().register(new com.seibel.distanthorizons.forge17.actors.VehicleClient());
        com.seibel.distanthorizons.forge17.effects.ActorRenderer players=new com.seibel.distanthorizons.forge17.effects.ActorRenderer();
        MinecraftForge.EVENT_BUS.register(players);
        FMLCommonHandler.instance().bus().register(players);
        net.minecraftforge.client.ClientCommandHandler.instance.registerCommand(
            new com.seibel.distanthorizons.forge17.effects.DistantEffectsCommand());
        FMLCommonHandler.instance()
            .bus()
            .register(this);
		
        ForgePluginPacketSender.setPacketHandler((player, message) -> 
        {
            ClientApi.INSTANCE.pluginMessageReceived(message);
            ServerApi.INSTANCE.pluginMessageReceived(player, message);
        });
    }
	
	//endregion
	
	
	
    //==============//
    // chunk events //
    //==============//
	//region
	
    @SubscribeEvent
    public void clickBlockEvent(PlayerInteractEvent event) 
    {
        if (MC.clientConnectedToDedicatedServer()) 
		{
            ILevelWrapper wrappedLevel = ProxyUtil.getLevelWrapper(event.world);
            if (SharedApi.isChunkAtBlockPosAlreadyUpdating(wrappedLevel, event.x, event.z)) 
			{
                return;
            }
			
            Minecraft.getMinecraft()
	            // add to render thread executor
                .addScheduledTask(() -> 
                {
	                World level = event.world;
                    Chunk chunk = level.getChunkFromBlockCoords(event.x, event.z);
                    ChunkWrapper chunkWrapper = new ChunkWrapper(chunk, wrappedLevel);
                    SharedApi.INSTANCE.applyChunkUpdate(chunkWrapper, wrappedLevel, true);
                });
        }
    }
	
	public static void onClientChunkFilled(Chunk chunk)
	{
		if (!MC.clientConnectedToDedicatedServer())
		{
			return;
		}
		
		World level = chunk.worldObj;
		if (level == null || !level.isRemote)
		{
			return;
		}
		
		ILevelWrapper wrappedLevel = ProxyUtil.getLevelWrapper(level);
		ChunkWrapper chunkWrapper = new ChunkWrapper(chunk, wrappedLevel);
		if (!ChunkWrapper.canSaveChunk(chunk))
		{
			LOGGER.info(
				"Skipping client chunk update for not-ready chunk [" + chunk.xPosition + "," + chunk.zPosition + "].");
			return;
		}
		
		SharedApi.INSTANCE.applyChunkUpdate(chunkWrapper, wrappedLevel, true);
	}
	
	//endregion
	
	
	
    //==============//
    // key bindings //
    //==============//
	//region

    @SubscribeEvent
    public void registerKeyBindings(InputEvent.KeyInputEvent event) 
    {
        if (Minecraft.getMinecraft().thePlayer == null) 
		{
            return;
        }
        /*
         * TODO if (event.getAction() != GLFW.GLFW_PRESS)
         * {
         * return;
         * }
         */

        // TODO ClientApi.INSTANCE.keyPressedEvent(event.getKey());
    }
	
	//endregion
	
	
	
    //===========//
    // rendering //
    //===========//
	//region

    @SubscribeEvent
    public void afterLevelRenderEvent(TickEvent.RenderTickEvent event)
    {
	    if (event.type.equals(TickEvent.RenderTickEvent.Type.RENDER))
	    {
		    boolean framebufferMixinWasEnabled = MixinFlags.framebufferMixinEnabled;
		    MixinFlags.framebufferMixinEnabled = true;
            try 
            {
                if (!framebufferMixinWasEnabled) 
				{
                    // The splash screen may have created MC's main framebuffer before our redirect was enabled.
                    // Rebuild it once after the first real render tick so the depth attachment becomes a texture.
                    Framebuffer framebuffer = Minecraft.getMinecraft()
                        .getFramebuffer();
                    if (framebuffer != null) 
					{
                        framebuffer.createBindFramebuffer(framebuffer.framebufferWidth, framebuffer.framebufferHeight);
                    }
                }
            } 
			catch (Throwable e) 
			{
                LOGGER.error("Unexpected error in afterLevelRenderEvent: [" + e.getMessage() + "].", e);
            }
        }
    }

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Text event) 
    {
        Minecraft mc = Minecraft.getMinecraft();
        if (event.isCanceled()
	        || !mc.gameSettings.showDebugInfo)
        {
			return;
        }

        F3Screen.addStringToDisplay(event.right);
        if (com.seibel.distanthorizons.core.config.Config.Client.Advanced.Graphics.GenericRendering.showDistantEffectDiagnostics.get())
        {
            event.right.add(DistantSmokeRenderer.INSTANCE.diagnostic());
            event.right.add(com.seibel.distanthorizons.forge17.effects.TrailClient.INSTANCE.diagnostic());
            event.right.add(com.seibel.distanthorizons.forge17.effects.TrailClient.INSTANCE.deliveryDiagnostic());
        }
    }
	
	//endregion
	
	
	
}
