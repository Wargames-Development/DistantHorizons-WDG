package com.seibel.distanthorizons.forge17.actors;

import com.seibel.distanthorizons.api.objects.math.DhApiMat4f;

public final class ModelProjectionChecks {
    public static int run() {
        int checks=0;
        require(ModelCapture.supportsSize(5120,2880),"5K full-resolution actor targets supported");checks++;
        require(!ModelCapture.supportsSize(0,2880) && !ModelCapture.supportsSize(7680,4320),"invalid and oversized targets remain bounded");checks++;
        require(!ModelCapture.supportsSize(Integer.MAX_VALUE,Integer.MAX_VALUE),"pixel budget calculation cannot overflow");checks++;
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
        // A model corridor contains sections behind/beside the camera which the
        // native renderer quite correctly omits. Zoom must reduce required coverage.
        for(double fov:new double[]{70,36,35,10,1}) {
            DhApiMat4f view=new DhApiMat4f(nativeProjection);
            view.m00=(float)(1/Math.tan(Math.toRadians(fov)*.5)/ (16.0/9));view.m11=(float)(1/Math.tan(Math.toRadians(fov)*.5));
            view.m02=view.m12=0;
            require(!ModelBounds.intersectsView(view,-16,-4,16,16,4,32),"behind-camera terrain cannot withhold a model");checks++;
            require(ModelBounds.intersectsView(view,-1,-1,-750,1,1,-749),"distant sight corridor required at every FOV, beyond native far plane");checks++;
            require(ModelBounds.intersectsView(view,-16,-16,-16,16,16,16),"camera-crossing terrain remains required");checks++;
            require(!ModelBounds.intersectsView(view,512,-4,-32,528,4,-16),"offscreen corridor expansion is irrelevant");checks++;
            TerrainCorridor corridor=new TerrainCorridor();corridor.reset(8192);
            final int[] irrelevant={0},required={0};
            require(corridor.known(0,65,0,0,65,-750,16,(sx,y0,y1,sz)->{
                boolean visible=ModelBounds.intersectsView(view,sx*16,y0-65,sz*16,sx*16+16,y1+1-65,sz*16+16);
                if(visible) required[0]++;else irrelevant[0]++;
                // Simulate complete native/DH terrain in view, absent offscreen.
                return !visible || sz<0 || sz==0;
            }),"complete visible corridor survives offscreen native culling under zoom");checks++;
            require(irrelevant[0]>0 && required[0]>0,"fixture exercises both culled and required columns");checks++;
            corridor.reset(8192);
            require(!corridor.known(0,65,0,0,65,-750,16,(sx,y0,y1,sz)->
                !ModelBounds.intersectsView(view,sx*16,y0-65,sz*16,sx*16+16,y1+1-65,sz*16+16) || sx!=0 || sz!=-20),
                "in-view terrain gap still withholds model under zoom");checks++;
            // Independent point oracle: no box containing an on-screen point may
            // be classified irrelevant, including translated/rotated camera views.
            for(int angle=0;angle<360;angle+=15) {
                double yaw=Math.toRadians(angle),cs=Math.cos(yaw),sn=Math.sin(yaw);
                DhApiMat4f modelView=new DhApiMat4f();modelView.m00=modelView.m22=(float)cs;
                modelView.m02=(float)sn;modelView.m20=(float)-sn;modelView.m11=modelView.m33=1;
                modelView.m03=3;modelView.m13=-2;modelView.m23=7;
                DhApiMat4f rotated=new DhApiMat4f(view);rotated.multiply(modelView);
                for(int i=0;i<25;i++) {
                    double depth=1+i*32,eyeX=((i%5)/2.0-1)*depth/view.m00*.99;
                    double eyeY=((i/5)/2.0-1)*depth/view.m11*.99;
                    double px=cs*(eyeX-3)-sn*(-depth-7),py=eyeY+2,pz=sn*(eyeX-3)+cs*(-depth-7);
                    require(ModelBounds.intersectsView(rotated,px-1,py-1,pz-1,px+1,py+1,pz+1),"visible ray never escapes terrain coverage after camera rotation");checks++;
                }
            }
        }
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
