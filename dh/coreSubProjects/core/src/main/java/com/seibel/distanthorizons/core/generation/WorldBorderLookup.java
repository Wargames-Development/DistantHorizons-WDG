package com.seibel.distanthorizons.core.generation;
import com.seibel.distanthorizons.core.generation.WorldBorderBounds;
/** Invoked only on the server thread; no Bukkit symbols are statically linked. */
public final class WorldBorderLookup {
    private WorldBorderLookup() { }
    /** Actual Bukkit world name, independently of whether WorldBorder is installed. */
    public static String worldName(Object world) {
        if(world==null)return null;
        try {
            Class<?> bukkit=Class.forName("org.bukkit.Bukkit");
            for(Object w:(Iterable<?>)bukkit.getMethod("getWorlds").invoke(null))
                if(w.getClass().getMethod("getHandle").invoke(w)==world)
                    return (String)w.getClass().getMethod("getName").invoke(w);
        } catch(ReflectiveOperationException|RuntimeException|LinkageError unavailable) { }
        return null;
    }
    public static WorldBorderBounds inspect(Object world) {
        if(world==null)return null;
        try {
            Class<?> bukkit=Class.forName("org.bukkit.Bukkit");
            Object pm=bukkit.getMethod("getPluginManager").invoke(null);
            Object plugin=pm.getClass().getMethod("getPlugin",String.class).invoke(pm,"WorldBorder");
            if(plugin==null||!Boolean.TRUE.equals(plugin.getClass().getMethod("isEnabled").invoke(plugin)))return null;
            // Resolve the actual CraftWorld by its native handle, not dimension/environment guesses.
            Object bw=null;
            for(Object w:(Iterable<?>)bukkit.getMethod("getWorlds").invoke(null)) {
                if(w.getClass().getMethod("getHandle").invoke(w)==world) {bw=w;break;}
            }
            if(bw==null)return null;
            Class<?> config=Class.forName("com.wimbli.WorldBorder.Config",true,plugin.getClass().getClassLoader());
            Object border=config.getMethod("Border",String.class).invoke(null,bw.getClass().getMethod("getName").invoke(bw));
            if(border==null)return null;
            Class<?> type=border.getClass();Object shape=type.getMethod("getShape").invoke(border);
            boolean round=shape==null?Boolean.TRUE.equals(config.getMethod("ShapeRound").invoke(null)):Boolean.TRUE.equals(shape);
            if(round)return null; // This adapter's contract is rectangular borders only.
            double x=((Number)type.getMethod("getX").invoke(border)).doubleValue();
            double z=((Number)type.getMethod("getZ").invoke(border)).doubleValue();
            double rx=((Number)type.getMethod("getRadiusX").invoke(border)).doubleValue();
            double rz=((Number)type.getMethod("getRadiusZ").invoke(border)).doubleValue();
            return new WorldBorderBounds(x-rx,z-rz,x+rx,z+rz);
        } catch(ClassNotFoundException absent) {return null;}
        catch(ReflectiveOperationException|LinkageError|RuntimeException incompatible) {return null;}
    }
}
