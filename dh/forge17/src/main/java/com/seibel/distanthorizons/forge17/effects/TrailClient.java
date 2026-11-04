package com.seibel.distanthorizons.forge17.effects;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import com.seibel.distanthorizons.core.config.Config;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import org.apache.logging.log4j.LogManager;

/** Main-thread consumer. Uses the installed source mod's real particle factories and normal lifetimes. */
public final class TrailClient
{
    public static TrailClient INSTANCE;
    private final ArrayBlockingQueue<TrailRelay.Batch> incoming=new ArrayBlockingQueue<TrailRelay.Batch>(8);
    private final Map<Integer,Emission> nativeEmissions=new HashMap<Integer,Emission>();
    private final int[] familyReceived=new int[4],familyBirths=new int[4],lastFamilyTick=new int[4];
    private final int[] lastFamilyDistance=new int[4];
    // Five seconds at four snapshots/second, plus the short post-delivery tail.
    private final java.util.ArrayDeque<String> flightHistory=new java.util.ArrayDeque<String>();
    private boolean flightHistoryActive;

    private final boolean[] failed=new boolean[4];
    private World world;
    private int clientTick,lastFlags=-1,lastDistance=-1,births,received,deduplicated,dropped;
    private boolean acknowledged;
    private Constructor<?> mchParam;
    private Method mchSpawn,mchVanilla,hbmEffect,flansSpawn;
    private Object hbmProxy;
    private TrailSample probe;
    private int probeTicks;
    private int creationAge;

