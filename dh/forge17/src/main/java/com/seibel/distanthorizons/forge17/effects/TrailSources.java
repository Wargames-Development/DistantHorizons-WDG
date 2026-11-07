package com.seibel.distanthorizons.forge17.effects;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.entity.Entity;
import net.minecraft.item.Item;
import net.minecraft.util.Vec3;
import org.apache.logging.log4j.LogManager;

/** Explicit adapters for inspected source paths. Unknown overrides fail closed. */
public final class TrailSources
{
    // Integrated servers and client diagnostics share these reflection caches.
    private static final Map<String,Field> FIELDS=new ConcurrentHashMap<String,Field>();
    private static final Map<String,Method> METHODS=new ConcurrentHashMap<String,Method>();
    private static final Map<String,Boolean> UNSUPPORTED=new HashMap<String,Boolean>();
    private TrailSources() { }

    public static Object field(Object object,String name) throws ReflectiveOperationException
    {
        String key=object.getClass().getName()+"#"+name;
        Field f=FIELDS.get(key);
        if (f==null)
        {
            Class<?> type=object.getClass();
            while (type!=null)
            {
                try { f=type.getDeclaredField(name);break; } catch (NoSuchFieldException absent) { type=type.getSuperclass(); }
            }
            if (f==null) throw new NoSuchFieldException(key);
            f.setAccessible(true);
            if (FIELDS.size()<256) FIELDS.put(key,f);
        }
        return f.get(object);
    }
    private static Method method(Object object,String name) throws ReflectiveOperationException
    {
        String key=object.getClass().getName()+"#"+name;
        Method m=METHODS.get(key);
        if (m==null)
        {
            Class<?> type=object.getClass();
            while (type!=null)
            {
                try { m=type.getDeclaredMethod(name);break; } catch (NoSuchMethodException absent) { type=type.getSuperclass(); }
            }
            if (m==null) throw new NoSuchMethodException(key);
            m.setAccessible(true);
            if (METHODS.size()<128) METHODS.put(key,m);
        }
        return m;
    }
    public static void mch(Entity e,String name,int count,float size)
    {
        if (!TrailRelay.INSTANCE.hasSubscribers() || e.worldObj.isRemote || name==null || name.isEmpty() || count<1 || count>50) return;
        TrailSample s=new TrailSample();s.family=TrailSample.MCH;s.style=name;s.count=Math.min(16,count);s.scale=size;
        TrailRelay.INSTANCE.offer(e,s);
    }
    public static void flans(Entity e)
    {
        if (!eligible(e)) return;
        try
        {
            Object type=field(e,"type");
            if(type==null) return;
            boolean trails=(Boolean)field(type,"trailParticles");
            int age=((Number)field(e,"ticksInAir")).intValue();
            if(!trails || age<=1) return;
            String boost=(String)field(type,"boostPhaseParticleType");
            int delay=((Number)field(e,"VLSDelay")).intValue();
            emit(e,TrailRules.flansPrimary(trails,age,(Boolean)field(type,"VLS"),delay,(String)field(type,"trailParticleType"),boost),0,10,1);
            emit(e,TrailRules.flansBoost(trails,age,(Boolean)field(e,"isAccelerating"),boost),1,8,1);
        }
        catch (ReflectiveOperationException | RuntimeException | LinkageError problem) { unsupported(e,problem); }
    }
    private static void emit(Entity e,String style,int slot,int count,float scale)
    {
        if (style==null || style.isEmpty()) return;
        TrailSample s=new TrailSample();s.family=TrailSample.FLANS;s.style=style;s.slot=slot;s.count=count;s.scale=scale;
        TrailRelay.INSTANCE.offer(e,s);
    }
    public static void hbm(Entity e)
    {
        if (!eligible(e)) return;
        try
        {
            String owner=method(e,"spawnContrail").getDeclaringClass().getSimpleName();
            // Rideable rockets and unreviewed overrides have different engine rules.
            if (!owner.equals("EntityMissileBaseNT") && !owner.equals("EntityMissileCustom")
                && !owner.equals("EntityMissileTier3") && !owner.equals("EntityMissileTier4")) return;
            TrailSample body=new TrailSample(); body.family=TrailSample.HBM;body.style="dhBody."+e.getClass().getSimpleName();body.slot=7;body.count=1;
            body.scale=6;
            body.mx=(float)e.motionX;body.my=(float)e.motionY;body.mz=(float)e.motionZ;
            TrailRelay.INSTANCE.offer(e,body);
            String style="missileContrail";
            float scale=((Number)method(e,"getContrailScale").invoke(e)).floatValue();
            int count=Math.max(1,Math.min(10,(int)Math.ceil(Math.sqrt(e.motionX*e.motionX+e.motionY*e.motionY+e.motionZ*e.motionZ))));
            if (owner.equals("EntityMissileCustom"))
            {
                Item fuselage=Item.getItemById(e.getDataWatcher().getWatchableObjectInt(10));
                Object[] attributes=(Object[])field(fuselage,"attributes");
                String fuel=((Enum<?>)attributes[0]).name();
                style=TrailRules.hbmFuel(fuel);
                if(style==null) return;
                scale=1;
            }
            Vec3 thrust=Vec3.createVectorHelper(0,1,0);
            thrust.rotateAroundZ(e.rotationPitch*(float)Math.PI/180F);
            thrust.rotateAroundY((e.rotationYaw+90)*(float)Math.PI/180F);
            if (owner.equals("EntityMissileTier3") || owner.equals("EntityMissileTier4"))
            {
                boolean tier4=owner.equals("EntityMissileTier4");
                Vec3 offset=Vec3.createVectorHelper(0,0,tier4?1:0.5);
                if(tier4)
                {
                    byte rotation=e.getDataWatcher().getWatchableObjectByte(3);
                    if(rotation==2) offset.rotateAroundY((float)-Math.PI/2F);
                    else if(rotation==4) offset.rotateAroundY((float)-Math.PI);
                    else if(rotation==3) offset.rotateAroundY((float)-Math.PI/2F*3F);
                }
                offset.rotateAroundY((e.rotationYaw+90)*(float)Math.PI/180F);
                offset.rotateAroundX(e.rotationPitch*(float)Math.PI/180F);
                offset.rotateAroundY(-(e.rotationYaw+90)*(float)Math.PI/180F);
                hbmSample(e,style,0,count,scale,thrust,offset.xCoord,offset.yCoord,offset.zCoord);
                if (tier4)
                {
                    hbmSample(e,style,1,count,scale,thrust,0,0,0);
                    hbmSample(e,style,2,count,scale,thrust,-offset.xCoord,-offset.zCoord,-offset.zCoord);
                }
                else
                {
                    hbmSample(e,style,1,count,scale,thrust,-offset.zCoord,offset.yCoord,offset.xCoord);
                    hbmSample(e,style,2,count,scale,thrust,-offset.xCoord,-offset.zCoord,-offset.zCoord);
                    hbmSample(e,style,3,count,scale,thrust,offset.zCoord,-offset.zCoord,-offset.xCoord);
                }
            }
            else hbmSample(e,style,0,count,scale,thrust,0,0,0);
        }
        catch (ReflectiveOperationException | RuntimeException | LinkageError problem) { unsupported(e,problem); }
    }
    private static void hbmSample(Entity e,String style,int slot,int count,float scale,Vec3 thrust,double x,double y,double z)
    {
        TrailSample s=new TrailSample();s.family=TrailSample.HBM;s.style=style;s.slot=slot;s.count=count;s.scale=scale;
        s.mx=(float)-thrust.xCoord;s.my=(float)-thrust.yCoord;s.mz=(float)-thrust.zCoord;
        s.ox=x;s.oy=y;s.oz=z;
        TrailRelay.INSTANCE.offer(e,s);
    }
    private static boolean eligible(Entity e)
    {
        return TrailRelay.INSTANCE.hasSubscribers() && !e.worldObj.isRemote && !e.isDead
            && !UNSUPPORTED.containsKey(e.getClass().getName());
    }
    private static void unsupported(Entity e,Throwable problem)
    {
        String key=e.getClass().getName();
        if (UNSUPPORTED.size()<64 && UNSUPPORTED.put(key,Boolean.TRUE)==null)
            LogManager.getLogger("DistantHorizons").warn("Distant trail adapter disabled for unsupported source "+key,problem);
    }
}
