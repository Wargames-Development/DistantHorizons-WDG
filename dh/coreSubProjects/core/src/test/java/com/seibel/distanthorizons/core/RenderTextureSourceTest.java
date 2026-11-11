package com.seibel.distanthorizons.core;
import com.seibel.distanthorizons.core.dataObjects.transformers.RenderTextureSource;
import com.seibel.distanthorizons.core.util.FullDataPointUtil;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import org.junit.Test;
import static org.junit.Assert.*;
public class RenderTextureSourceTest {
    @Test public void mergedTrunkCannotInheritLeavesAboveIt() throws Exception {
        LongArrayList column=new LongArrayList();
        column.add(FullDataPointUtil.encode(1,2,70,(byte)0,(byte)15));
        column.add(FullDataPointUtil.encode(2,6,64,(byte)0,(byte)15));
        column.add(FullDataPointUtil.encode(3,1,63,(byte)0,(byte)15));
        assertEquals(1,RenderTextureSource.select(column,64,72,2,id->id));
        assertEquals(0,RenderTextureSource.select(column,64,72,1,id->id));
        assertEquals(-1,RenderTextureSource.select(column,72,74,2,id->id));
        assertEquals(-1,RenderTextureSource.select(column,64,72,4,id->id));
    }
}
