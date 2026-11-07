package com.seibel.distanthorizons.forge17.effects;

import java.util.function.Consumer;

/** Native puff placement along the displayed engine's last tick, never a second flight path. */
final class NativeTrailEmitter {
    static void emit(TrailSample template,double[] previous,double[] current,double ox,double oy,double oz,Consumer<TrailSample> spawn) {
        double dx=current[0]-previous[0],dy=current[1]-previous[1],dz=current[2]-previous[2];
        double length=Math.sqrt(dx*dx+dy*dy+dz*dz);
        double direction=Math.sqrt(current[3]*current[3]+current[4]*current[4]+current[5]*current[5]);
        int count=Math.max(1,Math.min(10,(int)Math.ceil(length)));
        for(int i=0;i<count;i++) {
            double t=length>1e-6?i/length:0;
            TrailSample puff=template.copy();puff.count=1;
            puff.x0=puff.x=previous[0]+dx*t+ox;
            puff.y0=puff.y=previous[1]+dy*t+oy;
            puff.z0=puff.z=previous[2]+dz*t+oz;
            // The mesh and the exhaust share this exact smoothed nose direction.
            puff.mx=(float)(direction>1e-6?-current[3]/direction:0);
            puff.my=(float)(direction>1e-6?-current[4]/direction:-1);
            puff.mz=(float)(direction>1e-6?-current[5]/direction:0);
            spawn.accept(puff);
        }
    }
}
