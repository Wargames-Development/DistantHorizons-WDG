package com.seibel.distanthorizons.forge17.actors;

public final class TerrainCorridorChecks {
    public static int run() {
        int checks=0;TerrainCorridor corridor=new TerrainCorridor();
        corridor.reset(8192);
        require(corridor.known(0,65,0,512,65,0,4,(x,y0,y1,z)->y0==61 && y1==69),"known native/DH corridor");checks++;
        require(corridor.remaining()<8192,"work charged to view budget");checks++;
        corridor.reset(8192);
        require(!corridor.known(0,65,0,512,65,0,4,(x,y0,y1,z)->x!=10),"missing intermediate terrain cannot be skipped");checks++;
        corridor.reset(8192);
        require(!corridor.known(0,65,0,512,65,0,4,(x,y0,y1,z)->!(x==32 && z==-1)),"target envelope side coverage required");checks++;
        corridor.reset(8192);
        require(corridor.known(-.5,65,-.5,-512,65,-512,4,(x,y0,y1,z)->x<0 && z<0),"negative coordinates floor to correct chunks");checks++;
        corridor.reset(1);final int[] queried={0};
        require(!corridor.known(0,65,0,512,65,0,4,(x,y0,y1,z)->{ queried[0]++;return true; }),"over-budget bounds rejected early");checks++;
        require(queried[0]==0,"no work for rejected budget");checks++;
        corridor.reset(8192);
        require(!corridor.known(Double.NaN,0,0,1,1,1,4,(x,y0,y1,z)->true),"invalid camera rejected");checks++;
        require(!corridor.known(0,0,0,Double.POSITIVE_INFINITY,1,1,4,(x,y0,y1,z)->true),"invalid actor rejected");checks++;
        require(!corridor.known(0,0,0,1,1,1,129,(x,y0,y1,z)->true),"unbounded model radius rejected");checks++;
        return checks;
    }
    private static void require(boolean value,String name) { if(!value) throw new AssertionError(name); }
}
