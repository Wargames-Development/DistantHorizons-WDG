package com.seibel.distanthorizons.forge17.actors;

import com.seibel.distanthorizons.core.config.Config;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.*;
import cpw.mods.fml.common.network.simpleimpl.*;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BiConsumer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.NetworkManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;

/** Bounded server-authoritative cosmetic vehicle channel. Radar and entity tracking remain independent. */
public final class VehicleRelay {
    public static final VehicleRelay INSTANCE=new VehicleRelay();
    public static final String CHANNEL="DHVehicleLods";
    private static final SimpleNetworkWrapper NETWORK=NetworkRegistry.INSTANCE.newSimpleChannel(CHANNEL);
    private final ConcurrentHashMap<NetworkManager,Long> peers=new ConcurrentHashMap<>();
    private final ArrayBlockingQueue<Request> requests=new ArrayBlockingQueue<>(64);
    private final Map<UUID,Session> sessions=new HashMap<>();
    private volatile BiConsumer<Object,VehicleFrame> receiver;
    private volatile boolean serverPresent;
    private boolean initialized;
    private long sequence;
    private VehicleRelay() { }
    public void initialize() {
        if(initialized) return;initialized=true;
        NETWORK.registerMessage(SettingsHandler.class,Settings.class,0,Side.SERVER);
        NETWORK.registerMessage(FrameHandler.class,FrameMessage.class,1,Side.CLIENT);
        FMLCommonHandler.instance().bus().register(this);
    }
    public void setReceiver(BiConsumer<Object,VehicleFrame> receiver) { this.receiver=receiver; }
    public boolean serverPresent() { return serverPresent; }
    public void subscribe(long token,int range) { if(serverPresent) NETWORK.sendToServer(new Settings(token,range)); }
    @SubscribeEvent public void registered(FMLNetworkEvent.CustomPacketRegistrationEvent<?> event) {
        if(!event.registrations.contains(CHANNEL)) return;
        boolean present="REGISTER".equals(event.operation);
        if(event.side==Side.SERVER) { if(present) peers.put(event.manager,0L);else peers.remove(event.manager); }
        else serverPresent=present;
    }
    @SubscribeEvent public void disconnected(FMLNetworkEvent.ServerDisconnectionFromClientEvent event) { peers.remove(event.manager); }
    @SubscribeEvent public void clientDisconnected(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) { serverPresent=false; }
    public void clearServer() { requests.clear();sessions.clear();peers.clear();sequence=0; }
    @SubscribeEvent public void tick(TickEvent.ServerTickEvent event) {
        if(event.phase!=TickEvent.Phase.END) return;
        MinecraftServer server=MinecraftServer.getServer();if(server==null) return;
        sequence++;long now=System.nanoTime();Request request;
        for(int i=0;i<64 && (request=requests.poll())!=null;i++) {
            EntityPlayerMP player=request.player;
            if(player.playerNetServerHandler==null || !peers.containsKey(player.playerNetServerHandler.netManager)) continue;
            Session previous=sessions.get(player.getUniqueID());
            if(previous!=null && previous.player==player && previous.settings.token==request.settings.token && previous.settings.range==request.settings.range) previous.renewed=now;
            else sessions.put(player.getUniqueID(),new Session(player,request.settings,now));
        }
        for(Iterator<Session> it=sessions.values().iterator();it.hasNext();) {
            Session session=it.next();
            if(now-session.renewed>30000000000L || session.player.playerNetServerHandler==null
                    || !peers.containsKey(session.player.playerNetServerHandler.netManager)) it.remove();
        }
        if(sessions.isEmpty()) return;
        int interval=Math.max(1,Math.min(20,Config.Server.vehicleLodUpdateIntervalTicks.get()));
        if(sequence%interval!=0) return;
        boolean any=false;for(Session session:sessions.values()) any|=session.settings.range>0;
        if(!any && sequence%20!=0) return;
        boolean source=McheliSource.available();
        boolean allowed=source && Config.Server.allowMcheliLodSupport.get();
        int maximum=Config.Server.mcheliVehicleLodDistance.get();
        Map<World,Map<Long,List<Entity>>> grid=new IdentityHashMap<>();
        // One existing-entity traversal per world/update, only while delivery is requested and allowed.
        if(any && allowed && maximum>0) for(World world:server.worldServers) {
            if(world==null) continue;
            Map<Long,List<Entity>> cells=new HashMap<>();grid.put(world,cells);
            for(Object value:world.loadedEntityList) {
                Entity entity=(Entity)value;if(!McheliSource.supported(entity) || !position(entity)) continue;
                long key=key(cell(entity.posX),cell(entity.posZ));List<Entity> entries=cells.get(key);
                if(entries==null) { entries=new ArrayList<>();cells.put(key,entries); }entries.add(entity);
            }
        }
        // Expensive optional capture is shared across recipients and only done for selected candidates.
        Map<Entity,Optional<VehicleFrame.Entry>> captured=new IdentityHashMap<>();
        for(Session session:sessions.values()) {
            EntityPlayerMP player=session.player;
            int range=VehicleFrame.effectiveRange(session.settings.range,maximum,allowed);
            Comparator<Entity> nearest=(a,b)->Double.compare(a.getDistanceSqToEntity(player),b.getDistanceSqToEntity(player));
            PriorityQueue<Entity> selected=new PriorityQueue<>(VehicleFrame.MAX_VEHICLES,nearest.reversed());
            Map<Long,List<Entity>> cells=grid.get(player.worldObj);
            if(range>0 && player.isEntityAlive() && position(player) && cells!=null) {
                for(int x=cell(player.posX-range);x<=cell(player.posX+range);x++) for(int z=cell(player.posZ-range);z<=cell(player.posZ+range);z++) {
                    List<Entity> entries=cells.get(key(x,z));if(entries==null) continue;
                    for(Entity entity:entries) {
                        if(entity.getDistanceSqToEntity(player)>(double)range*range) continue;
                        if(selected.size()<VehicleFrame.MAX_VEHICLES) selected.add(entity);
                        else if(nearest.compare(entity,selected.peek())<0) { selected.poll();selected.add(entity); }
                    }
                }
            }
            List<Entity> ordered=new ArrayList<>(selected);ordered.sort(nearest);
            List<VehicleFrame.Entry> entries=new ArrayList<>();
            for(Entity entity:ordered) {
                Optional<VehicleFrame.Entry> snapshot=captured.get(entity);
                if(snapshot==null) { snapshot=Optional.ofNullable(McheliSource.capture(entity));captured.put(entity,snapshot); }
                if(snapshot.isPresent()) entries.add(snapshot.get());
            }
            try { NETWORK.sendTo(new FrameMessage(new VehicleFrame(session.settings.token,sequence,player.getUniqueID(),player.dimension,range,interval,source,entries)),player); }
            catch(RuntimeException closedPeer) { /* disconnect registration removes the session on the next tick */ }
        }
    }
    private static boolean position(Entity entity) {
        return Double.isFinite(entity.posX+entity.posY+entity.posZ) && Math.abs(entity.posX)<=30000000 && Math.abs(entity.posZ)<=30000000 && Math.abs(entity.posY)<=65536;
    }
    private static int cell(double coordinate) { return (int)Math.floor(coordinate/256); }
    private static long key(int x,int z) { return ((long)x<<32)^(z&0xffffffffL); }
    private static final class Request {
        final EntityPlayerMP player;final Settings settings;
        Request(EntityPlayerMP player,Settings settings) { this.player=player;this.settings=settings; }
    }
    private static final class Session {
        final EntityPlayerMP player;final Settings settings;long renewed;
        Session(EntityPlayerMP player,Settings settings,long renewed) { this.player=player;this.settings=settings;this.renewed=renewed; }
    }
    public static final class Settings implements IMessage {
        long token;int range;boolean valid;
        public Settings() { }
        Settings(long token,int range) { this.token=token;this.range=range; }
        public void toBytes(ByteBuf out) { out.writeByte(VehicleFrame.VERSION).writeLong(token).writeShort(range); }
        public void fromBytes(ByteBuf in) {
            valid=false;if(in.readableBytes()!=11) return;
            int version=in.readUnsignedByte();token=in.readLong();range=in.readUnsignedShort();
            valid=version==VehicleFrame.VERSION && token!=0 && range<=VehicleFrame.MAX_RANGE;
        }
    }
    public static final class SettingsHandler implements IMessageHandler<Settings,IMessage> {
        public IMessage onMessage(Settings settings,MessageContext context) {
            EntityPlayerMP player=context.getServerHandler().playerEntity;
            NetworkManager manager=player.playerNetServerHandler.netManager;Long previous=INSTANCE.peers.get(manager);long now=System.nanoTime();
            if(settings.valid && previous!=null && now-previous>=250000000L && INSTANCE.peers.replace(manager,previous,now)) INSTANCE.requests.offer(new Request(player,settings));
            return null;
        }
    }
    public static final class FrameMessage implements IMessage {
        VehicleFrame frame;
        public FrameMessage() { }
        FrameMessage(VehicleFrame frame) { this.frame=frame; }
        public void toBytes(ByteBuf out) { frame.write(out); }
        public void fromBytes(ByteBuf in) { try { frame=VehicleFrame.read(in); }catch(RuntimeException invalid) { frame=null; } }
    }
    public static final class FrameHandler implements IMessageHandler<FrameMessage,IMessage> {
        public IMessage onMessage(FrameMessage message,MessageContext context) {
            BiConsumer<Object,VehicleFrame> receiver=INSTANCE.receiver;
            if(receiver!=null && message.frame!=null) receiver.accept(context.netHandler,message.frame);return null;
        }
    }
}
