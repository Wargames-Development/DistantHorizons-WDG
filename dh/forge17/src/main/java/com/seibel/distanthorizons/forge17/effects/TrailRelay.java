package com.seibel.distanthorizons.forge17.effects;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import com.seibel.distanthorizons.core.config.Config;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.*;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.NetworkManager;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;

/** DH-owned opt-in visual channel. Never requests chunks or changes gameplay entity tracking. */
public final class TrailRelay
{
    public static final TrailRelay INSTANCE=new TrailRelay();
    public static final String CHANNEL="DHVisualTrails";
    public static final int VERSION=1, MAX_BATCH=32;
    private static final SimpleNetworkWrapper NETWORK=NetworkRegistry.INSTANCE.newSimpleChannel(CHANNEL);
    private final ConcurrentHashMap<NetworkManager, Boolean> peers=new ConcurrentHashMap<NetworkManager, Boolean>();
    private final ArrayBlockingQueue<Request> requests=new ArrayBlockingQueue<Request>(64);
    private final Map<UUID, Session> sessions=new HashMap<UUID, Session>();
    private final Map<World, LinkedHashMap<String, Stream>> worlds=new HashMap<World, LinkedHashMap<String, Stream>>();
    private final Map<World,List<TrailSample>> blasts=new HashMap<World,List<TrailSample>>();
    private volatile boolean subscribed, serverPresent;
    private volatile Consumer<Batch> clientReceiver;
    private boolean initialized;
    private final boolean[] reportedFamily=new boolean[4];

    private TrailRelay() { }
    public void initialize()
    {
        if (initialized) return;
        initialized=true;
        NETWORK.registerMessage(SettingsHandler.class, Settings.class, 0, Side.SERVER);
        NETWORK.registerMessage(BatchHandler.class, Batch.class, 1, Side.CLIENT);
        FMLCommonHandler.instance().bus().register(this);
        MinecraftForge.EVENT_BUS.register(this);
    }
    public boolean hasSubscribers() { return subscribed && Config.Server.enableDistantTrailRelay.get(); }
    public boolean serverPresent() { return serverPresent; }
    public void setClientReceiver(Consumer<Batch> receiver) { clientReceiver=receiver; }
    public void subscribe(int flags, int distance)
    {
        if (serverPresent) NETWORK.sendToServer(new Settings(flags, distance));
    }

    @SubscribeEvent
    public void registered(FMLNetworkEvent.CustomPacketRegistrationEvent<?> event)
    {
        if (!event.registrations.contains(CHANNEL)) return;
        boolean register="REGISTER".equals(event.operation);
        if (event.side == Side.SERVER)
        {
            if (register) peers.put(event.manager, Boolean.TRUE); else peers.remove(event.manager);
        }
        else serverPresent=register;
    }
    @SubscribeEvent
    public void disconnected(FMLNetworkEvent.ServerDisconnectionFromClientEvent event) { peers.remove(event.manager); }
    @SubscribeEvent
    public void clientDisconnected(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) { serverPresent=false; }

