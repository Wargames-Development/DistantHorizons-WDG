package com.seibel.distanthorizons.core.render;

/** Short-held revision lock; never held while reading terrain, building geometry or uploading GL buffers. */
public final class TerrainBuildState {
    private long revision;
    private volatile boolean dirty;
    public synchronized void invalidate() { revision++;dirty=true; }
    public synchronized long begin() { return revision; }
    public synchronized boolean complete(long builtRevision) {
        if(builtRevision!=revision) return false;
        dirty=false;return true;
    }
    public boolean isDirty() { return dirty; }
}
