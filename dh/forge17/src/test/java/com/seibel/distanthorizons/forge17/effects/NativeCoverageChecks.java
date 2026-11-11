package com.seibel.distanthorizons.forge17.effects;

import com.seibel.distanthorizons.api.objects.math.DhApiMat4f;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import java.io.File;
import java.lang.reflect.*;
import java.net.*;

/** Run the real native coverage query with each supported Angelica class inventory. */
final class NativeCoverageChecks {
    private static void set(Object object,String name,Object value) throws Exception {
        Field field=object.getClass().getDeclaredField(name);field.setAccessible(true);field.set(object,value);
    }
    static int run(String jar) {
        String name="com.seibel.distanthorizons.forge17.actors.AngelicaTerrainCoverage";
        try(URLClassLoader loader=new URLClassLoader(new URL[]{new File(jar).toURI().toURL(),
            ActorLifecycleChecks.class.getProtectionDomain().getCodeSource().getLocation(),
            com.seibel.distanthorizons.forge17.actors.ModelBounds.class.getProtectionDomain().getCodeSource().getLocation()},NativeCoverageChecks.class.getClassLoader()) {
            @Override protected Class<?> loadClass(String requested,boolean resolve) throws ClassNotFoundException {
                if(requested.equals(name)) { Class<?> type=findLoadedClass(requested);if(type==null) type=findClass(requested);if(resolve) resolveClass(type);return type; }
                return super.loadClass(requested,resolve);
            }
        }) {
            Class<?> type=loader.loadClass(name);Object coverage=type.newInstance();
            Field access=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");access.setAccessible(true);
            sun.misc.Unsafe allocator=(sun.misc.Unsafe)access.get(null);
            Object world=allocator.allocateInstance(net.minecraft.client.multiplayer.WorldClient.class);
            set(coverage,"world",world);set(coverage,"minSection",0);set(coverage,"maxSection",15);
            Field table=type.getDeclaredField("columns");table.setAccessible(true);
            Long2LongOpenHashMap columns=(Long2LongOpenHashMap)table.get(coverage);
            long key=(0L<<32)^(-8 & 0xffffffffL);columns.put(key,1L);
            DhApiMat4f projection=new DhApiMat4f();projection.m00=projection.m11=30;projection.m32=-1;projection.m22=-1.01F;projection.m23=-.1F;
            Method query=type.getMethod("isKnown",net.minecraft.world.World.class,int.class,int.class,int.class,int.class,
                DhApiMat4f.class,double.class,double.class,double.class);
            require(Boolean.TRUE.equals(query.invoke(coverage,world,0,0,32,-128,projection,0.,0.,0.)),"offscreen vertical sections cannot erase covered vehicle pixels");
            columns.clear();
            require(Boolean.FALSE.equals(query.invoke(coverage,world,0,0,32,-128,projection,0.,0.,0.)),"visible native terrain gap remains unknown");
            require(Boolean.TRUE.equals(query.invoke(coverage,world,0,16,32,-128,projection,0.,0.,0.)),"wholly offscreen queried section needs no scene depth");
            require(Boolean.FALSE.equals(query.invoke(coverage,null,0,16,32,-128,projection,0.,0.,0.)),"wrong world cannot authorize coverage");
            return 4;
        } catch(Exception error) { throw new AssertionError("Native coverage regression: "+jar,error); }
    }
    private static void require(boolean value,String message) { if(!value) throw new AssertionError(message); }
}
