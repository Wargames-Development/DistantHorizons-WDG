package com.seibel.distanthorizons.forge17.actors;

/** Four authoritative samples on one bounded presentation timeline. Never predicts actions or position. */
public final class PlayerTrack {
    private final PlayerFrame.Pose[] poses=new PlayerFrame.Pose[4];
    private final long[] ticks=new long[4];
    private final double[] phases=new double[4],amounts=new double[4];
    private int count,interval=2;
    private long received;
    public void observe(PlayerFrame.Pose pose,long sequence,long receipt,int interval) {
        if(count>0 && sequence<=ticks[count-1]) return;
        if(count>0) {
            PlayerFrame.Pose previous=poses[count-1];
            double dx=pose.x-previous.x,dy=pose.y-previous.y,dz=pose.z-previous.z;
            if(previous.entityId!=pose.entityId || !previous.uuid.equals(pose.uuid) || dx*dx+dy*dy+dz*dz>4096 || sequence-ticks[count-1]>20) count=0;
        }
        double phase=0,amount=0;
        if(count>0) {
            PlayerFrame.Pose previous=poses[count-1];long elapsed=sequence-ticks[count-1];
            double dx=pose.x-previous.x,dz=pose.z-previous.z;
            double target=Math.min(1,Math.sqrt(dx*dx+dz*dz)*4/elapsed);
            amount=amounts[count-1]+(target-amounts[count-1])*Math.min(1,elapsed*.4);
            phase=phases[count-1]+amount*elapsed;
        }
        if(count==4) {
            System.arraycopy(poses,1,poses,0,3);System.arraycopy(ticks,1,ticks,0,3);
            System.arraycopy(phases,1,phases,0,3);System.arraycopy(amounts,1,amounts,0,3);count=3;
        }
        poses[count]=pose;ticks[count]=sequence;phases[count]=phase;amounts[count]=amount;count++;
        this.interval=Math.max(1,Math.min(20,interval));received=receipt;
    }
    public boolean sample(long now,Sample out) {
        if(count==0 || now-received>1000000000L) return false;
        double tick=ticks[count-1]+Math.max(0,Math.min(interval,(now-received)/50000000.0))-Math.max(2,interval);
        int a=0,b=0;
        if(tick>=ticks[count-1]) a=b=count-1;
        else for(int i=1;i<count;i++) if(tick>=ticks[i-1] && tick<ticks[i]) { a=i-1;b=i;break; }
        double t=a==b?0:Math.max(0,Math.min(1,(tick-ticks[a])/(ticks[b]-ticks[a])));
        PlayerFrame.Pose first=poses[a],last=poses[b],state=t>=1?last:first;
        out.x=mix(first.x,last.x,t);out.y=mix(first.y,last.y,t);out.z=mix(first.z,last.z,t);
        out.yaw=angle(first.yaw,last.yaw,t);out.pitch=(float)mix(first.pitch,last.pitch,t);
        out.headYaw=angle(first.headYaw,last.headYaw,t);out.bodyYaw=angle(first.bodyYaw,last.bodyYaw,t);
        out.phase=(float)mix(phases[a],phases[b],t);out.amount=(float)mix(amounts[a],amounts[b],t);
        double swing=last.swing-first.swing;if(swing<-.5) swing+=1;
        out.swing=(float)((first.swing+swing*t)%1);out.age=(float)mix(first.age,last.age,t);
        out.bedYaw=state.bedYaw;out.useTicks=state.useTicks;
        if((first.flags & 64)!=0 && (last.flags & 64)!=0) out.useTicks=(int)mix(first.useTicks,last.useTicks,t);
        else if((first.flags & 64)!=0 && (last.flags & 64)==0) out.useTicks=Math.max(0,first.useTicks-(int)(tick-ticks[a]));
        out.first=first;out.last=last;out.fraction=(float)t;
        out.flags=state.flags;out.brightness=state.brightness;out.revision=state.appearanceRevision;
        out.entityId=state.entityId;return true;
    }
    private static double mix(double a,double b,double t) { return a+(b-a)*t; }
    private static float angle(float a,float b,double t) {
        double difference=b-a;difference-=Math.floor((difference+180)/360)*360;
        return (float)(a+difference*t);
    }
    public static final class Sample {
        public PlayerFrame.Pose first,last;
        public float fraction;
        public double x,y,z;
        public float yaw,pitch,headYaw,bodyYaw,phase,amount,swing,age,bedYaw;
        public int flags,brightness,entityId,useTicks;
        public long revision;
    }
}
