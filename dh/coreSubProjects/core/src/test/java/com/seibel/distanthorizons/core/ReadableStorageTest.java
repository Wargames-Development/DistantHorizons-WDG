package com.seibel.distanthorizons.core;
import org.junit.Test;import static org.junit.Assert.*;
import java.nio.file.*;import java.util.*;import java.nio.charset.StandardCharsets;
import com.seibel.distanthorizons.core.file.structure.ReadableWorldStorage;
import com.seibel.distanthorizons.core.dataObjects.transformers.LegacyLogFacePolicy;
import com.seibel.distanthorizons.core.network.messages.base.LevelInitMessage;
import io.netty.buffer.*;
public class ReadableStorageTest {
    @Test public void endpointIsPortableAndConnectionIndependent() {
        assertEquals("host.wargames.uk.27007",ReadableWorldStorage.endpointFolder("HOST.WARGAMES.UK:27007",false));
        assertEquals("host.wargames.uk.27007",ReadableWorldStorage.endpointFolder("host.wargames.uk:27007",true));
    }
    @Test public void portableEndpointHandlesIpv6AndWindowsReservedCharacters() {
        assertEquals("[2001%3Adb8%3A%3A1].25565",ReadableWorldStorage.endpointFolder("[2001:DB8::1]"));
        assertEquals("[fe80%3A%3A1%25en0].27007",ReadableWorldStorage.endpointFolder("[fe80::1%en0]:27007"));
        assertEquals("%63on.27007",ReadableWorldStorage.endpointFolder("CON:27007"));
        for(String host:Arrays.asList("a?b", "a*b", "a\"b", "a<b", "a>b", "a|b", "con", "nul", "COM1", "lpt9")) {
            String folder=ReadableWorldStorage.endpointFolder(host+":27007");
            assertFalse(folder,folder.matches(".*[\\\\/:*?\"<>|\\x00-\\x1f].*"));
            assertFalse(folder,folder.endsWith(".")||folder.endsWith(" "));
            assertEquals(folder,ReadableWorldStorage.endpointFolder(host+":27007",true));
            assertEquals(folder,ReadableWorldStorage.endpointFolder(host+":27007",false));
        }
        assertNotEquals(ReadableWorldStorage.endpointFolder("a?b:27007"),ReadableWorldStorage.endpointFolder("a%3fb:27007"));
        assertNotEquals(ReadableWorldStorage.endpointFolder("host:27007"),ReadableWorldStorage.endpointFolder("host:27008"));
        assertEquals(ReadableWorldStorage.endpointFolder("host"),ReadableWorldStorage.endpointFolder("HOST.:025565"));
    }
    @Test public void portableRootMigrationPreservesDatabaseAndIdentity() throws Exception {
        for(String oldName:ReadableWorldStorage.legacyEndpointFolders("host.wargames.uk:27007")) {
            Path base=Files.createTempDirectory("dh-portable-endpoint-");
            try {
                Path old;
                try {old=base.resolve(oldName);}catch(InvalidPathException windowsLiteralColon){continue;}
                Files.createDirectory(old);Path world=Files.createDirectory(old.resolve("earth40"));
                byte[] bytes=new byte[]{8,6,7,5,3,0,9};
                Files.write(world.resolve("DistantHorizons.sqlite"),bytes);
                Files.write(world.resolve(".dh-level-key"),"stable@overworld:0".getBytes(StandardCharsets.UTF_8));
                Path target=base.resolve(ReadableWorldStorage.endpointFolder("host.wargames.uk:27007"));
                assertEquals(old,com.seibel.distanthorizons.core.file.structure.EndpointStorageMigration.migrateUnique(target,Arrays.asList(old.toFile())));
                assertFalse(Files.exists(old));assertArrayEquals(bytes,Files.readAllBytes(target.resolve("earth40/DistantHorizons.sqlite")));
                assertEquals("stable@overworld:0",new String(Files.readAllBytes(target.resolve("earth40/.dh-level-key")),StandardCharsets.UTF_8));
                assertNull(com.seibel.distanthorizons.core.file.structure.EndpointStorageMigration.migrateUnique(target,Arrays.asList(old.toFile())));
            } finally {try(java.util.stream.Stream<Path> paths=Files.walk(base)){paths.sorted(Comparator.reverseOrder()).forEach(p->{try{Files.delete(p);}catch(Exception failure){throw new RuntimeException(failure);}});}}
        }
    }
    @Test public void worldNameRoundTripsSeparatelyFromStableIdentity() {
        LevelInitMessage src=new LevelInitMessage("overworld:0","","00000001kk71e@overworld:0","earth40");
        ByteBuf b=Unpooled.buffer();try{src.encode(b);LevelInitMessage dest=new LevelInitMessage();dest.decode(b);
            assertEquals("earth40",dest.storageWorldName);assertEquals(src.levelKey,dest.levelKey);assertFalse(b.isReadable());}finally{b.release();}
    }
    @Test public void migrationRetainsDatabaseAndRefusesDifferentWorldWithSameName() throws Exception {
        Path root=Files.createTempDirectory("dh-world-names-");String key="00000001kk71e@overworld:0";
        try {
            Path old=Files.createDirectory(root.resolve(key.replace(":","@@")));Files.write(old.resolve("DistantHorizons.sqlite"),new byte[]{3,1,4});
            Path active=ReadableWorldStorage.resolve(root.toFile(),"earth40",key).toPath();
            assertEquals(root.resolve("earth40"),active);assertFalse(Files.exists(old));assertArrayEquals(new byte[]{3,1,4},Files.readAllBytes(active.resolve("DistantHorizons.sqlite")));
            assertEquals(key+"\n",new String(Files.readAllBytes(active.resolve(".dh-level-key")),StandardCharsets.UTF_8));
            assertEquals(active,ReadableWorldStorage.resolve(root.toFile(),"earth40",key).toPath());
            try {ReadableWorldStorage.resolve(root.toFile(),"earth40","another@overworld:0");fail();}catch(IllegalStateException expected) { }
            assertArrayEquals(new byte[]{3,1,4},Files.readAllBytes(active.resolve("DistantHorizons.sqlite")));
            Files.createDirectory(root.resolve("unidentified"));
            try{ReadableWorldStorage.resolve(root.toFile(),"unidentified",key);fail();}catch(IllegalStateException expected) { }
            for(String bad:Arrays.asList("..","../escape","a/b","a\\b","", "bad:name", "a\nb")) {
                try{ReadableWorldStorage.validateName(bad);fail(bad);}catch(IllegalArgumentException expected) { }
            }
        }finally{try(java.util.stream.Stream<Path> paths=Files.walk(root)){paths.sorted(Comparator.reverseOrder()).forEach(p->{try{Files.delete(p);}catch(Exception e){throw new RuntimeException(e);}});}}
    }
    @Test public void everyLegacyLogAxisUsesBarkRatherThanCutEnd() {
        for(int meta=0;meta<16;meta++) {
            int face=LegacyLogFacePolicy.representativeFace(meta),axis=meta&12;
            if(axis==0)assertTrue(face!=0&&face!=1);
            if(axis==4)assertTrue(face!=4&&face!=5);
            if(axis==8)assertTrue(face!=2&&face!=3);
            assertTrue(face>=0&&face<6);
        }
    }
}
