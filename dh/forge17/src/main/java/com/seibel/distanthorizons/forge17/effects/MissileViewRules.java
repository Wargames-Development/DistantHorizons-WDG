package com.seibel.distanthorizons.forge17.effects;

/** Native ownership until the complete model reaches its far clipping plane. */
final class MissileViewRules {
    static boolean insideClientChunks(double dx,double dz,int clientChunks,double radius) {
        double extent=Math.max(1,clientChunks)*16D;
        return Double.isFinite(dx) && Double.isFinite(dz) && Double.isFinite(radius)
            && Math.max(Math.abs(dx),Math.abs(dz))+radius<extent;
    }
    static double fadeStart(double dhNearPlane) { return (dhNearPlane+16D)*1.5D; }
    static boolean beforeNativeFade(double distance,double radius,double fadeStart,boolean enabled) {
        return !enabled || (Double.isFinite(distance) && Double.isFinite(radius) && Double.isFinite(fadeStart)
            && distance+radius<fadeStart);
    }
    static boolean drawProxy(boolean available,boolean nativeExpected,long nativeFrame,long frame) {
        return available && (!nativeExpected || nativeFrame!=frame);
    }
    static boolean nativeOwns(double clipW,double clipZ,double planeX,double planeY,double planeZ,double radius) {
        double margin=radius*Math.sqrt(planeX*planeX+planeY*planeY+planeZ*planeZ);
        return Double.isFinite(clipW) && Double.isFinite(clipZ) && Double.isFinite(margin)
            && clipW>0 && clipW-clipZ>margin;
    }
}
