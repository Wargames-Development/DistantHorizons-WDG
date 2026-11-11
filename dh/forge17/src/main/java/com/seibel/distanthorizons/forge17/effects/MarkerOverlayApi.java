package com.seibel.distanthorizons.forge17.effects;

import com.seibel.distanthorizons.api.objects.math.DhApiMat4f;
import com.seibel.distanthorizons.forge17.actors.ModelCapture;
import com.seibel.distanthorizons.forge17.actors.ModelProjection;
import com.seibel.distanthorizons.common.render.openGl.glObject.GLState;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.*;

/** Optional, render-thread-only visual marker handoff. Does not select or authorize targets. */
@SideOnly(Side.CLIENT)
public final class MarkerOverlayApi {
    public static final int API_VERSION=1;
    private static final List<Marker> pending=new ArrayList<>();
    private static final ModelCapture capture=new ModelCapture();
    private static int nativeCopy;
    private static boolean failed;
    private static final List<Beam> beams=new ArrayList<>();
    private MarkerOverlayApi() { }
    /** True means DH owns this glyph for this frame, including unknown coverage. */
    public static boolean enqueue(Object world,double x,double y,double z,double radius,int left,int bottom,int right,int top,Runnable draw) {
        ActorRenderer owner=ActorRenderer.INSTANCE;
        if(failed || owner==null || !owner.cameraReady(world)) return false;
        if(draw==null || !Double.isFinite(x+y+z+radius) || radius<0 || radius>128 || pending.size()>=128) return true;
        pending.add(new Marker(world,x,y,z,radius,left,bottom,right,top,draw));return true;
    }
    static void reset() { pending.clear();beams.clear(); }
    static void draw() {
        if(pending.isEmpty() && beams.isEmpty()) return;
        Minecraft mc=Minecraft.getMinecraft();
        if(GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)!=mc.getFramebuffer().framebufferObject) { pending.clear();beams.clear();return; }
        int read=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING),draw=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int mode=GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        try(GLState saved=new GLState()) {
            GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
            GL11.glMatrixMode(GL11.GL_PROJECTION);GL11.glPushMatrix();GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glPushMatrix();
            try {
                ActorRenderer owner=ActorRenderer.INSTANCE;
                MarkerView view=owner.markerView();if(view==null || !ModelCapture.supportsSize(view.width,view.height)) return;
                int nativeDepth=view.nativeDepth;
                if(nativeDepth<=0) {
                    if(nativeCopy==0) nativeCopy=GL11.glGenTextures();
                    GL13.glActiveTexture(GL13.GL_TEXTURE0);GL11.glBindTexture(GL11.GL_TEXTURE_2D,nativeCopy);
                    GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);
                    GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);
                    GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,draw);
                    GL11.glCopyTexImage2D(GL11.GL_TEXTURE_2D,0,GL14.GL_DEPTH_COMPONENT24,0,0,view.width,view.height,0);
                    nativeDepth=nativeCopy;
                }
                capture.prepare(view.width,view.height);
                if(!owner.cameraTerrainBegin(view.nativeMvp,view.x,view.y,view.z)) return;
                try {
                    drawBeams(view,nativeDepth);
                    for(Marker marker:pending) {
                        if(marker.world!=mc.theWorld || !owner.markerKnown(view,marker.x,marker.y,marker.z,marker.radius)) continue;
                        float front=targetDepth(view,marker,true);if(!Float.isFinite(front) || front<0) continue;
                        if(!capture.begin(view.width,view.height,Math.max(0,marker.left),Math.max(0,marker.bottom),Math.min(view.width,marker.right),Math.min(view.height,marker.top),view.projection)) continue;
                        boolean complete=false;
                        try { GL20.glUseProgram(0);marker.draw.run();complete=true; }
                        finally { capture.end(complete); }
                        capture.compositeMarker(view.inverse,view.dhMvp,view.nativeMvp,view.terrainDepth,nativeDepth,view.reverse,front,targetDepth(view,marker,false),
                            projected(view.nativeMvp,marker.x-view.x,marker.y-view.y,marker.z-view.z,true),
                            projected(view.nativeMvp,marker.x-view.x,marker.y-view.y,marker.z-view.z,false));
                    }
                } finally { owner.cameraTerrainEnd(); }
            } finally {
                GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glPopMatrix();GL11.glMatrixMode(GL11.GL_PROJECTION);GL11.glPopMatrix();
                GL11.glMatrixMode(mode);GL11.glPopAttrib();
            }
        } catch(RuntimeException | LinkageError error) {
            failed=true;
            org.apache.logging.log4j.LogManager.getLogger("DistantHorizons").error("DH visual marker pass stopped",error);
        } finally {
            pending.clear();beams.clear();GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,read);GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,draw);
        }
    }
    /** Optional WGMap beam handoff. Callback draws absolute world geometry on this frame only.
     * Unlike BVR glyphs, every beam fragment retains its physical destination depth. */
    public static boolean drawBeam(Object world,double x,double bottom,double z,double top,boolean occlude,Runnable draw) {
        ActorRenderer owner=ActorRenderer.INSTANCE;
        if(failed || owner==null || !owner.cameraReady(world)) return false;
        if(draw==null || !Double.isFinite(x+bottom+z+top) || top<=bottom || top-bottom>1000000 || beams.size()>=128) return true;
        beams.add(new Beam(world,x,bottom,z,top,occlude,draw));draw();return true;
    }
    private static void drawBeams(MarkerView view,int nativeDepth) {
        DhApiMat4f projection=new DhApiMat4f(),mvp=new DhApiMat4f(),inverse=new DhApiMat4f();
        for(Beam beam:beams) {
            if(beam.world!=Minecraft.getMinecraft().theWorld) continue;
            double x=beam.x-view.x,z=beam.z-view.z;
            double a=-(view.view.m20*x+view.view.m21*(beam.bottom-view.y)+view.view.m22*z+view.view.m23);
            double b=-(view.view.m20*x+view.view.m21*(beam.top-view.y)+view.view.m22*z+view.view.m23);
            double near=Math.max(.05,Math.min(a,b)-2),far=Math.max(a,b)+2;
            if(far<=near || far>2000000) continue;
            projection.set(view.projection);
            projection.m22=(float)(-(far+near)/(far-near));projection.m23=(float)(-2*far*near/(far-near));
            mvp.set(projection);mvp.multiply(view.view);if(!ModelProjection.inverse(mvp,inverse)) continue;
            int[] bounds=beamBounds(view,beam);if(bounds==null) continue;
            if(!capture.begin(view.width,view.height,bounds[0],bounds[1],bounds[2],bounds[3],projection)) continue;
            boolean complete=false;
            try {
                GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glLoadMatrix(columnMajor(view.view));
                GL11.glTranslated(-view.x,-view.y,-view.z);
                GL20.glUseProgram(0);beam.draw.run();complete=true;
            } finally { capture.end(complete); }
            capture.compositeWorldOverlay(inverse,view.dhMvp,view.nativeMvp,view.terrainDepth,nativeDepth,view.reverse,beam.occlude);
        }
    }
    private static int[] beamBounds(MarkerView view,Beam beam) {
        DhApiMat4f m=view.nativeMvp;
        double minX=1,minY=1,maxX=-1,maxY=-1;
        boolean front=false,behind=false;
        for(int corner=0;corner<8;corner++) {
            double x=beam.x-view.x+((corner&1)==0?-1:1);
            double y=((corner&2)==0?beam.bottom:beam.top)-view.y;
            double z=beam.z-view.z+((corner&4)==0?-1:1);
            double w=m.m30*x+m.m31*y+m.m32*z+m.m33;
            if(w<=.05) { behind=true;continue; }
            front=true;
            double px=(m.m00*x+m.m01*y+m.m02*z+m.m03)/w;
            double py=(m.m10*x+m.m11*y+m.m12*z+m.m13)/w;
            minX=Math.min(minX,px);maxX=Math.max(maxX,px);minY=Math.min(minY,py);maxY=Math.max(maxY,py);
        }
        if(!front) return null;
        if(behind) return new int[]{0,0,view.width,view.height};
        int left=Math.max(0,(int)Math.floor((minX+1)*.5*view.width)-2);
        int bottom=Math.max(0,(int)Math.floor((minY+1)*.5*view.height)-2);
        int right=Math.min(view.width,(int)Math.ceil((maxX+1)*.5*view.width)+2);
        int top=Math.min(view.height,(int)Math.ceil((maxY+1)*.5*view.height)+2);
        return right>left && top>bottom?new int[]{left,bottom,right,top}:null;
    }
    private static final java.nio.FloatBuffer matrix=org.lwjgl.BufferUtils.createFloatBuffer(16);
    private static java.nio.FloatBuffer columnMajor(DhApiMat4f value) {
        float[] rows=new float[16];value.putValuesInArray(rows);matrix.clear();
        for(int c=0;c<4;c++) for(int r=0;r<4;r++) matrix.put(rows[r*4+c]);matrix.flip();return matrix;
    }
    private static final class Beam {
        final Object world;final double x,bottom,z,top;final boolean occlude;final Runnable draw;
        Beam(Object world,double x,double bottom,double z,double top,boolean occlude,Runnable draw) { this.world=world;this.x=x;this.bottom=bottom;this.z=z;this.top=top;this.occlude=occlude;this.draw=draw; }
    }
    private static float projected(DhApiMat4f m,double x,double y,double z,boolean horizontal) {
        double w=m.m30*x+m.m31*y+m.m32*z+m.m33;
        return (float)((horizontal?m.m00*x+m.m01*y+m.m02*z+m.m03:m.m10*x+m.m11*y+m.m12*z+m.m13)/w);
    }
    private static float targetDepth(MarkerView view,Marker marker,boolean front) {
        DhApiMat4f m=view.view,p=view.projection;
        double eye=m.m20*(marker.x-view.x)+m.m21*(marker.y-view.y)+m.m22*(marker.z-view.z)+m.m23;
        if(eye>=0) return Float.NaN;
        // Use a conservative front envelope so the target cannot cover its own glyph.
        if(front) eye=Math.min(-.05,eye+marker.radius+.05);
        return (float)(.5*((p.m22*eye+p.m23)/-eye+1));
    }
    private static final class Marker {
        final Object world;final double x,y,z,radius;final int left,bottom,right,top;final Runnable draw;
        Marker(Object world,double x,double y,double z,double radius,int left,int bottom,int right,int top,Runnable draw) { this.world=world;this.x=x;this.y=y;this.z=z;this.radius=radius;this.left=left;this.bottom=bottom;this.right=right;this.top=top;this.draw=draw; }
    }
    static final class MarkerView {
        final DhApiMat4f nativeMvp=new DhApiMat4f(),dhMvp=new DhApiMat4f(),inverse=new DhApiMat4f();
        DhApiMat4f projection,view;double x,y,z;int terrainDepth,nativeDepth,width,height;boolean reverse;
    }
}
