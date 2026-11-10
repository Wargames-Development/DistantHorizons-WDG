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
        int minX=floor(Math.min(cx,x-radius))>>4,maxX=floor(Math.max(cx,x+radius))>>4;
        int minZ=floor(Math.min(cz,z-radius))>>4,maxZ=floor(Math.max(cz,z+radius))>>4;
        int minY=floor(Math.min(cy,y-radius)),maxY=floor(Math.max(cy,y+radius));
        long count=(long)(maxX-minX+1)*(maxZ-minZ+1);
        if(count>remaining) return false;
        for(int sx=minX;sx<=maxX;sx++) for(int sz=minZ;sz<=maxZ;sz++) {
            remaining--;
            if(!source.known(sx,minY,maxY,sz)) return false;
        }
        return true;
    }
    private static boolean coordinate(double n) { return Double.isFinite(n) && Math.abs(n)<=30000000; }
    private static int floor(double n) { return (int)Math.floor(n); }
}
