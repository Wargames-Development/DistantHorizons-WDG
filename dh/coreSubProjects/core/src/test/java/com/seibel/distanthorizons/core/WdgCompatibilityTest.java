package com.seibel.distanthorizons.core;
import org.junit.Test;
import static org.junit.Assert.*;
import java.nio.file.*;
import java.util.*;
import com.seibel.distanthorizons.core.generation.*;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.file.structure.*;
import com.seibel.distanthorizons.core.dataObjects.transformers.*;
import com.seibel.distanthorizons.core.network.messages.base.WorldBorderMessage;
import com.seibel.distanthorizons.core.jar.updater.WdgUpdatePolicy;
import io.netty.buffer.Unpooled;
import io.netty.buffer.ByteBuf;
public class WdgCompatibilityTest {
    @Test public void endpointsIgnoreMenusAndNormalizeDefaultPort() {
        assertEquals("play.example.com:25565",ServerEndpointIdentity.canonical(" PLAY.Example.COM. "));
        assertEquals(ServerEndpointIdentity.canonical("play.example.com"),ServerEndpointIdentity.canonical("play.example.com:025565"));
        assertNotEquals(ServerEndpointIdentity.canonical("example.com:25565"),ServerEndpointIdentity.canonical("example.com:25566"));
        assertEquals("[2001:db8::1]:25565",ServerEndpointIdentity.canonical("[2001:DB8::1]"));
        assertEquals("[2001:db8::1]:25566",ServerEndpointIdentity.canonical("[2001:db8::1]:25566"));
        assertEquals("127.0.0.1:25565",ServerEndpointIdentity.canonical("127.0.0.1"));
        for(String bad:Arrays.asList("", "host/path", "host:0", "host:65536", "[broken", "some host")) {
            try{ServerEndpointIdentity.canonical(bad);fail(bad);}catch(IllegalArgumentException expected) { }
        }
    }
    @Test public void migrationMovesOnlyOneProvenDatasetAndNeverOverwrites() throws Exception {
        Path base=Files.createTempDirectory("dh-migration-");
        try {
            Path old=Files.createDirectory(base.resolve("old")), target=base.resolve("endpoint");
            Files.write(old.resolve("lod.sqlite"),new byte[]{1,2,3});
            assertEquals(old,EndpointStorageMigration.migrateUnique(target,Arrays.asList(old.toFile())));
            assertFalse(Files.exists(old));assertArrayEquals(new byte[]{1,2,3},Files.readAllBytes(target.resolve("lod.sqlite")));
            Path other=Files.createDirectory(base.resolve("other"));Files.write(other.resolve("lod.sqlite"),new byte[]{9});
            assertNull(EndpointStorageMigration.migrateUnique(target,Arrays.asList(other.toFile())));assertTrue(Files.exists(other));
            Path third=Files.createDirectory(base.resolve("third"));
            assertNull(EndpointStorageMigration.migrateUnique(base.resolve("ambiguous"),Arrays.asList(other.toFile(),third.toFile())));
            assertTrue(Files.exists(other));assertTrue(Files.exists(third));
        } finally {try(java.util.stream.Stream<Path> paths=Files.walk(base)){paths.sorted(Comparator.reverseOrder()).forEach(f->{try{Files.delete(f);}catch(Exception e){throw new RuntimeException(e);}});}}
    }
    @Test public void actualEarthBorderEdgesAndNegativeCoordinates() {
        WorldBorderBounds b=new WorldBorderBounds(-20992,-12288,20992,12288);
        assertTrue(b.containsChunk(-1312,-768));assertTrue(b.containsChunk(1311,767));
        assertFalse(b.containsChunk(-1313,0));assertFalse(b.containsChunk(1312,0));
        assertFalse(b.containsChunk(0,-769));assertFalse(b.containsChunk(0,768));
        assertEquals(1,b.classify(-20992,-12288,64));
        assertEquals(-1,b.classify(20992,0,64));assertEquals(0,b.classify(20960,0,64));
        WorldBorderBounds tiny=new WorldBorderBounds(-0.5,-1.5,15.5,14.5);assertFalse(tiny.containsChunk(0,0));
    }
    @Test public void splitOnlyIntersectingSectionsAndKeepUnconfiguredWorlds() {
        WorldBorderBounds b=new WorldBorderBounds(-20992,-12288,20992,12288);
        long inside=DhSectionPos.encode((byte)6,0,0), outside=DhSectionPos.encode((byte)6,1000,0);
        assertNull(BorderRetrieval.check(null,outside));assertNull(BorderRetrieval.check(b,inside));
        assertTrue(BorderRetrieval.check(b,outside).isCompletedExceptionally());
        boolean split=false;
        for(int x=-25;x<=25;x++) {
            long p=DhSectionPos.encode((byte)11,x,0);
            if(b.classify(p)==0){assertEquals(com.seibel.distanthorizons.core.generation.tasks.ERetrievalResultState.REQUIRES_SPLITTING,BorderRetrieval.check(b,p).join().state);split=true;}
        }
        assertTrue(split);
    }
    @Test public void borderMessageRoundTripAndRemoval() {
        WorldBorderMessage in=new WorldBorderMessage("minecraft:overworld",new WorldBorderBounds(-20992,-12288,20992,12288));
        ByteBuf data=Unpooled.buffer();try{
            in.encode(data);WorldBorderMessage out=new WorldBorderMessage();out.decode(data);assertFalse(data.isReadable());
            assertEquals(in.dimension,out.dimension);assertEquals(in.bounds,out.bounds);
            new WorldBorderMessage("minecraft:the_nether",null).encode(data);out.decode(data);assertNull(out.bounds);
        }finally{data.release();}
    }
    @Test public void visibleSnowAndAquaticMetadataStayDistinct() {
        assertTrue(EfrLodCompat.visibleVolume("etfuturum:powder_snow:0"));assertFalse(EfrLodCompat.visibleVolume("other:powder_snow:0"));
        assertFalse(EfrLodCompat.visibleVolume("etfuturum:powder_snow:999"));assertFalse(EfrLodCompat.visibleVolume("etfuturum:powder_snow:not-meta"));
        for(int m=0;m<16;m++) {
            assertTrue(EfrLodCompat.containsWater("etfuturum:seagrass:"+m));
            assertEquals((m&4)!=0,EfrLodCompat.containsWater("etfuturum:sea_pickle:"+m));
            assertEquals((m&8)!=0,EfrLodCompat.containsWater("etfuturum:tube_coral:"+m));
            assertFalse(EfrLodCompat.containsWater("etfuturum:tube_coral_block:"+m));
        }
        assertTrue(EfrLodCompat.submergedPlant("etfuturum:tall_seagrass:8"));assertFalse(EfrLodCompat.submergedPlant("etfuturum:tube_coral:0"));
        assertTrue(WdgUpdatePolicy.upstreamUpdatesDisabled());
    }
    @Test public void upstreamMetadataIsDisconnected() {
        assertFalse(com.seibel.distanthorizons.core.jar.installer.ModrinthGetter.init());
        com.seibel.distanthorizons.core.jar.installer.GitlabGetter getter=new com.seibel.distanthorizons.core.jar.installer.GitlabGetter("18204078");
        assertTrue(getter.projectPipelines.isEmpty());assertTrue(getter.getDownloads(1).isEmpty());
        assertTrue(getter.getPipelineInfo(1).isEmpty());assertTrue(getter.getCommitInfo("anything").isEmpty());
        assertNull(com.seibel.distanthorizons.core.jar.installer.GitlabGetter.getLatestForVersion("1.7.10"));
    }
    @Test public void invalidBordersAndAllFourCornerFaces() {
        for(double[] x:new double[][]{{Double.NaN,0,10,10},{0,0,Double.POSITIVE_INFINITY,10},{0,0,0,10},{0,0,10,-1}}) {
            try {new WorldBorderBounds(x[0],x[1],x[2],x[3]);fail();}catch(IllegalArgumentException expected) { }
        }
        WorldBorderBounds b=new WorldBorderBounds(-20992,-12288,20992,12288);
        for(int x:new int[]{-1312,1311})for(int z:new int[]{-768,767})assertTrue(b.containsChunk(x,z));
        assertNull(WorldBorderLookup.inspect(null));
    }
    public interface FakeAccess { }
    public interface FakeShearable { }
    public static class BaseBlock {public int colorMultiplier(FakeAccess access,int x,int y,int z){return 0xffffff;}}
    public static class BushWithoutTint extends BaseBlock implements FakeShearable { }
    public static class RealTint extends BaseBlock {@Override public int colorMultiplier(FakeAccess access,int x,int y,int z){return 0x123456;}}
    public static class SrgBase {public int func_149720_d(FakeAccess access,int x,int y,int z){return 0xffffff;}}
    public static class SrgTint extends SrgBase {@Override public int func_149720_d(FakeAccess access,int x,int y,int z){return 0x123456;}}
    @Test public void tintRequiresARealCallbackRatherThanBushOrShearableLabels() {
        assertFalse(LegacyTintPolicy.hasCustomColorCallback(BushWithoutTint.class,BaseBlock.class,FakeAccess.class));
        assertTrue(LegacyTintPolicy.hasCustomColorCallback(RealTint.class,BaseBlock.class,FakeAccess.class));
        assertFalse(LegacyTintPolicy.hasCustomColorCallback(SrgBase.class,SrgBase.class,FakeAccess.class));
        assertTrue(LegacyTintPolicy.hasCustomColorCallback(SrgTint.class,SrgBase.class,FakeAccess.class));
        assertTrue(LegacyTintPolicy.hasCustomColorCallback(Object.class,BaseBlock.class,FakeAccess.class));
    }
    @Test public void neighbourCleanupIsExactAndCancelsLateAcquisition() {
        GenerationNeighborLease lease=new GenerationNeighborLease();
        lease.add(new com.seibel.distanthorizons.core.pos.DhChunkPos(1311,767));
        lease.add(new com.seibel.distanthorizons.core.pos.DhChunkPos(1310,767));
        assertEquals(2,lease.release().size());assertTrue(lease.isReleased());assertTrue(lease.release().isEmpty());
        try {lease.add(new com.seibel.distanthorizons.core.pos.DhChunkPos(1312,767));fail();}catch(IllegalStateException expected) { }
        GenerationNeighborLease pending=new GenerationNeighborLease();assertTrue(pending.release().isEmpty());
        assertTrue(pending.isReleased());assertTrue(pending.release().isEmpty());
    }
}
