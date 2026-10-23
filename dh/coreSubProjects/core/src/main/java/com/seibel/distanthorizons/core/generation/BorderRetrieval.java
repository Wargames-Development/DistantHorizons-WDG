package com.seibel.distanthorizons.core.generation;
import com.seibel.distanthorizons.core.generation.tasks.DataSourceRetrievalResult;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import java.util.concurrent.CompletableFuture;
public final class BorderRetrieval {
    private BorderRetrieval() { }
    public static boolean generationComplete(WorldBorderBounds bounds,long pos,it.unimi.dsi.fastutil.bytes.ByteArrayList steps) {
        for(int i=0;i<steps.size();i++) {
            if(bounds!=null&&!bounds.includesGenerationColumn(pos,i/64,i%64))continue;
            byte step=steps.getByte(i);
            if(step==com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiWorldGenerationStep.EMPTY.value
                ||step==com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiWorldGenerationStep.DOWN_SAMPLED.value)return false;
        }
        return true;
    }
    public static CompletableFuture<DataSourceRetrievalResult> check(WorldBorderBounds b,long pos) {
        if(b==null||b.canRetrieve(pos))return null;
        if(b.classify(pos)==0&&DhSectionPos.getDetailLevel(pos)>DhSectionPos.SECTION_MINIMUM_DETAIL_LEVEL)
            return CompletableFuture.completedFuture(DataSourceRetrievalResult.CreateSplit());
        CompletableFuture<DataSourceRetrievalResult> result=new CompletableFuture<>();
        result.completeExceptionally(new java.util.concurrent.CancellationException("Outside configured WorldBorder"));return result;
    }
}
