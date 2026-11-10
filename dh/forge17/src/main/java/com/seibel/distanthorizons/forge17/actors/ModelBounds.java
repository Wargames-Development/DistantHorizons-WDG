package com.seibel.distanthorizons.forge17.actors;

import com.seibel.distanthorizons.api.objects.math.DhApiMat4f;

/** Conservative screen rectangle for a world-aligned model envelope in camera-relative space. */
public final class ModelBounds {
    private ModelBounds() { }
    public static boolean project(DhApiMat4f m,double x,double y,double z,double radius,int width,int height,int[] out) {
        if(out.length<4 || width<=0 || height<=0 || !Double.isFinite(x+y+z+radius) || radius<=0) return false;
        double minX=Double.POSITIVE_INFINITY,minY=minX,maxX=Double.NEGATIVE_INFINITY,maxY=maxX;
        int behind=0;
        for(int i=0;i<8;i++) {
            double a=x+((i&1)==0?-radius:radius),b=y+((i&2)==0?-radius:radius),c=z+((i&4)==0?-radius:radius);
            double w=m.m30*a+m.m31*b+m.m32*c+m.m33;
            if(w<=.000001) { behind++;continue; }
            double px=(m.m00*a+m.m01*b+m.m02*c+m.m03)/w;
            double py=(m.m10*a+m.m11*b+m.m12*c+m.m13)/w;
            if(!Double.isFinite(px+py)) return false;
            minX=Math.min(minX,px);maxX=Math.max(maxX,px);minY=Math.min(minY,py);maxY=Math.max(maxY,py);
        }
        if(behind==8) return false;
        if(behind>0) { out[0]=out[1]=0;out[2]=width;out[3]=height;return true; }
        if(maxX< -1 || minX>1 || maxY< -1 || minY>1) return false;
        out[0]=(int)Math.max(0,Math.floor((minX*.5+.5)*width)-1);
        out[1]=(int)Math.max(0,Math.floor((minY*.5+.5)*height)-1);
        out[2]=(int)Math.min(width,Math.ceil((maxX*.5+.5)*width)+1);
        out[3]=(int)Math.min(height,Math.ceil((maxY*.5+.5)*height)+1);
        return out[2]>out[0] && out[3]>out[1];
    }
}
