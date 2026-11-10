package com.seibel.distanthorizons.forge17.actors;

import com.seibel.distanthorizons.api.objects.math.DhApiMat4f;

/** Projection math for an unscaled model captured in its own narrow depth interval. */
public final class ModelProjection {
    private ModelProjection() { }
    public static boolean prepare(DhApiMat4f source,double eyeDepth,double radius,DhApiMat4f target) {
        if(!Double.isFinite(eyeDepth) || !Double.isFinite(radius) || radius<=0 || radius>256
                || eyeDepth+radius<=0.05 || Math.abs(source.m32+1)>0.00001 || Math.abs(source.m33)>0.00001
                || source.m30!=0 || source.m31!=0 || source.m20!=0 || source.m21!=0) return false;
        float[] values=new float[16];source.putValuesInArray(values);
        for(float value:values) if(!Float.isFinite(value)) return false;
        double near=Math.max(0.05,eyeDepth-radius),far=Math.max(near+1,eyeDepth+radius);
        target.set(source);
        target.m22=(float)(-(far+near)/(far-near));target.m23=(float)(-2*far*near/(far-near));
        return true;
    }
    /** Use JOML's checked inverse; the source matrix is never mutated. */
    public static boolean inverse(DhApiMat4f source,DhApiMat4f target) {
        float[] values=new float[16];source.putValuesInArray(values);
        for(float value:values) if(!Float.isFinite(value)) return false;
        org.joml.Matrix4f inverse=new org.joml.Matrix4f().setTransposed(values);
        float determinant=inverse.determinant();
        if(!Float.isFinite(determinant) || Math.abs(determinant)<1e-12F) return false;
        inverse.invert().transpose().get(values);
        for(float value:values) if(!Float.isFinite(value)) return false;
        target.set(new DhApiMat4f(values));return true;
    }
}
