package com.seibel.distanthorizons.forge17.actors;

import com.seibel.distanthorizons.core.config.Config;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.client.Minecraft;

/** Client-thread owner of presentation frames. Network threads only enqueue immutable values. */
public final class PlayerClient {
    public static PlayerClient INSTANCE;
    private final ArrayBlockingQueue<Delivery> pending=new ArrayBlockingQueue<Delivery>(4);
    private final ArrayBlockingQueue<AppearanceDelivery> incomingAppearance=new ArrayBlockingQueue<>(16);
    private final PlayerFrameCache cache=new PlayerFrameCache();
    private Object connection,world;
    private UUID viewer;
    private int requested=-1,ticks,dimension=Integer.MIN_VALUE;
    private long token;
    public PlayerClient() {
        INSTANCE=this;
        PlayerRelay.INSTANCE.setReceiver((source,frame)-> {
            Delivery delivery=new Delivery(source,frame);
            if(!pending.offer(delivery)) { pending.poll();pending.offer(delivery); }
        });
        PlayerRelay.INSTANCE.setAppearanceReceiver((source,appearance)-> {
            AppearanceDelivery delivery=new AppearanceDelivery(source,appearance);
            if(!incomingAppearance.offer(delivery)) { incomingAppearance.poll();incomingAppearance.offer(delivery); }
        });
    }
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END) return;
        Minecraft mc=Minecraft.getMinecraft();ticks++;
        UUID currentViewer=mc.thePlayer==null?null:mc.thePlayer.getUniqueID();
        int range=mc.thePlayer!=null && Config.Client.Advanced.Graphics.GenericRendering.enablePlayerLodSupport.get()
            ?PlayerFrame.effectiveRange(Config.Client.Advanced.Graphics.GenericRendering.playerLodDistance.get(),PlayerFrame.MAX_RANGE,true):0;
        int currentDimension=mc.thePlayer==null?0:mc.thePlayer.dimension;
        if(connection!=mc.getNetHandler() || world!=mc.theWorld || !java.util.Objects.equals(viewer,currentViewer) || range!=requested || dimension!=currentDimension) {
            connection=mc.getNetHandler();world=mc.theWorld;viewer=currentViewer;requested=range;dimension=currentDimension;
            do { token=ThreadLocalRandom.current().nextLong(); } while(token==0);
            pending.clear();incomingAppearance.clear();cache.reset(connection,token,mc.thePlayer==null?0:mc.thePlayer.dimension,viewer);
            if(connection!=null) PlayerRelay.INSTANCE.subscribe(token,requested);
        }
        if(connection==null || world==null || viewer==null) { pending.clear();incomingAppearance.clear();return; }
        // Renew frequently enough that a rate-limited toggle request repairs promptly.
        if(ticks%20==0) PlayerRelay.INSTANCE.subscribe(token,requested);
        Delivery delivery;
        long now=System.nanoTime();
        while((delivery=pending.poll())!=null) if(requested>0 && delivery.frame.range<=requested) cache.accept(delivery.connection,delivery.frame,delivery.received);
        AppearanceDelivery appearance;
        while((appearance=incomingAppearance.poll())!=null) if(requested>0) cache.accept(appearance.connection,appearance.appearance);
        cache.expire(now);
    }
    public PlayerFrameCache frames() { return cache; }
    public String diagnostic() {
        return "DH player delivery: server="+PlayerRelay.INSTANCE.serverPresent()+" requested="+requested
            +" effective="+cache.range()+" poses="+cache.players().size()+" (model provider pending)";
    }
    private static final class AppearanceDelivery {
        final Object connection;final PlayerAppearance appearance;
        AppearanceDelivery(Object connection,PlayerAppearance appearance) { this.connection=connection;this.appearance=appearance; }
    }
    private static final class Delivery {
        final Object connection;final PlayerFrame frame;final long received;
        Delivery(Object connection,PlayerFrame frame) { this.connection=connection;this.frame=frame;this.received=System.nanoTime(); }
    }
}
