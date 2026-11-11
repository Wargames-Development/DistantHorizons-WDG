package com.seibel.distanthorizons.forge17.actors;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.*;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;

/** Optional client model boundary. Experimental launch gate stays closed until pack runtime acceptance. */
@SideOnly(Side.CLIENT)
public final class McheliActors {
    private final Map<UUID,Contact> contacts=new LinkedHashMap<>();
    private Method create,observe,sample,prepare,render,close,invalidate,nativeRadius,riddenVehicle;
    private boolean available;
    private long sequence=-1,receipt;
    public McheliActors(BooleanSupplier owner,Predicate<Entity> replacement,Predicate<Entity> observation) {
        if(!Boolean.getBoolean("dh.experimentalMcheliModels") || !Loader.isModLoaded("mcheli") || !FmurPlayerBridge.supportsViews()) return;
        try {
            Class<?> api=Class.forName("mcheli.lod.MCH_ExternalVehicleRenderer",false,getClass().getClassLoader());
            if(api.getField("API_VERSION").getInt(null)!=4) return;
            create=api.getMethod("create",World.class);nativeRadius=api.getMethod("nativeRadius",Entity.class);Class<?> context=create.getReturnType();
            riddenVehicle=Class.forName("mcheli.aircraft.MCH_EntityAircraft",false,getClass().getClassLoader()).getMethod("getAircraft_RiddenOrControl",Entity.class);
            observe=context.getMethod("observe",byte[].class,UUID.class,int.class,double.class,double.class,double.class,long.class,long.class,int.class,int.class);
            sample=context.getMethod("sample",long.class,double[].class);prepare=context.getMethod("prepare");
            render=context.getMethod("render",double.class,double.class,double.class,float.class);close=context.getMethod("close");invalidate=context.getMethod("invalidate");
            api.getMethod("setOwner",BooleanSupplier.class,Predicate.class,Predicate.class).invoke(null,owner,replacement,observation);available=true;
        } catch(ReflectiveOperationException | RuntimeException | LinkageError unsupported) {
            org.apache.logging.log4j.LogManager.getLogger("DistantHorizons").warn("Optional MCHELI model adapter unavailable; legacy retained",unsupported);
        }
    }
    public boolean available() { return available && VehicleClient.INSTANCE!=null; }
    public void ready() { if(available()) VehicleClient.INSTANCE.activateDelivery(); }
    public boolean ownsLegacy() { return available() && VehicleClient.INSTANCE.ownsLegacy(); }
    public boolean contains(Entity entity) {
        Contact contact=contacts.get(entity.getUniqueID());return contact!=null && contact.sampled && !contact.invalid && contact.entityId==entity.getEntityId();
    }
    /** Cockpit/local camera models belong to MCHELI's native near-depth path. */
    public boolean localVehicle(Entity entity) {
        if(entity==null || riddenVehicle==null) return false;
        Minecraft mc=Minecraft.getMinecraft();
        try {
            return entity==riddenVehicle.invoke(null,mc.thePlayer) || entity==riddenVehicle.invoke(null,mc.renderViewEntity);
        } catch(ReflectiveOperationException | RuntimeException | LinkageError failure) {
            // Retain native ownership if the optional cockpit identity seam fails.
            return true;
        }
    }
    public double radius(Entity entity) {
        Contact contact=contacts.get(entity.getUniqueID());if(contact!=null && contact.sampled) return contact.position[3];
        try { return ((Number)nativeRadius.invoke(null,entity)).doubleValue(); }
        catch(ReflectiveOperationException | RuntimeException | LinkageError unavailable) { return 128; }
    }
    public void collect(List<ActorModel> models,Set<UUID> nativeDrawn) {
        if(!ownsLegacy()) { clear();return; }
        VehicleFrameCache cache=VehicleClient.INSTANCE.frames();VehicleFrame frame=cache.frame();
        if(frame==null || !frame.sourceReady) { clear();return; }
        if(frame.sequence!=sequence || cache.received()!=receipt) {
            sequence=frame.sequence;receipt=cache.received();Set<UUID> retained=new HashSet<>();
            for(VehicleFrame.Entry entry:frame.entries) retained.add(entry.uuid);
            Iterator<Map.Entry<UUID,Contact>> old=contacts.entrySet().iterator();
            while(old.hasNext()) { Map.Entry<UUID,Contact> entry=old.next();if(!retained.contains(entry.getKey())) { entry.getValue().dispose();old.remove(); } }
            for(VehicleFrame.Entry entry:frame.entries) {
                Contact contact=contacts.get(entry.uuid);
                try {
                    if(contact==null) { contact=new Contact(create.invoke(null,Minecraft.getMinecraft().theWorld));contacts.put(entry.uuid,contact); }
                    observe.invoke(contact.context,entry.payload(),entry.uuid,entry.entityId,entry.x,entry.y,entry.z,frame.sequence,receipt,frame.interval,frame.range);
                    contact.invalid=false;contact.entityId=entry.entityId;
                } catch(ReflectiveOperationException | RuntimeException | LinkageError invalid) {
                    if(contact!=null) contact.reject(invalid);
                }
            }
        }
        long now=System.nanoTime();
        for(Map.Entry<UUID,Contact> entry:contacts.entrySet()) {
            Contact contact=entry.getValue();if(contact.invalid) continue;
            try {
                Entity loaded=Minecraft.getMinecraft().theWorld.getEntityByID(contact.entityId);
                if(loaded!=null && loaded.getUniqueID().equals(entry.getKey()) && localVehicle(loaded)) continue;
                contact.sampled=Boolean.TRUE.equals(sample.invoke(contact.context,now,contact.position));
                if(contact.sampled && !nativeDrawn.contains(entry.getKey())) models.add(contact);
            }
            catch(ReflectiveOperationException | RuntimeException | LinkageError invalid) { contact.reject(invalid); }
        }
    }
    public void clear() { for(Contact contact:contacts.values()) contact.dispose();contacts.clear();sequence=-1;receipt=0; }
    private final class Contact implements ActorModel {
        final Object context;final double[] position=new double[6];boolean invalid,sampled;int entityId;
        Contact(Object context) { this.context=context; }
        public double x() { return position[0]; }public double y() { return position[1]; }public double centerY() { return position[1]; }
        public double z() { return position[2]; }public double radius() { return position[3]; }
        public int light() { return (int)position[4]; }public float partial() { return (float)position[5]; }
        public boolean prepare() {
            try { return !invalid && Boolean.TRUE.equals(prepare.invoke(context)); }
            catch(ReflectiveOperationException | RuntimeException | LinkageError failure) { reject(failure);return false; }
        }
        public void draw(double x,double y,double z) throws ReflectiveOperationException { render.invoke(context,x,y,z,partial()); }
        void reject(Throwable failure) {
            if(!invalid) org.apache.logging.log4j.LogManager.getLogger("DistantHorizons").warn("MCHELI visual actor withheld after invalid or unsupported source/model state",failure);
            invalid=true;sampled=false;
            try { invalidate.invoke(context); }catch(ReflectiveOperationException | RuntimeException | LinkageError ignored) { }
        }
        void dispose() { try { close.invoke(context); }catch(ReflectiveOperationException | RuntimeException | LinkageError failure) { reject(failure); } }
    }
}
