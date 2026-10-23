package com.seibel.distanthorizons.core.generation;
import com.seibel.distanthorizons.core.pos.DhChunkPos;
import java.util.*;
/** Server-thread-owned acquisition record. Cleanup can overtake a queued acquisition. */
public final class GenerationNeighborLease {
    private final List<DhChunkPos> acquired = new ArrayList<>();
    private boolean released;
    public boolean isReleased() { return this.released; }
    public void add(DhChunkPos position) {
        if (this.released) throw new IllegalStateException("Lease already released");
        this.acquired.add(position);
    }
    public List<DhChunkPos> release() {
        if (this.released) return Collections.emptyList();
        this.released = true;
        return Collections.unmodifiableList(this.acquired);
    }
}
