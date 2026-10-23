package com.seibel.distanthorizons.core;
import org.junit.Test;import static org.junit.Assert.*;
import org.bukkit.Bukkit;import com.wimbli.WorldBorder.Config;
import com.seibel.distanthorizons.core.generation.*;
public class WorldBorderLookupTest {
    @Test public void optionalPluginAndActualWorldIdentity() {
        Object actual=new Object(),other=new Object();Bukkit.plugin=null;Bukkit.worlds.clear();Config.borders.clear();
        assertNull(WorldBorderLookup.inspect(actual));
        Bukkit.Plugin plugin=new Bukkit.Plugin();Bukkit.plugin=plugin;
        Bukkit.worlds.add(new Bukkit.World(other,"other-world"));
        Config.Border b=new Config.Border();b.rx=20992;b.rz=12288;b.shape=false;Config.borders.put("other-world",b);
        assertNull(WorldBorderLookup.inspect(actual)); // Never borrow another world's border.
        Bukkit.worlds.add(new Bukkit.World(actual,"earth40"));assertNull(WorldBorderLookup.inspect(actual));
        assertEquals("earth40",WorldBorderLookup.worldName(actual));assertNull(WorldBorderLookup.worldName(new Object()));
        Config.borders.put("earth40",b);assertEquals(new WorldBorderBounds(-20992,-12288,20992,12288),WorldBorderLookup.inspect(actual));
        b.rx=100;assertEquals(100,WorldBorderLookup.inspect(actual).maxX,0);
        b.shape=true;assertNull(WorldBorderLookup.inspect(actual));b.shape=null;Config.round=true;assertNull(WorldBorderLookup.inspect(actual));
        Config.round=false;assertNotNull(WorldBorderLookup.inspect(actual));
        plugin.enabled=false;assertNull(WorldBorderLookup.inspect(actual));plugin.enabled=true;
        b.rx=0;assertNull(WorldBorderLookup.inspect(actual));Config.borders.clear();assertNull(WorldBorderLookup.inspect(actual));
    }
    @Test public void absentBukkitClassesRemainOptional() throws Exception {
        java.net.URL location=WorldBorderLookup.class.getProtectionDomain().getCodeSource().getLocation();
        try(java.net.URLClassLoader loader=new java.net.URLClassLoader(new java.net.URL[]{location},null)) {
            Class<?> type=loader.loadClass(WorldBorderLookup.class.getName());assertNull(type.getMethod("inspect",Object.class).invoke(null,new Object()));
        }
    }
}
