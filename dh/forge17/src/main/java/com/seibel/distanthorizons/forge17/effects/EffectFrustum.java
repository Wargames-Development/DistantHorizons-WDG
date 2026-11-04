package com.seibel.distanthorizons.forge17.effects;

import com.seibel.distanthorizons.api.objects.math.DhApiMat4f;

/** Conservative camera-side visibility test that deliberately omits the native far plane. */
public final class EffectFrustum
{
    private EffectFrustum() { }
    public static boolean beyondNativeView(DhApiMat4f m,double x,double y,double z,double radius)
    {
        double cz=m.m20*x+m.m21*y+m.m22*z+m.m23;
        double cw=m.m30*x+m.m31*y+m.m32*z+m.m33;
        return withinExtendedView(m,x,y,z,radius)
            && plane(m.m20-m.m30,m.m21-m.m31,m.m22-m.m32,cz-cw,radius);
    }
    /** Native particle viewport rejection is not proof of depth occlusion, even inside its far plane. */
    public static boolean withinExtendedView(DhApiMat4f m,double x,double y,double z,double radius)
    {
        double cx=m.m00*x+m.m01*y+m.m02*z+m.m03;
        double cy=m.m10*x+m.m11*y+m.m12*z+m.m13;
        double cz=m.m20*x+m.m21*y+m.m22*z+m.m23;
        double cw=m.m30*x+m.m31*y+m.m32*z+m.m33;
        // Plane equations retain the camera's pitch, yaw, third-person translation and FOV.
        return plane(m.m30,m.m31,m.m32,cw,radius)
            && plane(m.m30+m.m00,m.m31+m.m01,m.m32+m.m02,cw+cx,radius)
            && plane(m.m30-m.m00,m.m31-m.m01,m.m32-m.m02,cw-cx,radius)
            && plane(m.m30+m.m10,m.m31+m.m11,m.m32+m.m12,cw+cy,radius)
            && plane(m.m30-m.m10,m.m31-m.m11,m.m32-m.m12,cw-cy,radius);
    }
    private static boolean plane(double a,double b,double c,double value,double radius)
    {
        return value+radius*Math.sqrt(a*a+b*b+c*c)>0;
    }
}
