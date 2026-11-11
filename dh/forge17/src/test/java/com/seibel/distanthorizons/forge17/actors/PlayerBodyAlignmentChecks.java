package com.seibel.distanthorizons.forge17.actors;
public final class PlayerBodyAlignmentChecks {
    public static int run() {
        PlayerBodyAlignment body=new PlayerBodyAlignment();int checks=0;
        require(body.apply(0,0,45F,1_000_000_000L)==45,"tracked native torso copied");checks++;
        for(int i=1;i<30;i++){require(body.apply(0,0,null,1_000_000_000L+i*50_000_000L)==45,"idle tracking exit cannot rotate torso");checks++;}
        float changed=body.apply(0,20,null,2_500_000_000L);
        require(changed>0 && changed<45,"look change releases alignment smoothly");checks++;
        for(int i=1;i<100;i++)changed=body.apply(0,20,null,2_500_000_000L+i*50_000_000L);
        require(Math.abs(changed)<.01,"active pose converges to snapshot heading");checks++;
        require(Math.abs(PlayerBodyAlignment.interpolate(179,-179,.5F)-180)<.001,"angle interpolation wraps the short way");checks++;
        body.reset();require(body.apply(-45,0,null,9_000_000_000L)==-45,"new actor cannot inherit an old body offset");checks++;
        return checks;
    }
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
