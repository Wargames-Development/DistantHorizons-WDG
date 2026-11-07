package com.seibel.distanthorizons.forge17.effects;

import java.util.Arrays;
import java.util.List;
import java.util.WeakHashMap;
import net.minecraft.client.particle.EntityFX;

/** Bounded observation only. Counts geometry, never claims that submitted quads produced pixels. */
public final class NtmTrace
{
    private static final int BINS=7, KINDS=3;
    private static final String[] LABELS={"<128","128-192","192-256","256-320","320-512",">=512","unavailable"};
    private static final WeakHashMap<EntityFX,Integer> origins=new WeakHashMap<EntityFX,Integer>();
    private static final int[] live=new int[BINS*KINDS], dispatch=new int[BINS*KINDS],
        near=new int[BINS*KINDS],far=new int[BINS*KINDS],selected=new int[BINS*KINDS];
    private static final long[] input=new long[BINS],fullTrim=new long[BINS],partialTrim=new long[BINS],births=new long[BINS*KINDS];
    private static final int[] ageMin=new int[BINS*KINDS],ageMax=new int[BINS*KINDS];
    private static long queueEvictions;
    private static int scans,unclassified,examined;
    private static int bodies,trackedBodies,nativeOwners,nativeDraws,proxyDraws,lastBodyId;
    private NtmTrace() { }
    public static int bin(double depth) {
        return !Double.isFinite(depth)?6:depth<128?0:depth<192?1:depth<256?2:depth<320?3:depth<512?4:5;
    }
    public static int origin(EntityFX p) {
        Integer value=origins.get(p);return value==null?2:value;
    }
    private static int index(EntityFX p) {
        return bin(DistantSmokeRenderer.cameraDepth(p.posX,p.posY,p.posZ))*KINDS+origin(p);
    }
    public static void born(EntityFX p) {
        if(EffectSources.family(p)!=TrailSample.HBM || !EffectSources.enabled(TrailSample.HBM)) return;
        EffectOrigin.Source s=EffectOrigin.get();
        int kind=s==null?2:s.remote?1:0;
        if(origins.size()<4096) origins.put(p,kind);else unclassified++;
        births[bin(DistantSmokeRenderer.cameraDepth(p.posX,p.posY,p.posZ))*KINDS+kind]++;
    }
    public static void delivery(TrailSample s,double covered) {
        int b=bin(DistantSmokeRenderer.cameraDepth(s.x,s.y,s.z));
        input[b]++;
        if(covered>=1) fullTrim[b]++;else if(covered>0) partialTrim[b]++;
    }
    public static synchronized void queueEvicted() { queueEvictions++; }
    public static void live(List[] layers) {
        if(++scans%5!=0 || !EffectSources.enabled(TrailSample.HBM)) return;
        Arrays.fill(live,0);Arrays.fill(ageMin,Integer.MAX_VALUE);Arrays.fill(ageMax,-1);examined=0;
        for(List layer:layers) for(Object o:layer) {
            if(++examined>16384) return;
            if(o instanceof EntityFX) {
                EntityFX p=(EntityFX)o;
                if(!p.isDead && EffectSources.family(p)==TrailSample.HBM) {
                    int i=index(p);live[i]++;
                    if(p.getClass().getName().equals("com.hbm.particle.ParticleRocketFlame")) try {
                        int age=((Number)TrailSources.field(p,"age")).intValue();
                        ageMin[i]=Math.min(ageMin[i],age);ageMax[i]=Math.max(ageMax[i],age);
                    } catch(ReflectiveOperationException | RuntimeException ignored) { }
                }
            }
        }
    }
    public static void frame() {
        Arrays.fill(dispatch,0);Arrays.fill(near,0);Arrays.fill(far,0);Arrays.fill(selected,0);
        bodies=trackedBodies=nativeOwners=nativeDraws=proxyDraws=0;lastBodyId=-1;
    }
    static void body(int id,boolean tracked,boolean nativeOwner,boolean nativeDraw,boolean proxyDraw) {
        bodies++;lastBodyId=id;if(tracked) trackedBodies++;if(nativeOwner) nativeOwners++;
        if(nativeDraw) nativeDraws++;if(proxyDraw) proxyDraws++;
    }
    public static void dispatched(EntityFX p) {
        if(EffectSources.family(p)==TrailSample.HBM) dispatch[index(p)]++;
    }
    public static void geometry(EntityFX p,int[] raw,int start,int end,
        com.seibel.distanthorizons.api.objects.math.DhApiMat4f m) {
        if(EffectSources.family(p)!=TrailSample.HBM) return;
        for(int v=start;v+31<end;v+=32) {
            boolean outside=false;
            for(int c=0;c<4;c++) {
                int i=v+c*8;double x=Float.intBitsToFloat(raw[i]),y=Float.intBitsToFloat(raw[i+1]),z=Float.intBitsToFloat(raw[i+2]);
                double w=m.m30*x+m.m31*y+m.m32*z+m.m33;
                outside|=w>0 && m.m20*x+m.m21*y+m.m22*z+m.m23>w;
            }
            int i=quadBin(raw,v,m)*KINDS+origin(p);
            (outside?far:near)[i]++;
        }
    }
    public static int quadBin(int[] raw,int v,com.seibel.distanthorizons.api.objects.math.DhApiMat4f m) {
        double x=0,y=0,z=0;
        for(int c=0;c<4;c++) {int i=v+c*8;x+=Float.intBitsToFloat(raw[i]);y+=Float.intBitsToFloat(raw[i+1]);z+=Float.intBitsToFloat(raw[i+2]);}
        return bin((m.m30*x+m.m31*y+m.m32*z)*0.25+m.m33);
    }
    public static void selected(int bin,int origin) { selected[bin*KINDS+origin]++; }
    private static String triplet(int[] values,int b) {int i=b*KINDS;return values[i]+"/"+values[i+1]+"/"+values[i+2];}
    private static String triplet(long[] values,int b) {int i=b*KINDS;return values[i]+"/"+values[i+1]+"/"+values[i+2];}
    private static String ages(int b) {
        StringBuilder s=new StringBuilder();
        for(int k=0;k<KINDS;k++) {if(k>0) s.append('/');int i=b*KINDS+k;
            if(live[i]==0 || ageMax[i]<0) s.append('-');else s.append(ageMin[i]).append('-').append(ageMax[i]);}
        return s.toString();
    }
    public static synchronized String snapshot() {
        StringBuilder s=new StringBuilder("NTM stages v2 depth=clipW metres; N/R/U=native/relay/unattributed; input/trim/born=cumulative; queueEvicted="+queueEvictions+" unclassified="+unclassified+" liveScan="+examined
            +" body calls/tracked/nativeOwner/nativeObserved/proxySelected="+bodies+"/"+trackedBodies+"/"+nativeOwners+"/"+nativeDraws+"/"+proxyDraws+" lastId="+lastBodyId);
        for(int b=0;b<BINS;b++) s.append(" | ").append(LABELS[b]).append(": in/full/part=").append(input[b]).append('/').append(fullTrim[b]).append('/').append(partialTrim[b])
            .append(" born=").append(triplet(births,b)).append(" live=").append(triplet(live,b)).append(" flameAge=").append(ages(b)).append(" calls=").append(triplet(dispatch,b))
            .append(" near=").append(triplet(near,b)).append(" far=").append(triplet(far,b)).append(" selected=").append(triplet(selected,b));
        return s.toString();
    }
    public static synchronized void clear() {
        origins.clear();Arrays.fill(live,0);Arrays.fill(input,0);Arrays.fill(fullTrim,0);Arrays.fill(partialTrim,0);Arrays.fill(births,0);
        Arrays.fill(ageMin,Integer.MAX_VALUE);Arrays.fill(ageMax,-1);frame();queueEvictions=0;scans=0;unclassified=0;examined=0;
    }
}
