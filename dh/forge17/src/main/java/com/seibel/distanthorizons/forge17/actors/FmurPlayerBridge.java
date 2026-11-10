package com.seibel.distanthorizons.forge17.actors;

import cpw.mods.fml.common.Loader;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import net.minecraft.entity.player.EntityPlayerMP;

/** Optional versioned ownership boundary; no direct FMUR class links. */
public final class FmurPlayerBridge {
    private static boolean resolved,present,available;
    private static Class<?> api;
    private static java.lang.reflect.Method poseCapture,auxiliaryView;
    private static void resolve() {
        if(resolved) return;resolved=true;present=Loader.isModLoaded("flansmod");
        if(!present) { available=true;return; }
        try {
            api=Class.forName("com.flansmod.common.playerlod.ExternalPlayerPresentation",false,FmurPlayerBridge.class.getClassLoader());
            available=api.getField("API_VERSION").getInt(null)==3;
            if(available) poseCapture=api.getMethod("capturePose",EntityPlayerMP.class);
        } catch(ReflectiveOperationException | RuntimeException | LinkageError unavailable) { available=false; }
    }
    public static boolean registerServer(Predicate<EntityPlayerMP> owner) {
        resolve();if(!available) return false;if(!present) return true;
        try { api.getMethod("setServerOwner",Predicate.class).invoke(null,owner);return true; }
        catch(ReflectiveOperationException | RuntimeException | LinkageError error) { return false; }
    }
    public static boolean registerClient(BooleanSupplier owner) {
        resolve();if(!available) return false;if(!present) return true;
        try {
            Class<?> client=Class.forName("com.flansmod.client.ExternalPlayerPresentationClient",false,FmurPlayerBridge.class.getClassLoader());
            if(client.getField("API_VERSION").getInt(null)!=3) return false;
            Class<?> views=Class.forName("com.flansmod.client.ThermalRenderApi",false,FmurPlayerBridge.class.getClassLoader());
            auxiliaryView=views.getMethod("isAuxiliaryView");
            api.getMethod("setClientOwner",BooleanSupplier.class).invoke(null,owner);return true;
        }
        catch(ReflectiveOperationException | RuntimeException | LinkageError error) { return false; }
    }
    public static boolean supportsViews() { resolve();return !present || auxiliaryView!=null; }
    public static boolean auxiliaryView() {
        resolve();if(!present) return false;if(auxiliaryView==null) return true;
        try { return Boolean.TRUE.equals(auxiliaryView.invoke(null)); }
        catch(ReflectiveOperationException | RuntimeException | LinkageError unavailable) { return true; }
    }
    public static byte[] capturePose(EntityPlayerMP player) {
        resolve();if(!present) return new byte[0];
        if(!available) throw new IllegalArgumentException("FMUR presentation API unavailable");
        try {
            byte[] data=(byte[])poseCapture.invoke(null,player);
            if(data==null || data.length>PlayerFrame.MAX_EXTENSION_BYTES) throw new IllegalArgumentException("Invalid FMUR pose size");
            return data;
        } catch(ReflectiveOperationException error) { throw new IllegalArgumentException("FMUR pose capture failed",error); }
    }
    public static void appendAppearance(EntityPlayerMP player,net.minecraft.nbt.NBTTagCompound root) throws java.io.IOException {
        resolve();if(!present) return;
        if(!available) throw new java.io.IOException("FMUR presentation API unavailable");
        try {
            root.setTag("fmur",(net.minecraft.nbt.NBTTagCompound)api.getMethod("captureAppearance",EntityPlayerMP.class).invoke(null,player));
        } catch(ReflectiveOperationException | RuntimeException error) { throw new java.io.IOException("FMUR appearance capture failed",error); }
    }
    public static Visual createVisual(net.minecraft.entity.player.EntityPlayer actor) throws ReflectiveOperationException {
        resolve();if(!present) return new Visual(null);
        if(!available) throw new IllegalStateException("FMUR presentation API unavailable");
        Class<?> client=Class.forName("com.flansmod.client.ExternalPlayerPresentationClient",false,FmurPlayerBridge.class.getClassLoader());
        return new Visual(client.getMethod("create",net.minecraft.entity.player.EntityPlayer.class).invoke(null,actor));
    }
    public static final class Visual implements AutoCloseable {
        private final Object context;
        private final java.lang.reflect.Method begin,appearance,close,pose;
        private Visual(Object context) throws ReflectiveOperationException {
            this.context=context;Class<?> type=context==null?null:context.getClass();
            begin=type==null?null:type.getMethod("begin");close=type==null?null:type.getMethod("close");
            appearance=type==null?null:type.getMethod("appearance",net.minecraft.nbt.NBTTagCompound.class,boolean.class);
            pose=type==null?null:type.getMethod("pose",byte[].class,byte[].class,float.class,float.class);
        }
        public void appearance(net.minecraft.nbt.NBTTagCompound data,boolean enabled) throws ReflectiveOperationException {
            if(context!=null) appearance.invoke(context,data,enabled);
        }
        public void pose(PlayerTrack.Sample sample) throws ReflectiveOperationException {
            if(context!=null && sample.first!=null) pose.invoke(context,sample.first.extension(),sample.last.extension(),sample.fraction,sample.age);
        }
        public AutoCloseable begin() throws ReflectiveOperationException {
            return context==null?null:(AutoCloseable)begin.invoke(context);
        }
        public void close() {
            if(context!=null) try { close.invoke(context); }
            catch(ReflectiveOperationException error) { throw new IllegalStateException("FMUR actor cleanup failed",error); }
        }
    }
    public static boolean present() { resolve();return present; }
}
