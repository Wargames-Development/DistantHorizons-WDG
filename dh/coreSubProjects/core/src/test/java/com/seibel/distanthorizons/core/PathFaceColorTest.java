package com.seibel.distanthorizons.core;

import com.seibel.distanthorizons.core.dataObjects.render.textures.*;
import com.seibel.distanthorizons.core.dataObjects.transformers.EfrLodCompat;
import java.lang.reflect.Method;
import org.junit.Test;
import static org.junit.Assert.*;

public class PathFaceColorTest {
    @Test public void distinctSideColourSurvivesTopColourMultiplication() throws Exception {
        int top=0xffbe9962,side=0xff79553a;
        BlockFaceTexture face=BlockFaceTexture.createTexture(1,1,new int[]{side});
        Method convert=BlockTextureRegistry.class.getDeclaredMethod("convertColorsToDifferenceRatios",BlockFaceTexture.class);convert.setAccessible(true);
        // A uniform face previously vanished to flat shading, losing its distinct colour.
        assertNull(convert.invoke(null,face));
        byte[] encoded=(byte[])convert.invoke(null,face.relativeToColor(top));assertNotNull(encoded);
        for(int i=0;i<3;i++) {
            int base=(top>>(16-i*8))&255,target=(side>>(16-i*8))&255;
            double rendered=base*(encoded[i]&255)/127.5;
            assertEquals("dirt side retains its own colour rather than path-top colour",target,rendered,1.0);
        }
        assertNull(face.ratioReferenceColor);
        assertTrue(EfrLodCompat.distinctDirtFaces("etfuturum:grass_path"));
        assertTrue(EfrLodCompat.distinctDirtFaces("etfuturum:grass_path:0"));
        assertFalse(EfrLodCompat.distinctDirtFaces("other:grass_path"));
        assertFalse(EfrLodCompat.distinctDirtFaces("etfuturum:grass_path_fake"));
        for(int meta=0;meta<16;meta++) assertTrue(EfrLodCompat.distinctDirtFaces("minecraft:farmland:"+meta));
        assertFalse(EfrLodCompat.distinctDirtFaces("minecraft:farmland:16"));
        assertFalse(EfrLodCompat.distinctDirtFaces("minecraft:farmland:bad"));
    }
}
