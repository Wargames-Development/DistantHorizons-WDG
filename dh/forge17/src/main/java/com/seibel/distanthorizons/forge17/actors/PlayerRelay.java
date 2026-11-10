package com.seibel.distanthorizons.forge17.actors;

import com.seibel.distanthorizons.core.config.Config;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.*;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.NetworkManager;
import net.minecraft.server.MinecraftServer;

/** Server-owned cosmetic player poses. Never loads chunks or changes entity tracking. */
public final class PlayerRelay {
    public static final PlayerRelay INSTANCE=new PlayerRelay();
    public static final String CHANNEL="DHPlayerLods";
    private static final SimpleNetworkWrapper NETWORK=NetworkRegistry.INSTANCE.newSimpleChannel(CHANNEL);
    private final ConcurrentHashMap<NetworkManager,Long> peers=new ConcurrentHashMap<NetworkManager,Long>();
    private final ArrayBlockingQueue<Request> pending=new ArrayBlockingQueue<Request>(64);
    private final Map<UUID,Session> sessions=new HashMap<UUID,Session>();
    private volatile BiConsumer<Object,PlayerFrame> receiver;
    private volatile boolean serverPresent;
    private boolean initialized;
    private long sequence;
    private PlayerRelay() { }
    public void initialize() {
        if(initialized) return;initialized=true;
        NETWORK.registerMessage(SettingsHandler.class,Settings.class,0,Side.SERVER);
        NETWORK.registerMessage(FrameHandler.class,FrameMessage.class,1,Side.CLIENT);
        FMLCommonHandler.instance().bus().register(this);
    }
    public void setReceiver(BiConsumer<Object,PlayerFrame> receiver) { this.receiver=receiver; }
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
    @SubscribeEvent public void tick(TickEvent.ServerTickEvent event) {
        if(event.phase!=TickEvent.Phase.END) return;
        MinecraftServer server=MinecraftServer.getServer();if(server==null) return;
        long now=System.nanoTime();sequence++;
        Request request;
        for(int i=0;i<64 && (request=pending.poll())!=null;i++) {
            EntityPlayerMP p=request.player;
            if(p.playerNetServerHandler==null || !peers.containsKey(p.playerNetServerHandler.netManager)) continue;
            if(request.settings.range==0) sessions.remove(p.getUniqueID());
            else sessions.put(p.getUniqueID(),new Session(p,request.settings,now));
        }
        for(Iterator<Session> it=sessions.values().iterator();it.hasNext();) {
            Session s=it.next();
            if(s.player.playerNetServerHandler==null || !peers.containsKey(s.player.playerNetServerHandler.netManager)
                    || now-s.renewed>30000000000L) it.remove();
        }
        if(sessions.isEmpty() || sequence%4!=0) return;
        // Snapshot the existing player list once, not a loaded-entity scan per viewer.
        Map<net.minecraft.world.World,Map<Long,List<EntityPlayerMP>>> grid=new IdentityHashMap<>();
        if(Config.Server.allowPlayerLodSupport.get() && Config.Server.playerLodDistance.get()>0) for(Object value:server.getConfigurationManager().playerEntityList) {
            EntityPlayerMP target=(EntityPlayerMP)value;
            if(!validPosition(target) || !target.isEntityAlive() || target.isInvisible() || !PlayerVisibility.allowed(target)) continue;
            Map<Long,List<EntityPlayerMP>> cells=grid.get(target.worldObj);
            if(cells==null) { cells=new HashMap<>();grid.put(target.worldObj,cells); }
            long key=cellKey(cell(target.posX),cell(target.posZ));
            List<EntityPlayerMP> members=cells.get(key);
            if(members==null) { members=new ArrayList<>();cells.put(key,members); }
            members.add(target);
        }
        for(Session session:sessions.values()) {
            EntityPlayerMP viewer=session.player;
            int range=PlayerFrame.effectiveRange(session.settings.range,Config.Server.playerLodDistance.get(),Config.Server.allowPlayerLodSupport.get());
            Comparator<EntityPlayerMP> nearest=(a,b)->Double.compare(a.getDistanceSqToEntity(viewer),b.getDistanceSqToEntity(viewer));
            PriorityQueue<EntityPlayerMP> selected=new PriorityQueue<>(PlayerFrame.MAX_PLAYERS,nearest.reversed());
            Map<Long,List<EntityPlayerMP>> cells=grid.get(viewer.worldObj);
            if(range>0 && validPosition(viewer) && viewer.isEntityAlive() && cells!=null) {
                int minX=cell(viewer.posX-range),maxX=cell(viewer.posX+range);
                int minZ=cell(viewer.posZ-range),maxZ=cell(viewer.posZ+range);
                for(int x=minX;x<=maxX;x++) for(int z=minZ;z<=maxZ;z++) {
                    List<EntityPlayerMP> members=cells.get(cellKey(x,z));
                    if(members==null) continue;
                    for(EntityPlayerMP target:members) {
                        if(target==viewer || target.getDistanceSqToEntity(viewer)>(double)range*range) continue;
                        if(selected.size()<PlayerFrame.MAX_PLAYERS) selected.add(target);
                        else if(nearest.compare(target,selected.peek())<0) { selected.poll();selected.add(target); }
                    }
                }
            }
            List<EntityPlayerMP> candidates=new ArrayList<>(selected);candidates.sort(nearest);
            List<PlayerFrame.Pose> poses=new ArrayList<PlayerFrame.Pose>();
            for(EntityPlayerMP p:candidates) {
                int flags=(p.isSneaking()?1:0)|(p.isSprinting()?2:0)|(p.isBurning()?8:0)|(p.isRiding()?16:0)
                    |(p.isPlayerSleeping()?32:0)|(p.isUsingItem()?64:0);
                try {
                    // Light access is intentionally absent: it may load a chunk for an out-of-world entity.
                    poses.add(new PlayerFrame.Pose(p.getUniqueID(),p.getEntityId(),p.posX,p.posY,p.posZ,p.rotationYaw,
                        p.rotationPitch,p.rotationYawHead,p.renderYawOffset,p.swingProgress,flags,p.ticksExisted,0));
                } catch(IllegalArgumentException invalid) { continue; }
                if(poses.size()==PlayerFrame.MAX_PLAYERS) break;
            }
            try {
                NETWORK.sendTo(new FrameMessage(new PlayerFrame(session.settings.token,sequence,viewer.getUniqueID(),viewer.dimension,range,poses)),viewer);
            } catch(RuntimeException closed) {
                peers.remove(viewer.playerNetServerHandler.netManager);
                org.apache.logging.log4j.LogManager.getLogger("DistantHorizons").warn("DH player pose delivery stopped for closed peer",closed);
            }
        }
    }
    private static boolean validPosition(EntityPlayerMP player) {
        return Double.isFinite(player.posX) && Double.isFinite(player.posY) && Double.isFinite(player.posZ)
            && Math.abs(player.posX)<=30000000 && Math.abs(player.posZ)<=30000000 && Math.abs(player.posY)<=65536;
    }
    private static int cell(double coordinate) { return (int)Math.floor(coordinate/256.0); }
    private static long cellKey(int x,int z) { return ((long)x<<32) ^ (z & 0xffffffffL); }
    public void clearServer() { sessions.clear();pending.clear();peers.clear();sequence=0; }
    private static final class Session {
        final EntityPlayerMP player;final Settings settings;final long renewed;
        Session(EntityPlayerMP player,Settings settings,long renewed) { this.player=player;this.settings=settings;this.renewed=renewed; }
    }
    private static final class Request {
        final EntityPlayerMP player;final Settings settings;
        Request(EntityPlayerMP player,Settings settings) { this.player=player;this.settings=settings; }
    }
    public static final class Settings implements IMessage {
        long token;int range;boolean valid;
        public Settings() { }
        Settings(long token,int range) { this.token=token;this.range=range; }
        public void toBytes(ByteBuf out) { out.writeByte(PlayerFrame.VERSION).writeLong(token).writeShort(range); }
        public void fromBytes(ByteBuf in) {
            valid=false;
            if(in.readableBytes()!=11) return;
            int version=in.readUnsignedByte();token=in.readLong();range=in.readUnsignedShort();
            valid=version==PlayerFrame.VERSION && token!=0 && range<=PlayerFrame.MAX_RANGE;
        }
    }
    public static final class SettingsHandler implements IMessageHandler<Settings,IMessage> {
        public IMessage onMessage(Settings settings,MessageContext context) {
            EntityPlayerMP p=context.getServerHandler().playerEntity;
            NetworkManager manager=p.playerNetServerHandler.netManager;
            Long previous=INSTANCE.peers.get(manager);long now=System.nanoTime();
            if(settings.valid && previous!=null && now-previous>=250000000L && INSTANCE.peers.replace(manager,previous,now))
                INSTANCE.pending.offer(new Request(p,settings));
            return null;
        }
    }
    public static final class FrameMessage implements IMessage {
        PlayerFrame frame;
        public FrameMessage() { }
        FrameMessage(PlayerFrame frame) { this.frame=frame; }
        public void toBytes(ByteBuf out) { frame.write(out); }
        public void fromBytes(ByteBuf in) { try { frame=PlayerFrame.read(in); } catch(RuntimeException invalid) { frame=null; } }
    }
    public static final class FrameHandler implements IMessageHandler<FrameMessage,IMessage> {
        public IMessage onMessage(FrameMessage message,MessageContext context) {
            BiConsumer<Object,PlayerFrame> receiver=INSTANCE.receiver;
            if(message.frame!=null && receiver!=null) receiver.accept(context.netHandler,message.frame);
            return null;
        }
    }
}
