package com.seibel.distanthorizons.core.generation;
import com.seibel.distanthorizons.core.generation.tasks.DataSourceRetrievalResult;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import java.util.concurrent.CompletableFuture;
public final class BorderRetrieval {
    private BorderRetrieval() { }
    public static CompletableFuture<DataSourceRetrievalResult> check(WorldBorderBounds b,long pos) {
        if(b==null||b.classify(pos)==1)return null;
        if(b.classify(pos)==0&&DhSectionPos.getDetailLevel(pos)>DhSectionPos.SECTION_MINIMUM_DETAIL_LEVEL)
            return CompletableFuture.completedFuture(DataSourceRetrievalResult.CreateSplit());
        CompletableFuture<DataSourceRetrievalResult> result=new CompletableFuture<>();
        result.completeExceptionally(new java.util.concurrent.CancellationException("Outside configured WorldBorder"));return result;
    }
}
