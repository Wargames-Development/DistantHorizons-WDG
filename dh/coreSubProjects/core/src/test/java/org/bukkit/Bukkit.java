package org.bukkit;
import java.util.*;
/** Test-only optional-plugin fixture; never packaged in the mod. */
public final class Bukkit {
    public static Object plugin;
    public static List<Object> worlds=new ArrayList<>();
    public static Manager getPluginManager(){return new Manager();}
    public static List<Object> getWorlds(){return worlds;}
    public static final class Manager {public Object getPlugin(String name){return plugin;}}
    public static final class Plugin {public boolean enabled=true;public boolean isEnabled(){return enabled;}}
    public static final class World {public final Object handle;public final String name;
        public World(Object h,String n){handle=h;name=n;}public Object getHandle(){return handle;}public String getName(){return name;}}
}
