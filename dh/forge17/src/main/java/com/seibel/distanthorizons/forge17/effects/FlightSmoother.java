package com.seibel.distanthorizons.forge17.effects;

/** Four-tick delayed interpolation of observed flight, never extrapolates past a sample. */
final class FlightSmoother {
    private final double[][] points=new double[16][7];
    private int size;
    private double clock;
    void offer(long tick,double x,double y,double z,double vx,double vy,double vz) {
        if(size>0 && tick<=points[size-1][0]) return;
        if(size>0) {
            double[] last=points[size-1];
            double dx=x-last[1],dy=y-last[2],dz=z-last[3];
            if(tick-last[0]>10 || dx*dx+dy*dy+dz*dz>256D*256D) size=0;
        }
        if(size==0) clock=tick-4D;
        if(size==points.length) { for(int i=1;i<size;i++) System.arraycopy(points[i],0,points[i-1],0,7);size--; }
        double[] point=points[size++];point[0]=tick;point[1]=x;point[2]=y;point[3]=z;point[4]=vx;point[5]=vy;point[6]=vz;
    }
    void advance() { if(size>0) clock=Math.min(clock+1,points[size-1][0]); }
    double presentationTick() { return size==0?Double.NEGATIVE_INFINITY:Math.min(clock,points[size-1][0]); }
    void sample(double partial,double[] out) {
        if(size==0) return;
        double time=Math.min(clock+partial,points[size-1][0]);
        double[] a=points[0],b=a;
        for(int i=1;i<size;i++) { b=points[i];if(b[0]>=time) break;a=b; }
        double dt=b[0]-a[0],t=dt>0?Math.max(0,Math.min(1,(time-a[0])/dt)):0;
        double t2=t*t,t3=t2*t;
        for(int axis=0;axis<3;axis++) {
            out[axis]=(2*t3-3*t2+1)*a[axis+1]+(t3-2*t2+t)*dt*a[axis+4]
                +(-2*t3+3*t2)*b[axis+1]+(t3-t2)*dt*b[axis+4];
            out[axis+3]=a[axis+4]+(b[axis+4]-a[axis+4])*t;
        }
    }
}
