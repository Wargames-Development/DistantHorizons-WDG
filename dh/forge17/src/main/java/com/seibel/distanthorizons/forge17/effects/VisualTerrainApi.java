package com.seibel.distanthorizons.forge17.effects;

import com.seibel.distanthorizons.forge17.actors.ActorModel;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.util.function.DoubleConsumer;
import net.minecraft.client.Minecraft;

/** Render-thread geometry submission. Companions retain authorization, range and pose ownership. */
@SideOnly(Side.CLIENT)
public final class VisualTerrainApi {
    public static final int API_VERSION=1;
    public static final int PLAYER=1, VEHICLE=2, MAX_MODELS=256;
    private static final DoubleConsumer[] collectors=new DoubleConsumer[2];
    private static boolean collecting;
    private static Thread renderThread;
    static void beginFrame() { renderThread=Thread.currentThread(); }
    private VisualTerrainApi() { }
    public static void register(int kind,DoubleConsumer collector) {
        if(kind!=PLAYER && kind!=VEHICLE) throw new IllegalArgumentException("Unknown visual kind");
        collectors[kind-1]=collector;
    }
    public static boolean ready() {
        return Thread.currentThread()==renderThread && ActorRenderer.INSTANCE!=null
            && ActorRenderer.INSTANCE.cameraReady(Minecraft.getMinecraft().theWorld);
    }
    public static boolean collecting() {
        return Thread.currentThread()==renderThread && (collecting
            || ActorRenderer.INSTANCE!=null && ActorRenderer.INSTANCE.cameraPass.isActive());
    }
    static void collect(float partial) {
        if(collecting) throw new IllegalStateException("Nested visual collection");
        collecting=true;
        try { for(DoubleConsumer collector:collectors) if(collector!=null) collector.accept(partial); }
        finally { collecting=false; }
    }
    /** Accepted geometry is retained only until the current view finishes. Unknown terrain remains hidden. */
    public static boolean submit(Object world,int kind,double x,double y,double z,double centerY,double radius,
                                 float partial,int light,Runnable geometry) {
        if(!collecting() || !ready() || world!=Minecraft.getMinecraft().theWorld || geometry==null
                || (kind!=PLAYER && kind!=VEHICLE) || !Double.isFinite(x+y+z+centerY+radius)
                || radius<=0 || radius>256 || !Float.isFinite(partial)) return false;
        return ActorRenderer.INSTANCE.submit(new SubmittedModel(kind,x,y,z,centerY,radius,partial,light,geometry));
    }
    static final class SubmittedModel implements ActorModel {
        final int kind;
        private final double x,y,z,center,radius;
        private final float partial;
        private final int light;
        private final Runnable geometry;
        SubmittedModel(int kind,double x,double y,double z,double center,double radius,float partial,int light,Runnable geometry) {
            this.kind=kind;this.x=x;this.y=y;this.z=z;this.center=center;this.radius=radius;
            this.partial=partial;this.light=light;this.geometry=geometry;
        }
        public double x(){return x;} public double y(){return y;} public double z(){return z;}
        public double centerY(){return center;} public double radius(){return radius;}
        public float partial(){return partial;} public int light(){return light;}
        public boolean prepare(){return true;}
        public void draw(double x,double y,double z){geometry.run();}
    }
}
