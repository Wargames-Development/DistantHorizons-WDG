package com.seibel.distanthorizons.forge17.actors;

/** Conservative terrain completeness over the camera/actor envelope, not line-of-sight culling. */
public final class TerrainCorridor {
    public interface Columns { boolean known(int chunkX,int minY,int maxY,int chunkZ); }
    private int remaining;
    public void reset(int chunkBudget) { remaining=Math.max(0,chunkBudget); }
    public int remaining() { return remaining; }
    public boolean known(double cx,double cy,double cz,double x,double y,double z,double radius,Columns source) {
        if(source==null || !coordinate(cx) || !coordinate(cy) || !coordinate(cz) || !coordinate(x) || !coordinate(y)
                || !coordinate(z) || !Double.isFinite(radius) || radius<=0 || radius>128) return false;
        // A square-expanded segment contains every ray from the camera to the actor's
        // bounding cube. A full X/Z rectangle also includes unrelated off-ray terrain.
        int minX=floor(Math.min(cx,x)-radius)>>4,maxX=floor(Math.max(cx,x)+radius)>>4;
        int minY=floor(Math.min(cy,y-radius)),maxY=floor(Math.max(cy,y+radius));
        // Every X slab intersects the expanded segment. Reject enormous inputs before
        // traversing them, and count exact row work before making any provider queries.
        if((long)maxX-minX+1>remaining) return false;
        long count=0;
        for(int sx=minX;sx<=maxX;sx++) {
            long row=row(sx,cx,cz,x,z,radius);
            count+=(long)(int)row-(int)(row>>32)+1;
            if(count>remaining) return false;
        }
        for(int sx=minX;sx<=maxX;sx++) {
            long row=row(sx,cx,cz,x,z,radius);
            for(int sz=(int)(row>>32),maxZ=(int)row;sz<=maxZ;sz++) {
                remaining--;
                if(!source.known(sx,minY,maxY,sz)) return false;
            }
        }
        return true;
    }
    /** Inclusive Z chunk limits packed into the high/low halves without per-row allocations. */
    private static long row(int chunkX,double cx,double cz,double x,double z,double radius) {
        double enter=0,exit=1,vx=x-cx;
        if(vx!=0) {
            double a=(chunkX*16.0-radius-cx)/vx,b=((chunkX+1)*16.0+radius-cx)/vx;
            enter=Math.max(0,Math.min(a,b));exit=Math.min(1,Math.max(a,b));
        }
        double a=cz+(z-cz)*enter,b=cz+(z-cz)*exit;
        int minZ=floor(Math.min(a,b)-radius)>>4,maxZ=floor(Math.max(a,b)+radius)>>4;
        return ((long)minZ<<32)|(maxZ&0xffffffffL);
    }
    private static boolean coordinate(double n) { return Double.isFinite(n) && Math.abs(n)<=30000000; }
    private static int floor(double n) { return (int)Math.floor(n); }
}
