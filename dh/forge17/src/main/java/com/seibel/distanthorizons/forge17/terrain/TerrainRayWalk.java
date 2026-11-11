package com.seibel.distanthorizons.forge17.terrain;
/** Bounded voxel traversal; unknown prefixes never establish a surface behind them. */
public final class TerrainRayWalk {
    public interface Cells { int cell(int x,int y,int z); }
    private TerrainRayWalk() { }
    public static DhTerrainRayApi.Result trace(double x0,double y0,double z0,double x1,double y1,double z1,Cells cells,double[] hit) {
        if(hit==null || hit.length<3 || cells==null || !Double.isFinite(x0+y0+z0+x1+y1+z1)) return DhTerrainRayApi.Result.UNKNOWN;
        java.util.Arrays.fill(hit,0,3,Double.NaN);
        double[] start={x0,y0,z0},delta={x1-x0,y1-y0,z1-z0},next=new double[3],stepTime=new double[3];
        int[] cell={(int)Math.floor(x0),(int)Math.floor(y0),(int)Math.floor(z0)},step=new int[3];
        for(int axis=0;axis<3;axis++) {
            step[axis]=delta[axis]>0?1:delta[axis]<0?-1:0;
            stepTime[axis]=step[axis]==0?Double.POSITIVE_INFINITY:1/Math.abs(delta[axis]);
            next[axis]=step[axis]==0?Double.POSITIVE_INFINITY:((step[axis]>0?cell[axis]+1:cell[axis])-start[axis])/delta[axis];
        }
        double t=0;
        for(int work=0;work<8192;work++) {
            double end=Math.min(next[0],Math.min(next[1],next[2]));
            int type=cells.cell(cell[0],cell[1],cell[2]);
            if(type<0) return DhTerrainRayApi.Result.UNKNOWN;
            double contact=surface(type,cell[0],cell[1],cell[2],x0,y0,z0,delta[0],delta[1],delta[2],t,Math.min(1,end));
            if(Double.isFinite(contact)) { hit[0]=x0+delta[0]*contact;hit[1]=y0+delta[1]*contact;hit[2]=z0+delta[2]*contact;return DhTerrainRayApi.Result.BLOCKED; }
            if(end>1 || !Double.isFinite(end)) return DhTerrainRayApi.Result.CLEAR;
            t=end;
            int tied=0;for(int axis=0;axis<3;axis++) if(next[axis]<=end+1e-12) tied|=1<<axis;
            // Supercover corners/edges too: never skip a one-block wall on an exact diagonal.
            for(int subset=(tied-1)&tied;subset!=0;subset=(subset-1)&tied) {
                if(++work>=8192) return DhTerrainRayApi.Result.UNKNOWN;
                int touched=cells.cell(cell[0]+((subset&1)!=0?step[0]:0),cell[1]+((subset&2)!=0?step[1]:0),cell[2]+((subset&4)!=0?step[2]:0));
                if(touched<0) return DhTerrainRayApi.Result.UNKNOWN;
                double corner=surface(touched,cell[0]+((subset&1)!=0?step[0]:0),cell[1]+((subset&2)!=0?step[1]:0),cell[2]+((subset&4)!=0?step[2]:0),x0,y0,z0,delta[0],delta[1],delta[2],t,t);
                if(Double.isFinite(corner)) { hit[0]=x0+delta[0]*corner;hit[1]=y0+delta[1]*corner;hit[2]=z0+delta[2]*corner;return DhTerrainRayApi.Result.BLOCKED; }
            }
            for(int axis=0;axis<3;axis++) if((tied&(1<<axis))!=0) { cell[axis]+=step[axis];next[axis]+=stepTime[axis]; }
        }
        return DhTerrainRayApi.Result.UNKNOWN;
    }
    // Cells 0/1 are clear/full. Higher codes store an exact sixteenth-block Y interval.
    static double surface(int type,int x,int y,int z,double sx,double sy,double sz,double dx,double dy,double dz,double enter,double exit) {
        if(type<=0) return Double.NaN;
        if(type==1) return enter;
        if(type>=1024 && type<1032) {
            int[] omitted=STAIR_AIR[type-1024];double first=Double.NaN;
            for(int cube=0;cube<8;cube++) {
                if(cube==omitted[0] || cube==omitted[1]) continue;
                double lowX=x+(cube&1)*.5,lowY=y+((cube>>>1)&1)*.5,lowZ=z+((cube>>>2)&1)*.5;
                double contact=box(sx,sy,sz,dx,dy,dz,lowX,lowY,lowZ,lowX+.5,lowY+.5,lowZ+.5,enter,exit);
                if(Double.isFinite(contact) && (!Double.isFinite(first) || contact<first)) first=contact;
            }
            return first;
        }
        int packed=type-2;double low=y+(packed&31)/16.0,high=y+((packed>>>5)&31)/16.0;
        double a=enter,b=exit;
        if(dy==0) { if(sy<low || sy>high) return Double.NaN; }
        else { double t0=(low-sy)/dy,t1=(high-sy)/dy;a=Math.max(a,Math.min(t0,t1));b=Math.min(b,Math.max(t0,t1)); }
        return a<=b+1e-12?a:Double.NaN;
    }
    // Forge 1.7.10 BlockStairs.collisionRayTrace excludes these two half-cubes per metadata.
    private static final int[][] STAIR_AIR={{2,6},{3,7},{2,3},{6,7},{0,4},{1,5},{0,1},{4,5}};
    private static double box(double sx,double sy,double sz,double dx,double dy,double dz,double x0,double y0,double z0,double x1,double y1,double z1,double a,double b) {
        if(dx==0) { if(sx<x0 || sx>x1) return Double.NaN; }
        else { double p=(x0-sx)/dx,q=(x1-sx)/dx;a=Math.max(a,Math.min(p,q));b=Math.min(b,Math.max(p,q)); }
        if(dy==0) { if(sy<y0 || sy>y1) return Double.NaN; }
        else { double p=(y0-sy)/dy,q=(y1-sy)/dy;a=Math.max(a,Math.min(p,q));b=Math.min(b,Math.max(p,q)); }
        if(dz==0) { if(sz<z0 || sz>z1) return Double.NaN; }
        else { double p=(z0-sz)/dz,q=(z1-sz)/dz;a=Math.max(a,Math.min(p,q));b=Math.min(b,Math.max(p,q)); }
        return a<=b+1e-12?a:Double.NaN;
    }
}
