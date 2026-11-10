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
    private volatile BiConsumer<Object,PlayerAppearance> appearanceReceiver;
    private final Map<UUID,AppearanceState> appearances=new HashMap<>();
    private final Set<UUID> failedAppearances=new HashSet<>();
    private long appearanceRevision;
    private volatile boolean serverPresent;
    private boolean initialized;
    private long sequence;
    private boolean legacySafe;
    private static final byte[] NO_EXTENSION=new byte[0];
    private PlayerRelay() { }
    public void initialize() {
        if(initialized) return;initialized=true;
        legacySafe=FmurPlayerBridge.registerServer(this::ownsLegacy);
        NETWORK.registerMessage(SettingsHandler.class,Settings.class,0,Side.SERVER);
        NETWORK.registerMessage(FrameHandler.class,FrameMessage.class,1,Side.CLIENT);
        NETWORK.registerMessage(AppearanceHandler.class,AppearanceMessage.class,2,Side.CLIENT);
        FMLCommonHandler.instance().bus().register(this);
    }
    public void setReceiver(BiConsumer<Object,PlayerFrame> receiver) { this.receiver=receiver; }
    public void setAppearanceReceiver(BiConsumer<Object,PlayerAppearance> receiver) { appearanceReceiver=receiver; }
    public boolean serverPresent() { return serverPresent; }
    public void subscribe(long token,int range,boolean takeover) { if(serverPresent) NETWORK.sendToServer(new Settings(token,range,takeover)); }
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
            {
                Session current=sessions.get(p.getUniqueID());
                if(current!=null && current.player==p && current.settings.token==request.settings.token && current.settings.range==request.settings.range && current.settings.takeover==request.settings.takeover)
                    current.renewed=now;
                else sessions.put(p.getUniqueID(),new Session(p,request.settings,now));
            }
        }
        for(Iterator<Session> it=sessions.values().iterator();it.hasNext();) {
            Session s=it.next();
            if(s.player.playerNetServerHandler==null || !peers.containsKey(s.player.playerNetServerHandler.netManager)
                    || now-s.renewed>30000000000L) it.remove();
        }
        int interval=Math.max(1,Math.min(20,Config.Server.playerLodUpdateIntervalTicks.get()));
        if(sessions.isEmpty() || sequence%interval!=0) return;
        boolean fmur=Config.Server.allowFmurLodSupport.get();
        boolean anyRange=false;for(Session s:sessions.values()) anyRange|=s.settings.range>0;
        // Snapshot the existing player list once, not a loaded-entity scan per viewer.
        Map<net.minecraft.world.World,Map<Long,List<EntityPlayerMP>>> grid=new IdentityHashMap<>();
        Set<UUID> live=new HashSet<>();
        if(legacySafe && anyRange && Config.Server.allowPlayerLodSupport.get() && Config.Server.playerLodDistance.get()>0) for(Object value:server.getConfigurationManager().playerEntityList) {
            EntityPlayerMP target=(EntityPlayerMP)value;
            if(!validPosition(target) || !target.isEntityAlive() || !PlayerVisibility.allowed(target)) continue;
            live.add(target.getUniqueID());
            try {
                AppearanceState previous=appearances.get(target.getUniqueID());
                byte[] data=previous!=null && previous.player==target && previous.fmur==fmur && sequence%4!=0?previous.data:PlayerAppearance.capture(target,fmur);
                if(previous==null || previous.player!=target || previous.fmur!=fmur || !Arrays.equals(previous.data,data))
                    appearances.put(target.getUniqueID(),new AppearanceState(target,data,++appearanceRevision,fmur));
                failedAppearances.remove(target.getUniqueID());
            } catch(java.io.IOException | RuntimeException invalid) {
                appearances.remove(target.getUniqueID());
                if(failedAppearances.add(target.getUniqueID())) org.apache.logging.log4j.LogManager.getLogger("DistantHorizons")
                    .warn("DH player appearance withheld: invalid or oversized equipment/profile for "+target.getUniqueID(),invalid);
                continue;
            }
            Map<Long,List<EntityPlayerMP>> cells=grid.get(target.worldObj);
            if(cells==null) { cells=new HashMap<>();grid.put(target.worldObj,cells); }
            long key=cellKey(cell(target.posX),cell(target.posZ));
            List<EntityPlayerMP> members=cells.get(key);
            if(members==null) { members=new ArrayList<>();cells.put(key,members); }
            members.add(target);
        }
        appearances.keySet().retainAll(live);failedAppearances.retainAll(live);
        Map<UUID,byte[]> visualPoses=new HashMap<>();
        for(Session session:sessions.values()) {
            EntityPlayerMP viewer=session.player;
            int range=PlayerFrame.effectiveRange(session.settings.range,Config.Server.playerLodDistance.get(),legacySafe && Config.Server.allowPlayerLodSupport.get());
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
                        if(target==viewer || !PlayerVisibility.visibleTo(target,viewer) || target.getDistanceSqToEntity(viewer)>(double)range*range) continue;
                        if(selected.size()<PlayerFrame.MAX_PLAYERS) selected.add(target);
                        else if(nearest.compare(target,selected.peek())<0) { selected.poll();selected.add(target); }
                    }
                }
            }
            List<EntityPlayerMP> candidates=new ArrayList<>(selected);candidates.sort(nearest);
            List<PlayerFrame.Pose> poses=new ArrayList<PlayerFrame.Pose>();
            for(EntityPlayerMP p:candidates) {
                AppearanceState appearance=appearances.get(p.getUniqueID());
                if(appearance==null) continue;
                int flags=(p.isSneaking()?1:0)|(p.isSprinting()?2:0)|(p.isInvisible()?4:0)|(p.isBurning()?8:0)|(p.isRiding()?16:0)
                    |(p.isPlayerSleeping()?32:0)|(p.isUsingItem()?64:0)|(fmur?PlayerFrame.POSE_FMUR:0);
                try {
                    byte[] visual=visualPoses.get(p.getUniqueID());
                    if(visual==null) { visual=fmur?FmurPlayerBridge.capturePose(p):NO_EXTENSION;visualPoses.put(p.getUniqueID(),visual); }
                    poses.add(new PlayerFrame.Pose(p.getUniqueID(),p.getEntityId(),p.posX,p.posY,p.posZ,p.rotationYaw,
                        p.rotationPitch,p.rotationYawHead,p.renderYawOffset,p.swingProgress,flags,p.ticksExisted,light(p),appearance.revision,Math.max(0,Math.min(1000000,((com.seibel.distanthorizons.forge17.mixin.MixinPlayerUseState)p).dh$getItemInUseCount())),bedYaw(p),visual));
                } catch(IllegalArgumentException invalid) { continue; }
                if(poses.size()==PlayerFrame.MAX_PLAYERS) break;
            }
            try {
                NETWORK.sendTo(new FrameMessage(new PlayerFrame(session.settings.token,sequence,viewer.getUniqueID(),viewer.dimension,range,(legacySafe?PlayerFrame.SAFE:0)|(legacySafe && session.settings.takeover?PlayerFrame.OWNED:0)|(fmur?PlayerFrame.FMUR:0),interval,poses)),viewer);
                Set<UUID> retained=new HashSet<>();int bytes=32768,count=0;
                for(PlayerFrame.Pose pose:poses) retained.add(pose.uuid);
                List<PlayerFrame.Pose> order=AppearanceDeliveryOrder.ordered(poses,session.known,session.sent);
                for(PlayerFrame.Pose pose:order) {
                    AppearanceState appearance=appearances.get(pose.uuid);
                    Long known=session.known.get(pose.uuid),sent=session.sent.get(pose.uuid);
                    if(known!=null && known==appearance.revision && sent!=null && sequence-sent<20) continue;
                    PlayerAppearance message=new PlayerAppearance(session.settings.token,viewer.getUniqueID(),viewer.dimension,
                        pose.uuid,pose.entityId,appearance.revision,appearance.data);
                    if(count>=4 || message.bytes()>bytes) continue;
                    NETWORK.sendTo(new AppearanceMessage(message),viewer);bytes-=message.bytes();count++;
                    session.known.put(pose.uuid,appearance.revision);session.sent.put(pose.uuid,sequence);
                }
                session.known.keySet().retainAll(retained);session.sent.keySet().retainAll(retained);
            } catch(RuntimeException closed) {
                peers.remove(viewer.playerNetServerHandler.netManager);
                org.apache.logging.log4j.LogManager.getLogger("DistantHorizons").warn("DH player pose delivery stopped for closed peer",closed);
            }
        }
    }
    private boolean ownsLegacy(EntityPlayerMP player) {
        Session session=player==null?null:sessions.get(player.getUniqueID());
        return legacySafe && session!=null && session.player==player && session.settings.takeover
            && player.playerNetServerHandler!=null && peers.containsKey(player.playerNetServerHandler.netManager)
            && System.nanoTime()-session.renewed<=30000000000L;
    }
    private static float bedYaw(EntityPlayerMP player) {
        if(!player.isPlayerSleeping() || player.playerLocation==null) return 0;
        net.minecraft.util.ChunkCoordinates bed=player.playerLocation;
        if(bed.posY<0 || bed.posY>255 || !player.worldObj.getChunkProvider().chunkExists(bed.posX>>4,bed.posZ>>4)) return 0;
        int direction=player.worldObj.getChunkFromChunkCoords(bed.posX>>4,bed.posZ>>4).getBlockMetadata(bed.posX & 15,bed.posY,bed.posZ & 15) & 3;
        return direction==0?90:direction==2?270:direction==3?180:0;
    }
    private static int light(EntityPlayerMP player) {
        int x=(int)Math.floor(player.posX),z=(int)Math.floor(player.posZ),y=Math.max(0,Math.min(255,(int)Math.floor(player.posY+1)));
        // On the owning world thread, chunkExists precedes access; never request an absent chunk.
        if(!player.worldObj.getChunkProvider().chunkExists(x>>4,z>>4)) return 0;
        net.minecraft.world.chunk.Chunk chunk=player.worldObj.getChunkFromChunkCoords(x>>4,z>>4);
        int sky=chunk.getSavedLightValue(net.minecraft.world.EnumSkyBlock.Sky,x & 15,y,z & 15);
        int block=chunk.getSavedLightValue(net.minecraft.world.EnumSkyBlock.Block,x & 15,y,z & 15);
        return (sky<<20)|(block<<4);
    }
    private static boolean validPosition(EntityPlayerMP player) {
        return Double.isFinite(player.posX) && Double.isFinite(player.posY) && Double.isFinite(player.posZ)
            && Math.abs(player.posX)<=30000000 && Math.abs(player.posZ)<=30000000 && Math.abs(player.posY)<=65536;
    }
    private static int cell(double coordinate) { return (int)Math.floor(coordinate/256.0); }
    private static long cellKey(int x,int z) { return ((long)x<<32) ^ (z & 0xffffffffL); }
    public void clearServer() { sessions.clear();pending.clear();peers.clear();appearances.clear();failedAppearances.clear();sequence=appearanceRevision=0; }
    private static final class Session {
        final EntityPlayerMP player;final Settings settings;long renewed;
        final Map<UUID,Long> known=new HashMap<>(),sent=new HashMap<>();
        Session(EntityPlayerMP player,Settings settings,long renewed) { this.player=player;this.settings=settings;this.renewed=renewed; }
    }
    private static final class AppearanceState {
        final EntityPlayerMP player;final byte[] data;final long revision;final boolean fmur;
        AppearanceState(EntityPlayerMP player,byte[] data,long revision,boolean fmur) { this.player=player;this.data=data;this.revision=revision;this.fmur=fmur; }
    }
    private static final class Request {
        final EntityPlayerMP player;final Settings settings;
        Request(EntityPlayerMP player,Settings settings) { this.player=player;this.settings=settings; }
    }
    public static final class Settings implements IMessage {
        long token;int range;boolean valid,takeover;
        public Settings() { }
        Settings(long token,int range,boolean takeover) { this.token=token;this.range=range;this.takeover=takeover; }
        public void toBytes(ByteBuf out) { out.writeByte(PlayerFrame.VERSION).writeLong(token).writeShort(range).writeByte(takeover?1:0); }
        public void fromBytes(ByteBuf in) {
            valid=false;
            if(in.readableBytes()!=12) return;
            int version=in.readUnsignedByte();token=in.readLong();range=in.readUnsignedShort();int flags=in.readUnsignedByte();takeover=flags==1;
            valid=flags<=1 && version==PlayerFrame.VERSION && token!=0 && range<=PlayerFrame.MAX_RANGE;
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
    public static final class AppearanceMessage implements IMessage {
        PlayerAppearance appearance;
        public AppearanceMessage() { }
        AppearanceMessage(PlayerAppearance appearance) { this.appearance=appearance; }
        public void toBytes(ByteBuf out) { appearance.write(out); }
        public void fromBytes(ByteBuf in) { try { appearance=PlayerAppearance.read(in); } catch(RuntimeException invalid) { appearance=null; } }
    }
    public static final class AppearanceHandler implements IMessageHandler<AppearanceMessage,IMessage> {
        public IMessage onMessage(AppearanceMessage message,MessageContext context) {
            BiConsumer<Object,PlayerAppearance> receiver=INSTANCE.appearanceReceiver;
            if(message.appearance!=null && receiver!=null) receiver.accept(context.netHandler,message.appearance);
            return null;
        }
    }
    public static final class FrameHandler implements IMessageHandler<FrameMessage,IMessage> {
        public IMessage onMessage(FrameMessage message,MessageContext context) {
            BiConsumer<Object,PlayerFrame> receiver=INSTANCE.receiver;
            if(message.frame!=null && receiver!=null) receiver.accept(context.netHandler,message.frame);
            return null;
        }
    }
}
