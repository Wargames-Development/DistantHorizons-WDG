package com.seibel.distanthorizons.forge17.effects;

import com.seibel.distanthorizons.api.objects.math.DhApiMat4f;
import com.seibel.distanthorizons.forge17.actors.*;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import java.util.*;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;

/** One isolated FMUR view. Shares authoritative actors, never the primary writable targets. */
final class ActorCameraPass {
    private final ActorRenderer owner;
    private final ModelCapture capture=new ModelCapture();
    private final DhApiMat4f projection=new DhApiMat4f(),view=new DhApiMat4f(),mvp=new DhApiMat4f();
    private final DhApiMat4f localProjection=new DhApiMat4f(),inverse=new DhApiMat4f(),localMvp=new DhApiMat4f();
    private final int[] bounds=new int[4];
    private final Set<UUID> nativeIds=new HashSet<>();
    private final List<ActorModel> models=new ArrayList<>();
    private final List<ActorModel> colourModels=new ArrayList<>();
    private Object colourWorld;
    private long colourFrame=-1;
    private boolean reuseColour;
    private final NativeModel nativeModel=new NativeModel();
    private double x,y,z;
    private float partial,start;
    private int width,height,count;
    private boolean active,heat,shaderLighting;
    private int[] shaderTargets;
    private int shaderFramebuffer,terrainSnapshot;
    private final NativeSceneDepthCopy shaderTerrain = new NativeSceneDepthCopy();
    private final DhApiMat4f shaderMvp = new DhApiMat4f();
    ActorCameraPass(ActorRenderer owner) { this.owner=owner; }
    boolean beginShader(Object world,FloatBuffer p,FloatBuffer m,double x,double y,double z,
                        int width,int height,float partial,int[] targets,FloatBuffer distantProjection) {
        if(targets==null || targets.length!=10 || targets[0]<=0 || targets[3]<=0 || targets[4]<=0 || targets[5]<=0
                || targets[6]!=width || targets[7]!=height || distantProjection==null) return false;
        if(!begin(world,p,m,x,y,z,width,height,partial,16,false)) return false;
        try {
            shaderTargets=targets.clone();
            if(!read(distantProjection,shaderMvp)) { end();return false; }
            shaderMvp.multiply(view);
            terrainSnapshot=shaderTerrain.capture(targets[3]);
            if(terrainSnapshot<=0) { end();return false; }
            shaderLighting=true;return true;
        } catch(RuntimeException | LinkageError failure) { end();throw failure; }
    }
    boolean begin(Object world,FloatBuffer p,FloatBuffer m,double x,double y,double z,int width,int height,float partial,float start,boolean heat) {
        if(active || p==null || m==null || p.limit()<16 || m.limit()<16 || !owner.cameraReady(world) || !ModelCapture.supportsSize(width,height)
            || !Double.isFinite(x+y+z) || !Float.isFinite(partial) || !Float.isFinite(start) || start<1 || start>=4096) return false;
        shaderLighting=false;
        reuseColour=heat && colourWorld==world && colourFrame==DistantSmokeRenderer.frameId()
            && this.x==x && this.y==y && this.z==z && this.width==width && this.height==height
            && same(p,projection) && same(m,view);
        if(!heat) { colourWorld=null;colourFrame=-1;colourModels.clear(); }
        if(!read(p,projection) || !read(m,view)) return false;
        mvp.set(projection);mvp.multiply(view);
        if(!ModelProjection.inverse(mvp,inverse) || !owner.cameraTerrainBegin(mvp,x,y,z)) return false;
        this.x=x;this.y=y;this.z=z;this.width=width;this.height=height;this.partial=partial;this.start=start;this.heat=heat;
        nativeIds.clear();models.clear();count=0;
        try { capture.prepare(width,height);active=true;return true; }
        catch(RuntimeException | LinkageError failure) { owner.cameraTerrainEnd();return false; }
    }
    private static boolean read(FloatBuffer source,DhApiMat4f destination) {
        float[] rows=new float[16];
        for(int row=0;row<4;row++)for(int column=0;column<4;column++) {
            float value=source.get(column*4+row);if(!Float.isFinite(value)) return false;
            rows[row*4+column]=value;
        }
        destination.set(new DhApiMat4f(rows));return true;
    }
    private static boolean same(FloatBuffer source,DhApiMat4f matrix) {
        float[] rows=new float[16];matrix.putValuesInArray(rows);
        for(int row=0;row<4;row++)for(int column=0;column<4;column++)
            if(source.get(column*4+row)!=rows[row*4+column]) return false;
        return true;
    }
    boolean nativeEntity(Entity entity) {
        if(!active || entity==null) return false;
        double radius=entity instanceof EntityPlayer?8:owner.cameraVehicleRadius(entity);
        if(!Double.isFinite(radius) || radius<=0 || radius>128) return false;
        if(count>=128) return true;
        nativeIds.add(entity.getUniqueID());
        if(heat && owner.cameraLocalVehicle(entity)) return true;
        if(count++>=128) return true;
        nativeModel.entity=entity;nativeModel.radius=radius;
        nativeModel.px=entity.lastTickPosX+(entity.posX-entity.lastTickPosX)*partial;
        nativeModel.py=entity.lastTickPosY+(entity.posY-entity.lastTickPosY)*partial;
        nativeModel.pz=entity.lastTickPosZ+(entity.posZ-entity.lastTickPosZ)*partial;
        draw(nativeModel);return true;
    }
    void distant() {
        if(!active) return;
        if(reuseColour) models.addAll(colourModels);else owner.cameraModels(models,nativeIds);
        models.sort((a,b)->shaderLighting?Double.compare(distance(b),distance(a)):Double.compare(distance(a),distance(b)));
        if(!heat) {
            colourModels.addAll(models);colourWorld=net.minecraft.client.Minecraft.getMinecraft().theWorld;
            colourFrame=DistantSmokeRenderer.frameId();
        }
        for(ActorModel model:models) {
            if(count++>=128) break;
            draw(model);
        }
    }
    private double distance(ActorModel model) { double dx=model.x()-x,dy=model.centerY()-y,dz=model.z()-z;return dx*dx+dy*dy+dz*dz; }
    private void draw(ActorModel model) {
        double radius=model.radius();
        if(!Double.isFinite(radius) || radius<=0 || radius>128
            || !ModelBounds.project(mvp,model.x()-x,model.centerY()-y,model.z()-z,radius,width,height,bounds)
            || !owner.cameraTerrainKnown(x,y,z,model)) return;
        try {
            if(!model.prepare()) return;
            double rx=model.x()-x,ry=model.y()-y,rz=model.z()-z;
            double eye=-(view.m20*rx+view.m21*(model.centerY()-y)+view.m22*rz+view.m23);
            if(!ModelProjection.prepare(projection,eye,radius,localProjection)) return;
            localMvp.set(localProjection);localMvp.multiply(view);
            if(!ModelProjection.inverse(localMvp,inverse) || !capture.begin(width,height,bounds[0],bounds[1],bounds[2],bounds[3],localProjection)) return;
            boolean completed=false;
            try { owner.cameraDraw(model,rx,ry,rz,view,shaderLighting);completed=true; }
            finally { capture.end(completed); }
            if(shaderLighting) compositeShader();
            else capture.compositeAuxiliary(inverse,mvp,view,projection,start,heat);
        } catch(Exception | LinkageError failure) {
            throw new IllegalStateException("DH isolated actor draw failed",failure);
        }
    }
    private void compositeShader() {
        int read=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int draw=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        try {
            if(shaderFramebuffer==0) shaderFramebuffer=GL30.glGenFramebuffers();
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,shaderFramebuffer);
            for(int i=0;i<3;i++) GL30.glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER,
                GL30.GL_COLOR_ATTACHMENT0+i,GL11.GL_TEXTURE_2D,shaderTargets[i],0);
            IntBuffer outputs=BufferUtils.createIntBuffer(3);
            for(int i=0;i<3;i++) outputs.put(shaderTargets[i]>0?GL30.GL_COLOR_ATTACHMENT0+i:GL11.GL_NONE);
            outputs.flip();GL20.glDrawBuffers(outputs);
            GL30.glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER,GL30.GL_DEPTH_ATTACHMENT,
                GL11.GL_TEXTURE_2D,shaderTargets[3],0);
            if(GL30.glCheckFramebufferStatus(GL30.GL_DRAW_FRAMEBUFFER)!=GL30.GL_FRAMEBUFFER_COMPLETE)
                throw new IllegalStateException("Incomplete scope actor scene");
            capture.composite(inverse,shaderMvp,mvp,0,0,0,terrainSnapshot,shaderTargets[5],false,true);
            GL30.glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER,GL30.GL_DEPTH_ATTACHMENT,
                GL11.GL_TEXTURE_2D,shaderTargets[4],0);
            GL11.glDrawBuffer(GL11.GL_NONE);
            capture.opaqueDepthOnly(true);
            try { capture.composite(inverse,shaderMvp,mvp,0,0,0,terrainSnapshot,shaderTargets[5],false,true); }
            finally { capture.opaqueDepthOnly(false); }
            // Near native vehicles need native depth too: DH's near plane alone
            // cannot order two cockpit-range surfaces or reconstruct their fog distance.
            // No attached native image is sampled during these depth-only writes.
            capture.opaqueDepthOnly(true);
            try {
                for(int index:new int[]{5,8,9}) {
                    if(shaderTargets[index]<=0) continue;
                    GL30.glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER,GL30.GL_DEPTH_ATTACHMENT,
                        GL11.GL_TEXTURE_2D,shaderTargets[index],0);
                    capture.composite(inverse,shaderMvp,mvp,0,0,0,terrainSnapshot,0,false,false);
                }
            } finally { capture.opaqueDepthOnly(false); }
        } finally {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,read);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,draw);
        }
    }
    void reset() {
        end();colourModels.clear();colourWorld=null;colourFrame=-1;
        if(shaderFramebuffer!=0) { GL30.glDeleteFramebuffers(shaderFramebuffer);shaderFramebuffer=0; }
        shaderTerrain.close();
    }
    void end() { if(active) { active=false;shaderLighting=false;shaderTargets=null;owner.cameraTerrainEnd();nativeIds.clear();models.clear();nativeModel.entity=null; } }
    private final class NativeModel implements ActorModel {
        Entity entity;double px,py,pz,radius;
        public double x(){return px;}public double y(){return py;}public double z(){return pz;}
        public double centerY(){return py+(entity instanceof EntityPlayer?1:0);}public double radius(){return radius;}
        public boolean prepare(){return RenderManager.instance.getEntityRenderObject(entity)!=null;}
        public int light(){return entity.getBrightnessForRender(partial);}public float partial(){return partial;}
        public void draw(double x,double y,double z) {
            Render renderer=RenderManager.instance.getEntityRenderObject(entity);
            renderer.doRender(entity,x,y,z,entity.prevRotationYaw+(entity.rotationYaw-entity.prevRotationYaw)*partial,partial);
        }
    }
}