    public TrailClient()
    {
        INSTANCE=this;
        TrailRelay.INSTANCE.setClientReceiver(batch -> { if(!incoming.offer(batch)) { incoming.poll();NtmTrace.queueEvicted();incoming.offer(batch); } });
    }
    public static void nativeEmission(Entity entity)
    {
        TrailClient c=INSTANCE;
        if(c!=null && entity.worldObj==c.world && c.nativeEmissions.size()<1024)
        {
            Emission emission=c.nativeEmissions.get(entity.getEntityId());
            if(emission==null) { emission=new Emission();c.nativeEmissions.put(entity.getEntityId(),emission); }
            emission.tick=entity.worldObj.getTotalWorldTime();emission.x=entity.posX;emission.y=entity.posY;emission.z=entity.posZ;
        }
    }
    public static int flags()
    {
        int flags=0;
        if(Loader.isModLoaded("mcheli") && EffectSources.enabled(TrailSample.MCH)) flags|=1<<TrailSample.MCH;
        if(Loader.isModLoaded("hbm") && EffectSources.enabled(TrailSample.HBM)) flags|=1<<TrailSample.HBM;
        if(Loader.isModLoaded("flansmod") && EffectSources.enabled(TrailSample.FLANS)) flags|=1<<TrailSample.FLANS;
        return flags;
    }
    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event)
    {
        if(event.phase!=TickEvent.Phase.END) return;
        Minecraft mc=Minecraft.getMinecraft();
        if(world!=mc.theWorld)
        {
            world=mc.theWorld;nativeEmissions.clear();incoming.clear();EffectSources.clear();NtmTrace.clear();
            probe=null;probeTicks=0;acknowledged=false;lastFlags=-1;flightHistory.clear();flightHistoryActive=false;
        }
        if(world==null || mc.thePlayer==null) return;
        clientTick++;births=0;
        int flags=flags(),distance=Config.Client.Advanced.Graphics.GenericRendering.distantSmokeDistance.get();
        if(TrailRelay.INSTANCE.serverPresent() && (flags!=lastFlags || distance!=lastDistance || clientTick%100==0))
        {
            TrailRelay.INSTANCE.subscribe(flags,distance);lastFlags=flags;lastDistance=distance;
        }
        long tick=world.getTotalWorldTime();
        for(Iterator<Map.Entry<Integer,Emission>> it=nativeEmissions.entrySet().iterator();it.hasNext();)
            if(tick-it.next().getValue().tick>40) it.remove();
        TrailRelay.Batch batch;
        for(int n=0;n<2 && (batch=incoming.poll())!=null;n++)
        {
            if(batch.dimension!=world.provider.dimensionId) continue;
            acknowledged=true;
            for(TrailSample sample:batch.samples)
            {
                if(!EffectSources.enabled(sample.family)) continue;
                received++;familyReceived[sample.family]++;
                if(sample.family==TrailSample.HBM && !flightHistoryActive) {
                    flightHistoryActive=true;flightHistory.clear();
                }
                double dx=sample.x-mc.thePlayer.posX,dy=sample.y-mc.thePlayer.posY,dz=sample.z-mc.thePlayer.posZ;
                lastFamilyTick[sample.family]=clientTick;
                lastFamilyDistance[sample.family]=(int)Math.round(Math.sqrt(dx*dx+dy*dy+dz*dz));
                if(dx*dx+dy*dy+dz*dz>distance*(double)distance) continue;
                if(tick-sample.tick>10 || sample.tick-tick>200) { dropped++;continue; }
                Emission emission=nativeEmissions.get(sample.entityId);
                double traceCovered=0;
                if(emission!=null && (sample.family!=TrailSample.HBM || tick-emission.tick<=2))
                {
                    double covered=TrailRules.coveredFraction(sample.x0,sample.y0,sample.z0,sample.x,sample.y,sample.z,emission.x,emission.y,emission.z);
                    traceCovered=covered;
                    if(covered>=1) { if(sample.family==TrailSample.HBM) NtmTrace.delivery(sample,covered);deduplicated++;continue; }
                    if(covered>0)
                    {
                        sample.x0+=(sample.x-sample.x0)*covered;sample.y0+=(sample.y-sample.y0)*covered;sample.z0+=(sample.z-sample.z0)*covered;
                    }
                }
                if(sample.family==TrailSample.HBM) NtmTrace.delivery(sample,traceCovered);
                int count=Math.min(16,sample.count);
                // Reduce distant sampling density, retaining the observed segment and original puff lifetime/size.
                if(dx*dx+dy*dy+dz*dz>1024D*1024D) count=Math.max(2,(count+1)/2);
                spawnSegment(sample,count);
            }
        }
        recordFlightHistory();
        if(probe!=null && probeTicks>0)
        {
            if(clientTick%4==0) spawnSegment(probe,3);
            if(clientTick%100==0) logStatus();
            if(--probeTicks==0) { probe=null;logStatus(); }
        }
    }
    /** Automatic bounded diagnostics: no screenshot or precisely timed command needed. */
    private void recordFlightHistory()
    {
        if(!flightHistoryActive) return;
        int quietTicks=clientTick-lastFamilyTick[TrailSample.HBM];
        boolean finished=quietTicks>=20;
        if(clientTick%5==0 || finished) {
            if(flightHistory.size()>=24) flightHistory.removeFirst();
            String rendering=DistantSmokeRenderer.INSTANCE==null?"renderer unavailable":DistantSmokeRenderer.INSTANCE.diagnostic();
            flightHistory.addLast("tick="+clientTick+" "+deliveryDiagnostic()+" | "+rendering+" | "+NtmTrace.snapshot());
        }
        if(finished) {
            org.apache.logging.log4j.Logger logger=LogManager.getLogger("DistantHorizons");
            logger.info("DH NTM automatic flight history BEGIN (delivery quiet for "+quietTicks+" ticks; not proof of impact)");
            for(String snapshot:flightHistory) logger.info("DH NTM history: "+snapshot);
            logger.info("DH NTM automatic flight history END");
            flightHistory.clear();flightHistoryActive=false;
        }
    }
    private static final class Emission { long tick;double x,y,z; }
    private void spawnSegment(TrailSample sample,int count)
    {
        if(failed[sample.family]) return;
        creationAge=sample==probe?0:(int)Math.max(0,Math.min(10,world.getTotalWorldTime()-sample.tick));
        EffectOrigin.Source previous=EffectOrigin.enter(null,sample.family,true);
        try
        {
            for(int i=0;i<count;i++)
            {
                if(births>=128) { dropped++;break; }
                double t=(i+0.5)/count;
                double x=sample.x0+(sample.x-sample.x0)*t,y=sample.y0+(sample.y-sample.y0)*t,z=sample.z0+(sample.z-sample.z0)*t;
                spawn(sample,x,y,z);
            }
        }
        catch(ReflectiveOperationException | RuntimeException | LinkageError error)
        {
            failed[sample.family]=true;
            LogManager.getLogger("DistantHorizons").warn("Distant trail particle factory disabled for family "+sample.family+"; native effects unchanged",error);
        }
        finally { EffectOrigin.restore(previous);creationAge=0; }
    }
    public static boolean acceptBirth(net.minecraft.client.particle.EntityFX particle)
    {
        EffectOrigin.Source origin=EffectOrigin.get();
        if(origin==null || !origin.remote || INSTANCE==null) return true;
        if(INSTANCE.births>=128) { INSTANCE.dropped++;return false; }
        INSTANCE.births++;INSTANCE.familyBirths[origin.family]++;return true;
    }
    public static void ageBirth(net.minecraft.client.particle.EntityFX particle)
    {
        EffectOrigin.Source origin=EffectOrigin.get();
        if(origin==null || !origin.remote || INSTANCE==null) return;
        for(int i=0;i<INSTANCE.creationAge && !particle.isDead;i++) particle.onUpdate();
    }
    private void spawn(TrailSample s,double x,double y,double z) throws ReflectiveOperationException
    {
        if(s.family==TrailSample.MCH)
        {
            if(s.style.equals("explode"))
            {
                if(mchParam==null)
                {
                    Class<?> param=Class.forName("mcheli.particles.MCH_ParticleParam");
                    mchParam=param.getConstructor(World.class,String.class,double.class,double.class,double.class);
                    mchSpawn=Class.forName("mcheli.particles.MCH_ParticlesUtil").getMethod("spawnParticle",param);
                }
                Object param=mchParam.newInstance(world,"smoke",x,y,z);
                param.getClass().getField("size").setFloat(param,s.scale+world.rand.nextFloat());
                mchSpawn.invoke(null,param);
            }
            else
            {
                if(mchVanilla==null) mchVanilla=Class.forName("mcheli.particles.MCH_ParticlesUtil").getMethod("doSpawnParticle",String.class,
                    double.class,double.class,double.class,double.class,double.class,double.class);
                mchVanilla.invoke(null,s.style,x,y,z,0D,0D,0D);
            }
        }
        else if(s.family==TrailSample.HBM)
        {
            if(hbmEffect==null)
            {
                hbmProxy=Class.forName("com.hbm.main.MainRegistry").getField("proxy").get(null);
                hbmEffect=hbmProxy.getClass().getMethod("effectNT",NBTTagCompound.class);
            }
            NBTTagCompound nbt=new NBTTagCompound();
            nbt.setString("type",s.style);nbt.setDouble("posX",x);nbt.setDouble("posY",y);nbt.setDouble("posZ",z);
            nbt.setFloat("scale",s.scale);nbt.setDouble("moX",s.mx);nbt.setDouble("moY",s.my);nbt.setDouble("moZ",s.mz);
            nbt.setInteger("maxAge",60+world.rand.nextInt(20));
            hbmEffect.invoke(hbmProxy,nbt);
        }
        else if(s.family==TrailSample.FLANS)
        {
            if(flansSpawn==null)
            {
                flansSpawn=Class.forName("com.flansmod.client.ClientProxy").getDeclaredMethod("doSpawnParticle",String.class,
                    double.class,double.class,double.class,double.class,double.class,double.class,float.class);
                flansSpawn.setAccessible(true);
            }
            flansSpawn.invoke(null,s.style,x,y,z,0D,0D,0D,1F);
        }
    }
    public void probe(int family,int distance,int seconds)
    {
        Minecraft mc=Minecraft.getMinecraft();
        net.minecraft.util.Vec3 look=mc.thePlayer.getLookVec();
        probe=new TrailSample();probe.family=family;probe.uuid=UUID.randomUUID();probe.count=3;
        probe.style=family==TrailSample.MCH?"explode":family==TrailSample.HBM?"missileContrail":"flansmod.rocketexhaust";
        probe.scale=family==TrailSample.MCH?40:3;
        probe.x0=probe.x=mc.thePlayer.posX+look.xCoord*distance;
        probe.y0=probe.y=mc.thePlayer.posY+mc.thePlayer.getEyeHeight()+look.yCoord*distance;
        probe.z0=probe.z=mc.thePlayer.posZ+look.zCoord*distance;
        probeTicks=seconds*20;
    }
    public void probeRpg(int distance,int seconds)
    {
        probe(TrailSample.FLANS,distance,seconds);
        probe.style="flansmod.fmsmoke";
    }
    public void stopProbe() { probe=null;probeTicks=0; }
    public String diagnostic()
    {
        return "Trail relay: "+(!TrailRelay.INSTANCE.serverPresent()?"server channel absent":acknowledged?"connected":"awaiting server opt-in")
            +"; received="+received+" segments; native duplicates skipped="+deduplicated+"; dropped="+dropped;
    }
    public String deliveryDiagnostic()
    {
        return "Trail delivery: received M/H/F="+familyReceived[1]+"/"+familyReceived[2]+"/"+familyReceived[3]
            +"; births M/H/F="+familyBirths[1]+"/"+familyBirths[2]+"/"+familyBirths[3]+"; NTM native emitter=preserved"
            +"; last H="+lastSample(TrailSample.HBM)+" F="+lastSample(TrailSample.FLANS);
    }
    private String lastSample(int family)
    {
        return lastFamilyTick[family]==0?"none":(clientTick-lastFamilyTick[family])+"t/"+lastFamilyDistance[family]+"m";
    }
    public void logStatus()
    {
        LogManager.getLogger("DistantHorizons").info(diagnostic());
        LogManager.getLogger("DistantHorizons").info(deliveryDiagnostic());
        if(DistantSmokeRenderer.INSTANCE!=null) LogManager.getLogger("DistantHorizons").info(DistantSmokeRenderer.INSTANCE.diagnostic());
    }
}
