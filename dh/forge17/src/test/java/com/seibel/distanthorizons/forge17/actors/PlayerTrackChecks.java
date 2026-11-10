package com.seibel.distanthorizons.forge17.actors;

import java.util.UUID;

public final class PlayerTrackChecks {
    private static PlayerFrame.Pose pose(int id,double x,float yaw,int flags,long revision) {
        return new PlayerFrame.Pose(new UUID(1,2),id,x,65,0,yaw,10,yaw,yaw,0,flags,(int)x+100,0,revision);
    }
    public static int run() {
        int checks=0;PlayerTrack track=new PlayerTrack();PlayerTrack.Sample sample=new PlayerTrack.Sample();
        require(!track.sample(0,sample),"empty track hidden");checks++;
        track.observe(pose(1,0,359,1,1),0,0,2);
        track.observe(pose(1,2,1,2,2),2,100000000L,2);
        require(track.sample(150000000L,sample) && Math.abs(sample.x-1)<.00001,"single delayed position timeline");checks++;
        require(Math.abs(sample.yaw-360)<.001 && Math.abs(sample.bodyYaw-360)<.001,"short angular path shared by body/head");checks++;
        require(sample.flags==1 && sample.revision==1,"equipment/actions select the same sample time");checks++;
        require(sample.phase>0 && sample.amount>0,"gait derives from authoritative motion");checks++;
        require(track.sample(250000000L,sample) && sample.x==2 && sample.revision==2 && sample.flags==2,"latest pose reached without future prediction");checks++;
        require(track.sample(800000000L,sample) && sample.x==2,"missing packets cannot extrapolate indefinitely");checks++;
        require(!track.sample(1100000001L,sample),"stale track hidden");checks++;
        track.observe(pose(1,200,90,0,3),4,200000000L,2);
        require(track.sample(200000000L,sample) && sample.x==200 && sample.phase==0,"teleport resets gait/interpolation");checks++;
        track.observe(pose(2,202,90,0,4),6,300000000L,2);
        require(track.sample(300000000L,sample) && sample.x==202,"entity ID replacement resets presentation");checks++;
        track.observe(pose(2,250,90,0,5),5,350000000L,2);
        require(track.sample(350000000L,sample) && sample.x==202,"late observation ignored");checks++;
        track.observe(pose(2,204,90,0,5),40,400000000L,2);
        require(track.sample(400000000L,sample) && sample.x==204 && sample.phase==0,"long observation gaps reset presentation");checks++;
        PlayerTrack use=new PlayerTrack();
        use.observe(new PlayerFrame.Pose(new UUID(1,2),1,0,65,0,0,0,0,0,0,64,0,0,1,100,270),0,0,2);
        use.observe(new PlayerFrame.Pose(new UUID(1,2),1,0,65,0,0,0,0,0,0,64,2,0,1,98,270),2,100000000L,2);
        require(use.sample(150000000L,sample) && sample.useTicks==99 && sample.bedYaw==270,"item use and sleeping orientation share pose time");checks++;
        for(int interval=1;interval<=20;interval++) {
            PlayerTrack every=new PlayerTrack();
            every.observe(pose(1,0,0,0,1),0,0,interval);
            every.observe(pose(1,10,20,2,2),interval,1000000000L,interval);
            require(every.sample(1000000000L+Math.max(2,interval)*50000000L,sample)
                && sample.x==10 && sample.revision==2 && sample.flags==2,"every server interval reaches its final authoritative sample");checks++;
        }
        return checks;
    }
    private static void require(boolean value,String reason) { if(!value) throw new AssertionError(reason); }
}
