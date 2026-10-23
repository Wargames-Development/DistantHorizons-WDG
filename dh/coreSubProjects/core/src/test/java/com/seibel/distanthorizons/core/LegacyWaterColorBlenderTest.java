package com.seibel.distanthorizons.core;

import org.junit.Test;
import static org.junit.Assert.*;
import com.seibel.distanthorizons.core.dataObjects.render.ColumnRenderSource;
import com.seibel.distanthorizons.core.dataObjects.transformers.LegacyWaterColorBlender;
import com.seibel.distanthorizons.core.enums.EDhDirection;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.util.RenderDataPointUtil;

public class LegacyWaterColorBlenderTest
{
    private static final int W = ColumnRenderSource.WIDTH;
    private static final long RGB_MASK = 0xFFFFFFL << 32;
    private static long water(int color, int top) { return RenderDataPointUtil.createDataPoint(top, 10, color, 13, 2, 12); }
    private static ColumnRenderSource source(long value)
    {
        ColumnRenderSource s = ColumnRenderSource.createEmpty(DhSectionPos.encode((byte) 6, 0, 0), 2, 0);
        for (int x=0; x<W; x++) for (int z=0; z<W; z++) s.renderDataContainer.set((x*W+z)*2, value);
        s.markNotEmpty();
        return s;
    }
    @Test public void uniformWaterAndDisabledBlendAreUnchanged()
    {
        long p=water(0xA03080C0, 90);
        try (ColumnRenderSource s=source(p)) {
            LegacyWaterColorBlender.blend(s,new ColumnRenderSource[4],3);
            for(int x=0;x<W;x++)for(int z=0;z<W;z++)assertEquals(p,s.getDataPoint(x,z,0));
            s.renderDataContainer.set((32*W+32)*2,water(0xA0FFFFFF,90));
            long[] before=s.renderDataContainer.toLongArray();
            LegacyWaterColorBlender.blend(s,new ColumnRenderSource[4],0);
            assertArrayEquals(before,s.renderDataContainer.toLongArray());
        }
    }
    @Test public void transitionIsGradualAndOnlyRgbChanges()
    {
        try(ColumnRenderSource s=source(water(0xA0000000,90))) {
            for(int x=32;x<W;x++)for(int z=0;z<W;z++)s.renderDataContainer.set((x*W+z)*2,water(0xA0FFFFFF,90));
            long[] before=s.renderDataContainer.toLongArray();
            LegacyWaterColorBlender.blend(s,new ColumnRenderSource[4],3);
            int last=-1;
            for(int x=28;x<36;x++) {
                int blue=RenderDataPointUtil.getColor(s.getDataPoint(x,32,0))&255;
                assertTrue(blue>=last);last=blue;
            }
            assertTrue((RenderDataPointUtil.getColor(s.getDataPoint(31,32,0))&255)>0);
            assertTrue((RenderDataPointUtil.getColor(s.getDataPoint(32,32,0))&255)<255);
            for(int i=0;i<before.length;i++)assertEquals(before[i]&~RGB_MASK,s.renderDataContainer.getLong(i)&~RGB_MASK);
        }
    }
    @Test public void neighboringSectionEdgeAlsoBlendsWithoutMutatingNeighbor()
    {
        try(ColumnRenderSource a=source(water(0xA0000000,90));ColumnRenderSource b=source(water(0xA0FFFFFF,90))) {
            ColumnRenderSource[] neighbors=new ColumnRenderSource[4];neighbors[EDhDirection.EAST.compassIndex]=b;
            long[] before=b.renderDataContainer.toLongArray();
            LegacyWaterColorBlender.blend(a,neighbors,3);
            assertTrue((RenderDataPointUtil.getColor(a.getDataPoint(W-1,32,0))&255)>0);
            assertEquals(0,RenderDataPointUtil.getColor(a.getDataPoint(W-4,32,0))&255);
            assertArrayEquals(before,b.renderDataContainer.toLongArray());
        }
    }
    @Test public void dryShorelineAndDifferentWaterHeightsBlockBleeding()
    {
        for(long other:new long[]{RenderDataPointUtil.createDataPoint(90,10,0xFFFFFFFF,15,0,1),water(0xA0FFFFFF,91)})
            try(ColumnRenderSource s=source(water(0xA0000000,90))) {
                for(int x=32;x<W;x++)for(int z=0;z<W;z++)s.renderDataContainer.set((x*W+z)*2,other);
                long[] before=s.renderDataContainer.toLongArray();
                LegacyWaterColorBlender.blend(s,new ColumnRenderSource[4],3);
                assertArrayEquals(before,s.renderDataContainer.toLongArray());
            }
    }
    @Test public void missingAndDifferentDetailNeighborsAreSafe()
    {
        try(ColumnRenderSource s=source(water(0xA0000000,90));
            ColumnRenderSource other=ColumnRenderSource.createEmpty(DhSectionPos.encode((byte)7,1,0),2,0)) {
            for(int x=0;x<W;x++)for(int z=0;z<W;z++)other.renderDataContainer.set((x*W+z)*2,water(0xA0FFFFFF,90));
            ColumnRenderSource[] neighbors=new ColumnRenderSource[4];neighbors[EDhDirection.EAST.compassIndex]=other;
            long[] before=s.renderDataContainer.toLongArray();
            LegacyWaterColorBlender.blend(s,neighbors,99);
            assertArrayEquals(before,s.renderDataContainer.toLongArray());
        }
    }
    @Test public void sectionBoundaryUsesOnlyAvailableEdgeRecords()
    {
        try(ColumnRenderSource a=source(water(0xA0000000,90));ColumnRenderSource b=source(0)) {
            for(int z=0;z<W;z++)b.renderDataContainer.set(z*2,water(0xA0FFFFFF,90));
            ColumnRenderSource[] neighbors=new ColumnRenderSource[4];neighbors[EDhDirection.EAST.compassIndex]=b;
            LegacyWaterColorBlender.blend(a,neighbors,3);
            int last=0;
            for(int x=W-4;x<W;x++) {
                int color=RenderDataPointUtil.getColor(a.getDataPoint(x,32,0))&255;
                assertTrue(color>=last);last=color;
            }
            assertTrue(last>0);
        }
    }
    @Test public void submergedPlantColoredWaterIsNotBlendedIntoSurface()
    {
        try(ColumnRenderSource s=source(water(0xA00000FF,90))) {
            for(int x=0;x<W;x++)for(int z=0;z<W;z++)s.renderDataContainer.set((x*W+z)*2+1,water(0xA000FF00,80));
            long[] before=s.renderDataContainer.toLongArray();
            LegacyWaterColorBlender.blend(s,new ColumnRenderSource[4],3);
            assertArrayEquals(before,s.renderDataContainer.toLongArray());
        }
    }
}
