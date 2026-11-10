package com.seibel.distanthorizons.forge17.effects;

import com.seibel.distanthorizons.core.dataObjects.render.TerrainCoverage;
import java.util.Arrays;

/** Pure coverage checks; no Minecraft/GL context required. */
final class TerrainCoverageChecks {
    static int run() {
        int checks=0;
        long[] rows=new long[64];
        Arrays.fill(rows,-1L);
        TerrainCoverage full=TerrainCoverage.copyOf(rows);
        Arrays.fill(rows,0);
        require(full.isKnown(0,0,63,63),"published coverage is immutable"); checks++;
        require(!TerrainCoverage.UNKNOWN.isKnown(0,0),"unknown isn't sky"); checks++;
        for(int x=0;x<64;x++) for(int z=0;z<64;z++) {
            rows[x]=1L<<z;
            TerrainCoverage one=TerrainCoverage.copyOf(rows);
            require(one.isKnown(x,z),"known column including bit 63");
            require(one.isKnown(x,z,x,z),"one-column rectangle");
            require(!one.isKnown(x,(z+1)%64),"adjacent column remains unknown");
            require(!one.isKnown(0,0,63,63),"partial section cannot authorise whole section");
            rows[x]=0; checks+=4;
        }
        Arrays.fill(rows,-1L);
        rows[32]&=~(1L<<32);
        TerrainCoverage gap=TerrainCoverage.copyOf(rows);
        for(int lo=0;lo<64;lo++) for(int hi=lo;hi<64;hi++) {
            require(full.isKnown(lo,lo,hi,hi),"complete rectangle");
            require(gap.isKnown(lo,lo,hi,hi)==!(lo<=32 && hi>=32),"hole in rectangle rejects coverage");
            checks+=2;
        }
        for(int[] bounds:new int[][]{{-1,0,0,0},{0,-1,0,0},{0,0,64,63},{0,0,63,64},{2,0,1,0},{0,2,0,1}}) {
            require(!full.isKnown(bounds[0],bounds[1],bounds[2],bounds[3]),"invalid bounds fail closed"); checks++;
        }
        try { TerrainCoverage.copyOf(new long[63]); throw new AssertionError("invalid coverage length"); }
        catch(IllegalArgumentException expected) { checks++; }
        return checks;
    }
    private static void require(boolean value,String reason) { if(!value) throw new AssertionError(reason); }
}
