package com.seibel.distanthorizons.core.generation;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
/** Immutable block-face limits, safe to read on worker threads. Null means unrestricted. */
public final class WorldBorderBounds {
    public final double minX,minZ,maxX,maxZ;
    public WorldBorderBounds(double minX,double minZ,double maxX,double maxZ) {
        if(!Double.isFinite(minX)||!Double.isFinite(minZ)||!Double.isFinite(maxX)||!Double.isFinite(maxZ)
            || minX>=maxX || minZ>=maxZ || Math.abs(minX)>30000000 || Math.abs(maxX)>30000000
            || Math.abs(minZ)>30000000 || Math.abs(maxZ)>30000000) throw new IllegalArgumentException("Invalid border");
        this.minX=minX;this.minZ=minZ;this.maxX=maxX;this.maxZ=maxZ;
    }
    /** -1 outside, 0 intersects, 1 fully inside. Maximum edges are exclusive. */
    public int classify(long x,long z,long width) {
        if(width<=0)throw new IllegalArgumentException("Invalid footprint");
        long right=x+width, bottom=z+width;
        if(right<=minX||x>=maxX||bottom<=minZ||z>=maxZ)return -1;
        return x>=minX&&z>=minZ&&right<=maxX&&bottom<=maxZ?1:0;
    }
    public int classify(long section) {return classify(DhSectionPos.getMinCornerBlockX(section),DhSectionPos.getMinCornerBlockZ(section),DhSectionPos.getBlockWidth(section));}
    public boolean containsChunk(int x,int z) {return classify((long)x*16,(long)z*16,16)==1;}
    @Override public boolean equals(Object o) {if(!(o instanceof WorldBorderBounds))return false;WorldBorderBounds b=(WorldBorderBounds)o;
        return minX==b.minX&&minZ==b.minZ&&maxX==b.maxX&&maxZ==b.maxZ;}
    @Override public int hashCode(){return java.util.Objects.hash(minX,minZ,maxX,maxZ);}
}