    public void offer(Entity entity, TrailSample sample)
    {
        if (!hasSubscribers() || entity == null || entity.worldObj == null || entity.worldObj.isRemote || entity.isDead) return;
        sample.uuid=entity.getUniqueID(); sample.entityId=entity.getEntityId(); sample.tick=entity.worldObj.getTotalWorldTime();
        sample.x=entity.posX+sample.ox; sample.y=entity.posY+sample.oy; sample.z=entity.posZ+sample.oz;
        sample.x0=entity.prevPosX+sample.ox; sample.y0=entity.prevPosY+sample.oy; sample.z0=entity.prevPosZ+sample.oz;
        double step=(sample.x-sample.x0)*(sample.x-sample.x0)+(sample.y-sample.y0)*(sample.y-sample.y0)+(sample.z-sample.z0)*(sample.z-sample.z0);
        if(step>256D*256D) { sample.x0=sample.x;sample.y0=sample.y;sample.z0=sample.z; }
        if (!sample.valid()) return;
        if(sample.style.equals("dhBlast")) {
            List<TrailSample> list=blasts.get(entity.worldObj);
            if(list==null) { list=new ArrayList<TrailSample>();blasts.put(entity.worldObj,list); }
            if(list.size()<32) list.add(sample);
            return;
        }
        if(!reportedFamily[sample.family])
        {
            reportedFamily[sample.family]=true;
            org.apache.logging.log4j.LogManager.getLogger("DistantHorizons").info("DH trail source active: family="+sample.family+", class="+entity.getClass().getName()+", style="+sample.style);
        }
        LinkedHashMap<String, Stream> streams=worlds.get(entity.worldObj);
        if (streams == null) { streams=new LinkedHashMap<String, Stream>(); worlds.put(entity.worldObj, streams); }
        String key=sample.uuid+":"+sample.slot;
        Stream stream=streams.get(key);
        if (stream == null)
        {
            if (streams.size() >= 1024) return;
            Stream created=new Stream(entity,sample);
            streams.put(key,created);
            traceHbm(created,"start",sample.tick);
        }
        else if(stream.entity!=entity) streams.put(key,new Stream(entity,sample));
        else
        {
            if (!TrailRules.continuous(stream.latest.tick,stream.latest.style,sample.tick,sample.style)
                || (sample.x-stream.latest.x)*(sample.x-stream.latest.x)+(sample.y-stream.latest.y)*(sample.y-stream.latest.y)+(sample.z-stream.latest.z)*(sample.z-stream.latest.z)>256D*256D)
            {
                stream.startX=sample.x0; stream.startY=sample.y0; stream.startZ=sample.z0;
            }
            stream.latest=sample;
        }
    }

