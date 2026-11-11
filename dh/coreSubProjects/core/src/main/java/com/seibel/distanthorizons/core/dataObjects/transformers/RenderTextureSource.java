package com.seibel.distanthorizons.core.dataObjects.transformers;

import com.seibel.distanthorizons.core.util.FullDataPointUtil;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.util.function.IntUnaryOperator;

/** Select a texture from the material actually retained by vertical LOD reduction. */
public final class RenderTextureSource {
    private RenderTextureSource() { }
    public static int select(LongArrayList column,int bottom,int top,int material,IntUnaryOperator materialForId) {
        int selected=-1,largestOverlap=0;
        for(int index=0;index<column.size();index++) {
            long point=column.getLong(index);int y=FullDataPointUtil.getBottomY(point);
            int overlap=Math.min(top,y+FullDataPointUtil.getHeight(point))-Math.max(bottom,y);
            if(overlap<=0 || materialForId.applyAsInt(FullDataPointUtil.getId(point))!=material) continue;
            if(overlap>largestOverlap) { largestOverlap=overlap;selected=index; }
        }
        return selected;
    }
}
