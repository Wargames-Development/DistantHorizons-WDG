package com.seibel.distanthorizons.forge17.effects;

import com.seibel.distanthorizons.api.objects.math.DhApiMat4f;
import com.seibel.distanthorizons.forge17.actors.ModelCapture;
import com.seibel.distanthorizons.forge17.actors.ModelProjection;
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
    private static final List<Marker> batch=new ArrayList<>(16);
    private static final ModelCapture capture=new ModelCapture();
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
    static void reset() { pending.clear();beams.clear();batch.clear(); }
    static void draw() {
        if(pending.isEmpty() && beams.isEmpty()) return;
        Minecraft mc=Minecraft.getMinecraft();
        try {
            int draw=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
            if(draw!=mc.getFramebuffer().framebufferObject) return;
            ActorRenderer owner=ActorRenderer.INSTANCE;
            MarkerView view=owner.markerView();
            if(view==null || !ModelCapture.supportsSize(view.width,view.height)
                    || !owner.markerTerrainBegin(view)) return;
            try(ModelCapture.OverlayPass pass=capture.overlays(draw,view.width,view.height,view.dhMvp,view.nativeMvp,view.terrainDepth,view.nativeDepth,view.reverse)) {
                drawBeams(view,pass,mc.theWorld);
                for(Marker marker:pending) {
                    if(marker.world!=mc.theWorld) continue;
                    if(!marker.prepare(view) || !owner.markerKnown(view,marker.x,marker.y,marker.z,marker.radius)) continue;
                    if(batch.size()==16 || marker.overlaps(batch)) compositeMarkers(view,pass);
                    if(!pass.begin(marker.left,marker.bottom,marker.right,marker.top,view.projection)) break;
                    try { marker.draw.run(); }
                    finally { pass.end(); }
                    batch.add(marker);
                }
                compositeMarkers(view,pass);
            } finally { owner.markerTerrainEnd(); }
        } catch(RuntimeException | LinkageError error) {
            failed=true;
            org.apache.logging.log4j.LogManager.getLogger("DistantHorizons").error("DH visual marker pass stopped",error);
        } finally {
            reset();
        }
    }
    private static void compositeMarkers(MarkerView view,ModelCapture.OverlayPass pass) {
        if(batch.isEmpty()) return;
        // Overlapping scissors close the batch before another clear can overwrite those pixels.
        pass.startMarkers(view.inverse);
        try {
            for(Marker marker:batch) {
                pass.marker(marker.left,marker.bottom,marker.right,marker.top,marker.front,marker.centre,marker.anchorX,marker.anchorY);
            }
        } finally {
            pass.endComposite();
            batch.clear();
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
    private static final DhApiMat4f beamProjection=new DhApiMat4f(),beamMvp=new DhApiMat4f(),beamInverse=new DhApiMat4f();
    private static final int[] beamRectangle=new int[4];
    private static void drawBeams(MarkerView view,ModelCapture.OverlayPass pass,Object world) {
        if(beams.isEmpty()) return;
        java.nio.FloatBuffer modelView=columnMajor(view.view);
        for(Beam beam:beams) {
            if(beam.world!=world || !beamBounds(view,beam,beamRectangle)) continue;
            double x=beam.x-view.x,z=beam.z-view.z;
            double a=-(view.view.m20*x+view.view.m21*(beam.bottom-view.y)+view.view.m22*z+view.view.m23);
            double b=-(view.view.m20*x+view.view.m21*(beam.top-view.y)+view.view.m22*z+view.view.m23);
            double near=Math.max(.05,Math.min(a,b)-2),far=Math.max(a,b)+2;
            if(far<=near || far>2000000) continue;
            beamProjection.set(view.projection);
            beamProjection.m22=(float)(-(far+near)/(far-near));beamProjection.m23=(float)(-2*far*near/(far-near));
            beamMvp.set(beamProjection);beamMvp.multiply(view.view);if(!ModelProjection.inverse(beamMvp,beamInverse)) continue;
            if(!pass.begin(beamRectangle[0],beamRectangle[1],beamRectangle[2],beamRectangle[3],beamProjection)) continue;
            try {
                GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glLoadMatrix(modelView);
                GL11.glTranslated(-view.x,-view.y,-view.z);
                beam.draw.run();
            } finally { pass.end(); }
            pass.beam(beamInverse,beam.occlude);
        }
    }
    private static boolean beamBounds(MarkerView view,Beam beam,int[] bounds) {
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
        if(!front) return false;
        bounds[0]=behind?0:Math.max(0,(int)Math.floor((minX+1)*.5*view.width)-2);
        bounds[1]=behind?0:Math.max(0,(int)Math.floor((minY+1)*.5*view.height)-2);
        bounds[2]=behind?view.width:Math.min(view.width,(int)Math.ceil((maxX+1)*.5*view.width)+2);
        bounds[3]=behind?view.height:Math.min(view.height,(int)Math.ceil((maxY+1)*.5*view.height)+2);
        return bounds[2]>bounds[0] && bounds[3]>bounds[1];
    }
    private static final java.nio.FloatBuffer matrix=org.lwjgl.BufferUtils.createFloatBuffer(16);
    private static final float[] rows=new float[16];
    private static java.nio.FloatBuffer columnMajor(DhApiMat4f value) {
        value.putValuesInArray(rows);matrix.clear();
        for(int c=0;c<4;c++) for(int r=0;r<4;r++) matrix.put(rows[r*4+c]);matrix.flip();return matrix;
    }
    private static final class Beam {
        final Object world;final double x,bottom,z,top;final boolean occlude;final Runnable draw;
        Beam(Object world,double x,double bottom,double z,double top,boolean occlude,Runnable draw) { this.world=world;this.x=x;this.bottom=bottom;this.z=z;this.top=top;this.occlude=occlude;this.draw=draw; }
    }
    private static float targetDepth(DhApiMat4f projection,double eye) {
        return (float)(.5*((projection.m22*eye+projection.m23)/-eye+1));
    }
    private static final class Marker {
        final Object world;final double x,y,z,radius;final Runnable draw;
        int left,bottom,right,top;
        float front,centre,anchorX,anchorY;
        Marker(Object world,double x,double y,double z,double radius,int left,int bottom,int right,int top,Runnable draw) { this.world=world;this.x=x;this.y=y;this.z=z;this.radius=radius;this.left=left;this.bottom=bottom;this.right=right;this.top=top;this.draw=draw; }
        boolean prepare(MarkerView view) {
            left=Math.max(0,left);bottom=Math.max(0,bottom);
            right=Math.min(view.width,right);top=Math.min(view.height,top);
            if(left>=right || bottom>=top) return false;
            double dx=x-view.x,dy=y-view.y,dz=z-view.z;
            DhApiMat4f camera=view.view;
            double eye=camera.m20*dx+camera.m21*dy+camera.m22*dz+camera.m23;
            if(eye>=0) return false;
            // Use a conservative front envelope so the target cannot cover its own glyph.
            front=targetDepth(view.projection,Math.min(-.05,eye+radius+.05));
            if(!Float.isFinite(front) || front<0) return false;
            centre=targetDepth(view.projection,eye);
            DhApiMat4f m=view.nativeMvp;
            double w=m.m30*dx+m.m31*dy+m.m32*dz+m.m33;
            anchorX=(float)((m.m00*dx+m.m01*dy+m.m02*dz+m.m03)/w);
            anchorY=(float)((m.m10*dx+m.m11*dy+m.m12*dz+m.m13)/w);
            return true;
        }
        boolean overlaps(List<Marker> otherMarkers) {
            for(Marker other:otherMarkers) {
                if(left<other.right && right>other.left && bottom<other.top && top>other.bottom) return true;
            }
            return false;
        }
    }
    static final class MarkerView {
        final DhApiMat4f nativeMvp=new DhApiMat4f(),dhMvp=new DhApiMat4f(),inverse=new DhApiMat4f();
        DhApiMat4f projection,view;double x,y,z;int terrainDepth,nativeDepth,width,height;boolean reverse;
    }
}
