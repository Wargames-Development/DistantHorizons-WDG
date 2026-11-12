package com.seibel.distanthorizons.forge17.actors;

/** Optional, render-thread-only Angelica geometry API. No hard link on shader-off/server paths. */
@cpw.mods.fml.relauncher.SideOnly(cpw.mods.fml.relauncher.Side.CLIENT)
public final class ShaderActorLighting {
    private static boolean resolved;
    private static java.lang.reflect.Method begin,refresh;
    private ShaderActorLighting() { }
    public static AutoCloseable begin() throws ReflectiveOperationException {
        if(!resolved) {
            resolved=true;
            try {
                Class<?> api=Class.forName("com.gtnewhorizons.angelica.api.EntityGeometry");
                if(api.getField("API_VERSION").getInt(null)==1) {
                    begin=api.getMethod("begin");refresh=api.getMethod("refresh");
                }
            } catch(ClassNotFoundException | NoSuchMethodException absent) { begin=null; }
        }
        if(begin==null) throw new IllegalStateException("Vehicle shader lighting requires paired Angelica EntityGeometry v1");
        AutoCloseable result=(AutoCloseable)begin.invoke(null);
        if(result==null) throw new IllegalStateException("No compatible entity shader capture in the active scene");
        return result;
    }
    public static void refresh() throws ReflectiveOperationException { refresh.invoke(null); }
}
