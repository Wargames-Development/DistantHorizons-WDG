package com.seibel.distanthorizons.forge17.actors;

import cpw.mods.fml.common.Loader;
import net.minecraft.entity.Entity;

/** Versioned optional common-side source boundary. Never links client render classes. */
public final class McheliSource {
    private static boolean resolved,available;
    private static java.lang.reflect.Method capture,eligible;
    private McheliSource() { }
    public static boolean available() {
        if(!resolved) {
            resolved=true;
            if(Loader.isModLoaded("mcheli")) try {
                Class<?> api=Class.forName("mcheli.lod.MCH_ExternalVehiclePresentation",false,McheliSource.class.getClassLoader());
                if(api.getField("API_VERSION").getInt(null)==4) { capture=api.getMethod("capture",Entity.class);eligible=api.getMethod("eligible",Entity.class);available=true; }
            } catch(ReflectiveOperationException | RuntimeException | LinkageError unsupported) { available=false; }
        }
        return available;
    }
    public static boolean supported(Entity entity) {
        if(entity==null || entity.isDead || entity.isInvisible()) return false;
        String type=entity.getClass().getName();
        boolean known=type.equals("mcheli.helicopter.MCH_EntityHeli") || type.equals("mcheli.plane.MCP_EntityPlane")
            || type.equals("mcheli.tank.MCH_EntityTank") || type.equals("mcheli.vehicle.MCH_EntityVehicle");
        if(!known || !available()) return false;
        try { return Boolean.TRUE.equals(eligible.invoke(null,entity)); }
        catch(ReflectiveOperationException | RuntimeException | LinkageError unsupported) { return false; }
    }
    public static VehicleFrame.Entry capture(Entity entity) {
        if(!available() || entity==null) return null;
        try {
            byte[] bytes=(byte[])capture.invoke(null,entity);
            return bytes==null?null:new VehicleFrame.Entry(entity.getUniqueID(),entity.getEntityId(),entity.posX,entity.posY,entity.posZ,bytes);
        } catch(ReflectiveOperationException | RuntimeException | LinkageError invalid) { return null; }
    }
}
