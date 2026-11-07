package com.seibel.distanthorizons.forge17.effects;

import java.io.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.ResourceLocation;

/** Cached original NTM OBJ geometry. CPU transforms keep DH capture in world coordinates. */
final class NativeMissileMesh {
    private static final Map<String,Double> RADII=new HashMap<String,Double>();
    private static final Map<String,List<double[]>> CACHE=new HashMap<String,List<double[]>>();
    private static final Set<String> NATIVE_RENDERERS=new HashSet<String>(Arrays.asList(
        "EntityMissileGeneric","EntityMissileDecoy","EntityMissileIncendiary","EntityMissileCluster","EntityMissileBunkerBuster","EntityMissileStealth",
        "EntityMissileStrong","EntityMissileIncendiaryStrong","EntityMissileClusterStrong","EntityMissileBusterStrong","EntityMissileEMPStrong",
        "EntityMissileBurst","EntityMissileInferno","EntityMissileRain","EntityMissileDrill",
        "EntityMissileNuclear","EntityMissileMirv","EntityMissileVolcano","EntityMissileDoomsday","EntityMissileDoomsdayRusted"));
    static boolean replacesNative(String variant) { return NATIVE_RENDERERS.contains(variant) && !load(model(variant)).isEmpty(); }
    static double radius(String variant) {
        String name=model(variant);Double cached=RADII.get(name);
        if(cached==null) {
            double squared=0;
            for(double[] vertex:load(name)) squared=Math.max(squared,vertex[0]*vertex[0]+vertex[1]*vertex[1]+vertex[2]*vertex[2]);
            cached=Math.sqrt(squared)+1;RADII.put(name,cached);
        }
        return cached*(name.equals("strong")?1.5:1);
    }
    private static String model(String v) {
        if(v.contains("Strong")) return "strong";
        if(v.contains("Burst") || v.contains("Inferno") || v.contains("Rain") || v.contains("Drill")) return "huge";
        if(v.contains("Nuclear") || v.contains("Mirv") || v.contains("Doomsday") || v.contains("Volcano")) return "atlas";
        if(v.contains("Micro")) return "micro";
        if(v.contains("Stealth")) return "stealth";
        return "v2";
    }
    static ResourceLocation texture(String variant) {
        String m=model(variant),suffix="";
        if(m.equals("atlas")) suffix=variant.contains("Mirv")?"_thermo":variant.contains("Volcano")?"_tectonic":variant.contains("Doomsday")?"_doomsday":"_nuclear";
        else if(variant.contains("Incendiary") || variant.contains("Inferno")) suffix="_inc";
        else if(variant.contains("Cluster") || variant.contains("Rain")) suffix="_cl";
        else if(variant.contains("Buster") || variant.contains("Drill")) suffix="_bu";
        else if(variant.contains("Decoy")) suffix="_decoy";
        else if(variant.contains("EMP")) suffix="_emp";
        return new ResourceLocation("hbm:textures/models/missile/missile_"+m+suffix+".png");
    }
    private static List<double[]> load(String model) {
        List<double[]> mesh=CACHE.get(model);if(mesh!=null) return mesh;
        mesh=new ArrayList<double[]>();CACHE.put(model,mesh);
        List<double[]> positions=new ArrayList<double[]>(),uvs=new ArrayList<double[]>();
        try(BufferedReader reader=new BufferedReader(new InputStreamReader(Minecraft.getMinecraft().getResourceManager().getResource(new ResourceLocation("hbm:models/missile_"+model+".obj")).getInputStream(),java.nio.charset.StandardCharsets.UTF_8))) {
            String line;
            while((line=reader.readLine())!=null) {
                String[] fields=line.trim().split("\\s+");
                if(fields[0].equals("v")) positions.add(new double[]{Double.parseDouble(fields[1]),Double.parseDouble(fields[2]),Double.parseDouble(fields[3])});
                else if(fields[0].equals("vt")) uvs.add(new double[]{Double.parseDouble(fields[1]),1-Double.parseDouble(fields[2])});
                else if(fields[0].equals("f")) {
                    for(int i=2;i<fields.length-1;i++) for(int index:new int[]{1,i,i+1,i+1}) {
                        String[] ids=fields[index].split("/");
                        double[] v=positions.get(Integer.parseInt(ids[0])-1),uv=uvs.get(Integer.parseInt(ids[1])-1);
                        mesh.add(new double[]{v[0],v[1],v[2],uv[0],uv[1]});
                    }
                }
            }
        } catch(IOException | RuntimeException error) { mesh.clear();org.apache.logging.log4j.LogManager.getLogger("DistantHorizons").warn("Cannot load original NTM missile mesh: "+model,error); }
        return mesh;
    }
    static void draw(Tessellator t,String variant,double x,double y,double z,double ax,double ay,double az) {
        List<double[]> mesh=load(model(variant));if(mesh.isEmpty()) return;
        // Native renderer rotates around horizontal flight azimuth; local Y is the nose axis.
        double pitch=Math.acos(Math.max(-1,Math.min(1,ay))),yaw=Math.atan2(az,ax);
        double c=Math.cos(pitch),s=Math.sin(pitch),cy=Math.cos(yaw),sy=Math.sin(yaw);
        double scale=model(variant).equals("strong")?1.5:1;
        t.startDrawingQuads();t.setBrightness(15728880);t.setColorRGBA_F(0.62F,0.62F,0.62F,1);
        int vertex=0;
        for(double[] v:mesh) {
            if((vertex++ & 3)==0) {
                double[] b=mesh.get(vertex),d=mesh.get(vertex+1);
                double ux=b[0]-v[0],uy=b[1]-v[1],uz=b[2]-v[2];
                double vx=d[0]-v[0],vy=d[1]-v[1],vz=d[2]-v[2];
                double nx=uy*vz-uz*vy,ny=uz*vx-ux*vz,nz=ux*vy-uy*vx;
                double length=Math.sqrt(nx*nx+ny*ny+nz*nz);
                double radial=nx*cy+nz*sy,side=-nx*sy+nz*cy;
                double rotated=radial*c+ny*s;
                double wx=rotated*cy-side*sy,wy=ny*c-radial*s,wz=rotated*sy+side*cy;
                float shade=(float)(0.62*(0.72+0.28*Math.max(0,length>0?(wx*0.35+wy*0.8+wz*0.45)/length:0)));
                t.setColorRGBA_F(shade,shade,shade,1);
            }
            double vx=v[0]*scale,vy=v[1]*scale,vz=v[2]*scale;
            double radial=vx*cy+vz*sy,side=-vx*sy+vz*cy;
            double rotated=radial*c+vy*s;
            t.addVertexWithUV(x+rotated*cy-side*sy,y+vy*c-radial*s,z+rotated*sy+side*cy,v[3],v[4]);
        }
        t.draw();
    }
}
