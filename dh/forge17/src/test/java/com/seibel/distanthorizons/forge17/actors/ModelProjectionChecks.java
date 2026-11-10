package com.seibel.distanthorizons.forge17.actors;

import com.seibel.distanthorizons.api.objects.math.DhApiMat4f;

public final class ModelProjectionChecks {
    public static int run() {
        int checks=0;
        DhApiMat4f nativeProjection=new DhApiMat4f();nativeProjection.m00=1.3F;nativeProjection.m11=1.7F;
        nativeProjection.m22=-1.001F;nativeProjection.m23=-.1F;nativeProjection.m32=-1;
        nativeProjection.m02=.03F;nativeProjection.m12=-.02F;
        DhApiMat4f capture=new DhApiMat4f(),inverse=new DhApiMat4f();
        for(double distance:new double[]{.1,1,16,64,128,256,512,750,1024,4096,16384}) {
            require(ModelProjection.prepare(nativeProjection,distance,8,capture),"capture projection");
            require(capture.m00==nativeProjection.m00 && capture.m02==nativeProjection.m02,"FOV and jitter preserved");
            require(ModelProjection.inverse(capture,inverse),"capture inverse");checks+=3;
            for(double offset:new double[]{-0.02,0,0.02,1,4}) {
                double z=-(distance+offset);
                double clipZ=capture.m22*z+capture.m23,w=-z;
                double depth=(clipZ/w+1)*.5;
                if(depth<0 || depth>1) continue;
                // Quantise the depth actually stored by a 24-bit capture texture.
                depth=Math.rint(depth*16777215.0)/16777215.0;
                double nz=depth*2-1;
                double rz=(inverse.m22*nz+inverse.m23)/(inverse.m32*nz+inverse.m33);
                require(Math.abs(rz-z)<Math.max(.0002,distance*.000002),"model self-depth remains precise at distance");checks++;
            }
        }
        require(nativeProjection.m22==-1.001F && nativeProjection.m23==-.1F,"source projection unchanged");checks++;
        require(!ModelProjection.prepare(nativeProjection,Double.NaN,8,capture),"invalid pose fails closed");checks++;
        require(!ModelProjection.prepare(nativeProjection,-32,8,capture),"behind-camera model rejected");checks++;
        require(!ModelProjection.prepare(nativeProjection,512,0,capture),"empty bounds rejected");checks++;
        require(!ModelProjection.inverse(new DhApiMat4f(),inverse),"singular inverse rejected");checks++;
        DhApiMat4f oblique=new DhApiMat4f(nativeProjection);oblique.m20=.5F;
        require(!ModelProjection.prepare(oblique,512,8,capture),"unsupported oblique projection withheld");checks++;
        return checks;
    }
    private static void require(boolean value,String reason) { if(!value) throw new AssertionError(reason); }
}
