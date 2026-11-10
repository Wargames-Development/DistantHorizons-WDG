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
        DhApiMat4f dh=new DhApiMat4f(nativeProjection);dh.m00=.6F;dh.m11=.7F;dh.m22=-1.1F;dh.m23=-32;
        DhApiMat4f scene=new DhApiMat4f();require(ModelProjection.scene(nativeProjection,dh,scene),"shared native/DH camera depth");checks++;
        require(scene.m00==nativeProjection.m00 && scene.m11==nativeProjection.m11 && scene.m22==dh.m22,"scene inverse retains native pixel projection");checks++;
        require(ModelProjection.inverse(scene,inverse),"mixed scene inverse");checks++;
        DhApiMat4f identity=new DhApiMat4f(scene);identity.multiply(inverse);
        require(Math.abs(identity.m00-1)<.00001 && Math.abs(identity.m11-1)<.00001 && Math.abs(identity.m22-1)<.00001,"scene/inverse round trip");checks++;
        dh.m33=1;require(!ModelProjection.scene(nativeProjection,dh,scene),"incompatible camera depth bases withheld");checks++;
        com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiRenderParam original=
            new com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiRenderParam();
        original.mcProjectionMatrix.set(nativeProjection);original.dhProjectionMatrix.set(nativeProjection);
        original.mcModelViewMatrix.m00=original.mcModelViewMatrix.m11=original.mcModelViewMatrix.m22=original.mcModelViewMatrix.m33=1;
        original.dhModelViewMatrix.set(original.mcModelViewMatrix);original.dhModelViewMatrix.m03=7;
        com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiRenderParam copy=
            new com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiRenderParam();
        copy.update(original);
        require(copy.dhModelViewMatrix.m03==7 && copy.mcModelViewMatrix.m03==0,"event copy preserves distinct terrain and native origins");checks++;
        require(copy.dhMvmProjMatrix.m03==nativeProjection.m00*7,"copied terrain MVP uses terrain origin");checks++;
        int[] rect=new int[4];
        require(ModelBounds.project(nativeProjection,0,0,-512,8,1920,1080,rect),"distant model bounds");checks++;
        require(rect[0]>900 && rect[2]<1020 && rect[1]>500 && rect[3]<580,"capture work follows model screen area");checks++;
        require(!ModelBounds.project(nativeProjection,0,0,32,8,1920,1080,rect),"behind camera bounds rejected");checks++;
        require(!ModelBounds.project(nativeProjection,10000,0,-512,8,1920,1080,rect),"offscreen bounds rejected");checks++;
        require(ModelBounds.project(nativeProjection,0,0,-1,8,1920,1080,rect) && rect[0]==0 && rect[2]==1920,"near crossing remains conservative");checks++;
        java.util.Random random=new java.util.Random(783421);
        for(int i=0;i<1000;i++) {
            double z=-32-random.nextDouble()*992,x=(random.nextDouble()-.5)*-z*.5,y=(random.nextDouble()-.5)*-z*.5;
            require(ModelBounds.project(nativeProjection,x,y,z,8,1920,1080,rect),"visible random bounds");checks++;
            for(int j=0;j<8;j++) {
                double a=x+((j&1)==0?-8:8),b=y+((j&2)==0?-8:8),c=z+((j&4)==0?-8:8);
                double px=(nativeProjection.m00*a+nativeProjection.m02*c)/-c*.5+.5;
                double py=(nativeProjection.m11*b+nativeProjection.m12*c)/-c*.5+.5;
                px=Math.max(0,Math.min(1,px));py=Math.max(0,Math.min(1,py));
                require(px*1920>=rect[0] && px*1920<=rect[2] && py*1080>=rect[1] && py*1080<=rect[3],"all envelope corners enclosed");checks++;
            }
        }
        return checks;
    }
    private static void require(boolean value,String reason) { if(!value) throw new AssertionError(reason); }
}
