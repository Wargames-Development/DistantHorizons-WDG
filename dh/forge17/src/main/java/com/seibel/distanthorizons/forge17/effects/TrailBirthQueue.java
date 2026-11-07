package com.seibel.distanthorizons.forge17.effects;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.function.Consumer;
import java.util.function.ToDoubleFunction;

/** Bounded scheduled births, spread over the two ticks represented by each relay segment. */
final class TrailBirthQueue {
    private static final class Birth {
        final TrailSample sample;final double tick;final int arrival;
        Birth(TrailSample sample,double tick,int arrival) { this.sample=sample;this.tick=tick;this.arrival=arrival; }
    }
    private final ArrayDeque<Birth> pending=new ArrayDeque<Birth>();
    void clear() { pending.clear(); }
    void add(TrailSample sample,int count,int clientTick) {
        if(sample.family==TrailSample.HBM && sample.style.equals("missileContrail")) {
            // Reconstruct the two observed native tick emissions. NTM uses one-block
            // spacing from the previous position, not uniform spacing across the flight.
            double dx=(sample.x-sample.x0)/2,dy=(sample.y-sample.y0)/2,dz=(sample.z-sample.z0)/2;
            double length=Math.sqrt(dx*dx+dy*dy+dz*dz);
            int nativeCount=Math.max(1,Math.min(10,(int)Math.ceil(length)));
            for(int step=0;step<2;step++) for(int i=0;i<nativeCount && pending.size()<2048;i++) {
                double along=length>1e-6?i/length:0;
                TrailSample puff=sample.copy();puff.count=1;
                puff.x0=puff.x=sample.x0+dx*(step+along);
                puff.y0=puff.y=sample.y0+dy*(step+along);
                puff.z0=puff.z=sample.z0+dz*(step+along);
                pending.addLast(new Birth(puff,sample.tick-1+step,clientTick));
            }
            return;
        }
        for(int i=0;i<count && pending.size()<2048;i++) {
            double t=(i+0.5)/count;
            TrailSample puff=sample.copy();puff.count=1;
            puff.x0=puff.x=sample.x0+(sample.x-sample.x0)*t;
            puff.y0=puff.y=sample.y0+(sample.y-sample.y0)*t;
            puff.z0=puff.z=sample.z0+(sample.z-sample.z0)*t;
            pending.addLast(new Birth(puff,sample.tick-2+2*t,clientTick));
        }
    }
    void drain(int clientTick,ToDoubleFunction<TrailSample> clock,Consumer<TrailSample> spawn) {
        for(Iterator<Birth> it=pending.iterator();it.hasNext();) {
            Birth birth=it.next();
            if(clientTick-birth.arrival>12) { it.remove();continue; }
            double playhead=clock.applyAsDouble(birth.sample);
            // Sources without a body spread births across two client ticks.
            if(Double.isNaN(playhead)) playhead=birth.sample.tick-2+(clientTick-birth.arrival);
            if(birth.tick>playhead) continue;
            it.remove();spawn.accept(birth.sample);
        }
    }
}