    public void explosion(World world,double x,double y,double z,int clouds,float scale,float speed,float wave,float nativeRange) {
        if(world.isRemote || !hasSubscribers()) return;
        TrailSample sample=new TrailSample();sample.family=TrailSample.HBM;sample.style="dhNativeBlast";
        sample.uuid=UUID.randomUUID();sample.entityId=-1;sample.tick=world.getTotalWorldTime();sample.count=Math.max(1,Math.min(32,clouds));
        sample.x0=sample.x=x;sample.y0=sample.y=y;sample.z0=sample.z=z;sample.scale=scale;
        sample.mx=speed;sample.my=wave/4F;sample.mz=nativeRange/1024F;
        if(!sample.valid()) return;
        List<TrailSample> list=blasts.get(world);
        if(list==null) { list=new ArrayList<TrailSample>();blasts.put(world,list); }
        if(list.size()<32) list.add(sample);
    }

    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.START) return;
        Request request;
        for (int i=0; i<64 && (request=requests.poll()) != null; i++)
        {
            EntityPlayerMP p=request.player;
            if (p.playerNetServerHandler == null || !peers.containsKey(p.playerNetServerHandler.netManager)) continue;
            Session old=sessions.get(p.getUniqueID());
            long now=System.nanoTime();
            if (old != null && request.settings.flags!=0 && now-old.changedAt < 500000000L) continue;
            if (request.settings.flags == 0) sessions.remove(p.getUniqueID());
            else if(Config.Server.enableDistantTrailRelay.get())
            {
                sessions.put(p.getUniqueID(),new Session(p,request.settings,now));
                send(new Batch(p.dimension,new ArrayList<TrailSample>()),p);
            }
        }
        for (Iterator<Session> i=sessions.values().iterator();i.hasNext();)
        {
            Session s=i.next();
            if (s.player.playerNetServerHandler == null || !peers.containsKey(s.player.playerNetServerHandler.netManager)
                || s.player.isDead || System.nanoTime()-s.changedAt > 30000000000L) i.remove();
        }
        subscribed=!sessions.isEmpty();
        if (!hasSubscribers()) { worlds.clear(); blasts.clear(); }
    }

    @SubscribeEvent
    public void worldTick(TickEvent.WorldTickEvent event)
    {
        if (event.side != Side.SERVER || event.phase != TickEvent.Phase.END || !hasSubscribers()) return;
        List<TrailSample> pending=blasts.remove(event.world);
        if(pending!=null) for(Session session:sessions.values()) {
            EntityPlayerMP player=session.player;
            if(player.worldObj!=event.world || (session.settings.flags & (1<<TrailSample.HBM))==0) continue;
            double range=Math.min(session.settings.distance,Config.Server.distantTrailRelayDistance.get());
            List<TrailSample> visible=new ArrayList<TrailSample>();
            for(TrailSample sample:pending) if(player.getDistanceSq(sample.x,sample.y,sample.z)>sample.mz*1024D*sample.mz*1024D && player.getDistanceSq(sample.x,sample.y,sample.z)<=range*range) visible.add(sample);
            if(!visible.isEmpty()) send(new Batch(player.dimension,visible),player);
        }
        LinkedHashMap<String, Stream> streams=worlds.get(event.world);
        if (streams == null) return;
        long tick=event.world.getTotalWorldTime();
        for (Iterator<Stream> i=streams.values().iterator();i.hasNext();)
        {
            Stream s=i.next();
            if (s.entity.isDead || s.entity.worldObj != event.world || tick-s.latest.tick > 5)
            {
                traceHbm(s,s.entity.isDead?"entity_dead":s.entity.worldObj!=event.world?"world_changed":"source_updates_stopped",tick);
                i.remove();
            }
        }
        if ((tick & 1) != 0) return;
        for (Session session:sessions.values())
        {
            EntityPlayerMP p=session.player;
            if (p.worldObj != event.world) continue;
            int distance=Math.min(session.settings.distance,Config.Server.distantTrailRelayDistance.get());
            List<TrailSample> chosen=new ArrayList<TrailSample>();
            int cursor=0, skip=session.cursor++ % Math.max(1,streams.size());
            // Rotate admission under overload so later streams are not permanently starved.
            for (int pass=0;pass<2 && chosen.size()<MAX_BATCH;pass++) for (Stream s:streams.values())
            {
                int index=cursor++ % Math.max(1,streams.size());
                if ((pass==0 && index<skip) || (pass==1 && index>=skip)) continue;
                if (tick-s.latest.tick > 1 || (session.settings.flags & (1 << s.latest.family)) == 0
                    || p.getDistanceSq(s.latest.x,s.latest.y,s.latest.z)>distance*(double)distance) continue;
                TrailSample sample=s.latest.copy();
                sample.x0=s.startX; sample.y0=s.startY; sample.z0=s.startZ;
                sample.count=Math.min(32,sample.count*2);
                chosen.add(sample);
                if (chosen.size()>=MAX_BATCH) break;
            }
            if (!chosen.isEmpty()) send(new Batch(event.world.provider.dimensionId,chosen),p);
        }
        for (Stream s:streams.values()) { s.startX=s.latest.x; s.startY=s.latest.y; s.startZ=s.latest.z; }
    }
    /** One start/end line per HBM flight, not per puff or tick. Helps separate server
     * simulation gaps from a working client renderer without adding gameplay behaviour. */
    private static void traceHbm(Stream stream,String reason,long tick)
    {
        TrailSample sample=stream.latest;
        if(sample.family!=TrailSample.HBM || sample.slot!=0) return;
        Entity entity=stream.entity;
        Object ticket="unknown";
        try { ticket=TrailSources.field(entity,"loaderTicket")==null?"absent":"present"; }
        catch(ReflectiveOperationException | RuntimeException ignored) { }
        org.apache.logging.log4j.LogManager.getLogger("DistantHorizons").info(
            "DH NTM flight: reason="+reason+" id="+entity.getEntityId()+" uuid="+entity.getUniqueID()
            +" type="+entity.getClass().getSimpleName()+" entityAge="+entity.ticksExisted
            +" lastEmissionAge="+(tick-sample.tick)+" dead="+entity.isDead+" ticket="+ticket
            +" chunkLoaded="+entity.worldObj.blockExists((int)Math.floor(entity.posX),(int)Math.floor(entity.posY),(int)Math.floor(entity.posZ))
            +" pos="+entity.posX+","+entity.posY+","+entity.posZ);
    }
    private void send(Batch batch,EntityPlayerMP player)
    {
        if(player.playerNetServerHandler==null || !peers.containsKey(player.playerNetServerHandler.netManager)) return;
        try { NETWORK.sendTo(batch,player); }
        catch(RuntimeException closedConnection)
        {
            peers.remove(player.playerNetServerHandler.netManager);
            org.apache.logging.log4j.LogManager.getLogger("DistantHorizons").warn("Visual trail delivery disabled for a closed/unsupported peer",closedConnection);
        }
    }
    @SubscribeEvent
    public void unload(WorldEvent.Unload event) { if (!event.world.isRemote) { worlds.remove(event.world);blasts.remove(event.world); } }
    public void clearServer()
    {
        sessions.clear(); worlds.clear(); blasts.clear(); peers.clear(); requests.clear(); subscribed=false;
        java.util.Arrays.fill(reportedFamily,false);
    }

    private static final class Stream
    {
        final Entity entity; TrailSample latest; double startX,startY,startZ;
        Stream(Entity entity,TrailSample sample) { this.entity=entity; latest=sample; startX=sample.x0; startY=sample.y0; startZ=sample.z0; }
    }
    private static final class Session
    {
        final EntityPlayerMP player; final Settings settings; final long changedAt; int cursor;
        Session(EntityPlayerMP p,Settings s,long t) { player=p;settings=s;changedAt=t; }
    }
    private static final class Request
    {
        final EntityPlayerMP player; final Settings settings;
        Request(EntityPlayerMP p,Settings s) { player=p;settings=s; }
    }
    public static final class Settings implements IMessage
    {
        int flags,distance; boolean valid;
        public Settings() { }
        Settings(int flags,int distance) { this.flags=flags;this.distance=distance;valid=true; }
        public void toBytes(ByteBuf b) { b.writeByte(VERSION).writeByte(flags).writeInt(distance); }
        public void fromBytes(ByteBuf b)
        {
            if (b.readableBytes()!=6) return;
            int version=b.readUnsignedByte();flags=b.readUnsignedByte();distance=b.readInt();
            valid=version==VERSION && (flags & ~14)==0 && distance>=64 && distance<=16384;
        }
    }
    public static final class Batch implements IMessage
    {
        public int dimension;
        public List<TrailSample> samples=new ArrayList<TrailSample>();
        public boolean valid;
        public Batch() { }
        Batch(int dimension,List<TrailSample> samples) { this.dimension=dimension;this.samples=samples;valid=true; }
        public void toBytes(ByteBuf b)
        {
            b.writeByte(VERSION).writeInt(dimension).writeByte(samples.size());
            for (TrailSample s:samples) s.write(b);
        }
        public void fromBytes(ByteBuf b)
        {
            if (b.readableBytes()<6 || b.readableBytes()>8192) return;
            try
            {
                if (b.readUnsignedByte()!=VERSION) return;
                dimension=b.readInt(); int count=b.readUnsignedByte();
                if (count>MAX_BATCH) return;
                for (int i=0;i<count;i++) samples.add(TrailSample.read(b));
                valid=!b.isReadable();
            }
            catch (RuntimeException malformed) { samples.clear(); }
        }
    }
    public static final class SettingsHandler implements IMessageHandler<Settings,IMessage>
    {
        public IMessage onMessage(Settings message,MessageContext context)
        {
            if (message.valid) INSTANCE.requests.offer(new Request(context.getServerHandler().playerEntity,message));
            return null;
        }
    }
    public static final class BatchHandler implements IMessageHandler<Batch,IMessage>
    {
        public IMessage onMessage(Batch message,MessageContext context)
        {
            Consumer<Batch> receiver=INSTANCE.clientReceiver;
            if (message.valid && receiver!=null) receiver.accept(message);
            return null;
        }
    }
}
