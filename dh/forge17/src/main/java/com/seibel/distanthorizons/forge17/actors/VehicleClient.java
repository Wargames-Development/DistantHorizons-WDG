package com.seibel.distanthorizons.forge17.actors;

import com.seibel.distanthorizons.core.config.Config;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.client.Minecraft;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadLocalRandom;

/** Render provider must explicitly activate delivery. Until then only zero-range capability probes are sent. */
public final class VehicleClient {
    public static VehicleClient INSTANCE;
    private final ArrayBlockingQueue<Delivery> pending=new ArrayBlockingQueue<>(4);
    private final VehicleFrameCache cache=new VehicleFrameCache();
    private Object connection,world;
    private UUID viewer;
    private int dimension,requested=-1,ticks;
    private long token;
    private boolean ready;
    public VehicleClient() {
        INSTANCE=this;
        VehicleRelay.INSTANCE.setReceiver((source,frame)-> {
            Delivery delivery=new Delivery(source,frame);
            if(!pending.offer(delivery)) { pending.poll();pending.offer(delivery); }
        });
    }
    public void activateDelivery() { ready=true; }
    public VehicleFrameCache frames() { return cache; }
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END) return;
        Minecraft mc=Minecraft.getMinecraft();ticks++;
        UUID id=mc.thePlayer==null?null:mc.thePlayer.getUniqueID();int dim=mc.thePlayer==null?0:mc.thePlayer.dimension;
        int range=ready && Config.Client.Advanced.Graphics.GenericRendering.extendMcheliTrails.get()
            ?VehicleFrame.effectiveRange(Config.Client.Advanced.Graphics.GenericRendering.mcheliVehicleLodDistance.get(),VehicleFrame.MAX_RANGE,true):0;
        if(connection!=mc.getNetHandler() || world!=mc.theWorld || !java.util.Objects.equals(viewer,id) || dimension!=dim || requested!=range) {
            connection=mc.getNetHandler();world=mc.theWorld;viewer=id;dimension=dim;requested=range;
            do { token=ThreadLocalRandom.current().nextLong(); }while(token==0);
            pending.clear();cache.reset(connection,token,viewer,dimension);
            if(connection!=null) VehicleRelay.INSTANCE.subscribe(token,range);
        }
        if(connection==null || world==null || viewer==null) { pending.clear();return; }
        if(ticks%20==0) VehicleRelay.INSTANCE.subscribe(token,range);
        Delivery delivery;while((delivery=pending.poll())!=null) if(delivery.frame.range<=range) cache.accept(delivery.connection,delivery.frame,delivery.receipt);
        cache.expire(System.nanoTime());
    }
    public String diagnostic() {
        VehicleFrame frame=cache.frame();
        return "DH MCHELI delivery: server="+VehicleRelay.INSTANCE.serverPresent()+" requested="+requested+" effective="+(frame==null?0:frame.range)
            +" vehicles="+(frame==null?0:frame.entries.size())+" source="+(frame!=null && frame.sourceReady)+" provider="+(ready?"delivery active":"pending (legacy retained)");
    }
    private static final class Delivery {
        final Object connection;final VehicleFrame frame;final long receipt=System.nanoTime();
        Delivery(Object connection,VehicleFrame frame) { this.connection=connection;this.frame=frame; }
    }
}
