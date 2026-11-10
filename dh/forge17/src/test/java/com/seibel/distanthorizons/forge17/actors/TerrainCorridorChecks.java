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
        require(corridor.known(-16.5,65,-16.5,-512,65,-512,4,(x,y0,y1,z)->x<0 && z<0),"negative coordinates floor to correct chunks");checks++;
        corridor.reset(1);final int[] queried={0};
        require(!corridor.known(0,65,0,512,65,0,4,(x,y0,y1,z)->{ queried[0]++;return true; }),"over-budget bounds rejected early");checks++;
        require(queried[0]==0,"no work for rejected budget");checks++;
        corridor.reset(8192);
        require(!corridor.known(Double.NaN,0,0,1,1,1,4,(x,y0,y1,z)->true),"invalid camera rejected");checks++;
        require(!corridor.known(0,0,0,Double.POSITIVE_INFINITY,1,1,4,(x,y0,y1,z)->true),"invalid actor rejected");checks++;
        require(!corridor.known(0,0,0,1,1,1,129,(x,y0,y1,z)->true),"unbounded model radius rejected");checks++;
        corridor.reset(128);
        require(corridor.known(0,65,0,512,65,512,4,(x,y0,y1,z)->Math.abs(x-z)<=2),"diagonal corridor excludes unrelated rectangle corners and fits a linear work budget");checks++;
        corridor.reset(8192);
        require(!corridor.known(0,65,0,512,65,512,4,(x,y0,y1,z)->x!=10 || z!=10),"diagonal intermediate gap still withholds the actor");checks++;
        corridor.reset(8192);queried[0]=0;
        require(!corridor.known(-30000000,0,-30000000,30000000,0,30000000,4,(x,y0,y1,z)->{ queried[0]++;return true; }) && queried[0]==0,
            "enormous coordinate span is rejected without unbounded enumeration");checks++;
        java.util.Random random=new java.util.Random(0xD10DC0DEL);
        for(int trial=0;trial<250;trial++) {
            double cx=random.nextDouble()*2048-1024,cz=random.nextDouble()*2048-1024;
            double x=cx+random.nextDouble()*1024-512,z=cz+random.nextDouble()*1024-512;
            if(trial%5==0) x=cx;if(trial%7==0) z=cz;
            double radius=.01+random.nextDouble()*32;
            java.util.Set<Long> columns=new java.util.HashSet<>();corridor.reset(8192);
            require(corridor.known(cx,65,cz,x,65,z,radius,(sx,y0,y1,sz)->{ columns.add(key(sx,sz));return true; }),"bounded randomized corridor accepted");checks++;
            // Independent geometric oracle: sample rays to all sides/corners and
            // interior points of the bounding square, rather than recomputing rows.
            for(int sideX=-1;sideX<=1;sideX++) for(int sideZ=-1;sideZ<=1;sideZ++) for(int step=0;step<=64;step++) {
                double t=step/64.0,px=cx+(x+sideX*radius-cx)*t,pz=cz+(z+sideZ*radius-cz)*t;
                int sx=(int)Math.floor(px/16),sz=(int)Math.floor(pz/16);
                require(columns.contains(key(sx,sz)),"camera-to-envelope ray escaped required terrain coverage");checks++;
            }
            require(corridor.remaining()==8192-columns.size(),"view budget accounts for each required column exactly once");checks++;
        }
        return checks;
    }
    private static long key(int x,int z) { return ((long)x<<32)|(z&0xffffffffL); }
    private static void require(boolean value,String name) { if(!value) throw new AssertionError(name); }
}
