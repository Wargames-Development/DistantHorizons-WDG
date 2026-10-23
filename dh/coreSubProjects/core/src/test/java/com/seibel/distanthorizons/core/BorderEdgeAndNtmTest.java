package com.seibel.distanthorizons.core;

import org.junit.Test;
import static org.junit.Assert.*;
import com.seibel.distanthorizons.core.generation.*;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.enums.EDhDirection;
import com.seibel.distanthorizons.core.dataObjects.render.bufferBuilding.BufferQuad;
import com.seibel.distanthorizons.core.dataObjects.transformers.NtmLodPolicy;

public class BorderEdgeAndNtmTest {
    @Test public void nonAlignedEarthEdgesKeepBoundaryChunksAndRejectOutsideOnes() {
        WorldBorderBounds b=new WorldBorderBounds(-20429,-11770,20429,11674);
        assertFalse(b.containsChunk(1276,0));assertTrue(b.intersectsChunk(1276,0));
        assertFalse(b.intersectsChunk(1277,0));assertTrue(b.intersectsChunk(-1277,0));
        assertFalse(b.intersectsChunk(-1278,0));
        assertTrue(b.intersectsChunk(0,729));assertFalse(b.intersectsChunk(0,730));
        assertTrue(b.intersectsChunk(0,-736));assertFalse(b.intersectsChunk(0,-737));
    }
    @Test public void partialLeafIsAllowedButLargerIntersectingSectionsSplit() {
        WorldBorderBounds b=new WorldBorderBounds(5,5,51,51);
        long leaf=DhSectionPos.encode((byte)6,0,0),large=DhSectionPos.encode((byte)7,0,0);
        assertEquals(0,b.classify(leaf));assertNull(BorderRetrieval.check(b,leaf));assertTrue(b.canRetrieve(leaf));
        assertFalse(b.canRetrieve(large));
        assertEquals(com.seibel.distanthorizons.core.generation.tasks.ERetrievalResultState.REQUIRES_SPLITTING,BorderRetrieval.check(b,large).join().state);
        assertTrue(BorderRetrieval.check(b,DhSectionPos.encode((byte)6,1,0)).isCompletedExceptionally());
    }
    @Test public void leafGenerationOnlyVisitsIntersectingChunks() {
        WorldBorderBounds b=new WorldBorderBounds(5,5,29,29);int accepted=0;
        for(int x=0;x<4;x++)for(int z=0;z<4;z++)if(b.intersectsChunk(x,z)){accepted++;assertTrue(x<2&&z<2);}
        assertEquals(4,accepted);
    }
    @Test public void completionIgnoresOutsideChunksButIncludesAllBoundaryChunkColumns() {
        WorldBorderBounds b=new WorldBorderBounds(5,5,29,29);
        long section=DhSectionPos.encode((byte)6,0,0);int included=0;
        for(int x=0;x<64;x++)for(int z=0;z<64;z++) {
            assertEquals(x<32&&z<32,b.includesGenerationColumn(section,x,z));
            if(b.includesGenerationColumn(section,x,z))included++;
        }
        assertEquals(1024,included);
        WorldBorderBounds negative=new WorldBorderBounds(-29,-29,-5,-5);
        long n=DhSectionPos.encode((byte)6,-1,-1);
        assertTrue(negative.includesGenerationColumn(n,32,32));assertFalse(negative.includesGenerationColumn(n,31,32));
    }
    @Test public void partialGenerationCompletesWithoutWaitingForOutsideColumns() {
        WorldBorderBounds b=new WorldBorderBounds(5,5,29,29);long p=DhSectionPos.encode((byte)6,0,0);
        it.unimi.dsi.fastutil.bytes.ByteArrayList steps=new it.unimi.dsi.fastutil.bytes.ByteArrayList();
        for(int x=0;x<64;x++)for(int z=0;z<64;z++)steps.add((byte)(x<32&&z<32 ?
            com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiWorldGenerationStep.LIGHT.value :
            com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiWorldGenerationStep.EMPTY.value));
        assertTrue(BorderRetrieval.generationComplete(b,p,steps));assertFalse(BorderRetrieval.generationComplete(null,p,steps));
        steps.set(0,com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiWorldGenerationStep.EMPTY.value);
        assertFalse(BorderRetrieval.generationComplete(b,p,steps));
    }
    private BufferQuad quad(EDhDirection direction,int x,int z,int w,int h) {
        BufferQuad q=new BufferQuad();q.set((short)x,(short)50,(short)z,(short)w,(short)h,0xFE123456,(short)7,(byte)3,(byte)15,(byte)4,direction);return q;
    }
    @Test public void horizontalFacesClipAllFourEdgesIncludingNegativeWorldCoordinates() {
        WorldBorderBounds b=new WorldBorderBounds(-95,-94,-71,-72);
        for(EDhDirection d:new EDhDirection[]{EDhDirection.UP,EDhDirection.DOWN}) {
            BufferQuad q=quad(d,0,0,64,64);
            assertTrue(WorldBorderMeshClip.clip(q,b,-100,-100));
            assertEquals(5,q.x);assertEquals(6,q.z);assertEquals(24,q.widthEastWest);assertEquals(22,q.widthNorthSouthOrHeight);
            assertEquals(0xFE123456,q.color);assertEquals(50,q.y);assertEquals(7,q.textureTileId);assertEquals(3,q.irisBlockMaterialId);
            assertEquals(15,q.skyLight);assertEquals(4,q.blockLight);
        }
    }
    @Test public void sideFacesClipWidthButRetainTheirHeight() {
        WorldBorderBounds b=new WorldBorderBounds(5,6,29,28);
        for(EDhDirection d:new EDhDirection[]{EDhDirection.NORTH,EDhDirection.SOUTH}) {
            BufferQuad q=quad(d,0,6,64,32);assertTrue(WorldBorderMeshClip.clip(q,b,0,0));
            assertEquals(5,q.x);assertEquals(24,q.widthEastWest);assertEquals(32,q.widthNorthSouthOrHeight);
            assertFalse(WorldBorderMeshClip.clip(quad(d,0,30,64,32),b,0,0));
        }
        for(EDhDirection d:new EDhDirection[]{EDhDirection.EAST,EDhDirection.WEST}) {
            BufferQuad q=quad(d,5,0,64,32);assertTrue(WorldBorderMeshClip.clip(q,b,0,0));
            assertEquals(6,q.z);assertEquals(22,q.widthEastWest);assertEquals(32,q.widthNorthSouthOrHeight);
            assertFalse(WorldBorderMeshClip.clip(quad(d,30,0,64,32),b,0,0));
        }
    }
    @Test public void outsideAndZeroAreaFacesAreRejectedAndUnconfiguredMeshesRemainUntouched() {
        WorldBorderBounds b=new WorldBorderBounds(5,6,29,28);
        assertFalse(WorldBorderMeshClip.clip(quad(EDhDirection.UP,29,6,64,64),b,0,0));
        assertFalse(WorldBorderMeshClip.clip(quad(EDhDirection.DOWN,0,28,64,64),b,0,0));
        BufferQuad q=quad(EDhDirection.UP,0,0,64,64);assertTrue(WorldBorderMeshClip.clip(q,null,0,0));assertEquals(64,q.widthEastWest);
    }
    @Test public void structuralNtmColorsRemainVisibleWithoutTintingOtherMaterialsOrMods() {
        assertTrue(NtmLodPolicy.structural("hbm:tile.steel_scaffold:12",true));
        assertTrue(NtmLodPolicy.structural("hbm:tile.machine_assembler:15",true));
        assertFalse(NtmLodPolicy.structural("hbm:tile.crop_mint:0",false));
        assertFalse(NtmLodPolicy.structural("hbm:tile.fluid_oil:0",false));
        assertFalse(NtmLodPolicy.structural("other:tile.steel_scaffold:0",true));
        assertEquals(0xFF123456,NtmLodPolicy.color(true,0x10123456,0xFF778899));
        assertEquals(0xFF778899,NtmLodPolicy.color(true,0x00FFFFFF,0xFF778899));
        assertEquals(0x00123456,NtmLodPolicy.color(false,0x00123456,0xFF778899));
    }
    @Test public void invisibleLogicAndZeroHeightDummyPlatesDoNotBecomeLodCubes() {
        for(String s:new String[]{"hbm:tile.logic_block_invis:0","hbm:tile.dummy_plate_launch_table:0","hbm:tile.wand_jigsaw:0"}) {
            assertTrue(NtmLodPolicy.hidden(s));assertFalse(NtmLodPolicy.structural(s,true));
        }
        assertFalse(NtmLodPolicy.hidden("hbm:tile.dummy_port_launch_table:0"));
        assertFalse(NtmLodPolicy.hidden("hbm:tile.logic_block:0"));assertFalse(NtmLodPolicy.hidden("other:tile.logic_block_invis:0"));
    }
}
