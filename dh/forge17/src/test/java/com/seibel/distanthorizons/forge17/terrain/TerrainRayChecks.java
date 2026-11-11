package com.seibel.distanthorizons.forge17.terrain;
import java.util.Arrays;
public final class TerrainRayChecks {
    public static int run() {
        double[] hit=new double[3];int checks=0;
        for(int sign:new int[]{-1,1}) for(int length:new int[]{1,15,64,750,1500}) {
            Arrays.fill(hit,Double.NaN);int wall=sign*length;
            DhTerrainRayApi.Result result=TerrainRayWalk.trace(.5,320.5,.5,sign*(length+1)+.5,320.5,.5,(x,y,z)->x==wall?1:0,hit);
            require(result==DhTerrainRayApi.Result.BLOCKED,"one-block wall cannot be stepped over");checks++;
            require(Math.abs(hit[0]-(sign>0?wall:wall+1))<1e-8 && hit[1]==320.5,"first hit retains extended-height coordinate");checks++;
            result=TerrainRayWalk.trace(.5,320.5,.5,sign*(length+1)+.5,320.5,.5,(x,y,z)->x==0?-1:x==wall?1:0,hit);
            require(result==DhTerrainRayApi.Result.UNKNOWN,"missing prefix cannot authorize a hit behind it");checks++;
        }
        require(TerrainRayWalk.trace(.5,500,.5,100,.5,100,(x,y,z)->0,hit)==DhTerrainRayApi.Result.CLEAR,"known air corridor is clear");checks++;
        require(TerrainRayWalk.trace(.5,500,.5,100,.5,100,(x,y,z)->-1,hit)==DhTerrainRayApi.Result.UNKNOWN,"partial collision remains unknown");checks++;
        require(TerrainRayWalk.trace(.5,320,.5,10.5,320,10.5,(x,y,z)->x==1&&z==0?1:0,hit)==DhTerrainRayApi.Result.BLOCKED,"diagonal edge cannot step across a thin wall");checks++;
        require(TerrainRayWalk.trace(.5,320,.5,10.5,320,10.5,(x,y,z)->x==1&&z==0?-1:0,hit)==DhTerrainRayApi.Result.UNKNOWN && Double.isNaN(hit[0]),"unknown diagonal prefix rejects and clears a previous hit");checks++;
        return checks;
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
