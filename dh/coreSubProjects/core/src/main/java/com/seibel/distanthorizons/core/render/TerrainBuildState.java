package com.seibel.distanthorizons.core.render;

/** Short-held revision lock; never held while reading terrain, building geometry or uploading GL buffers. */
public final class TerrainBuildState {
    private long revision;
    private volatile boolean dirty;
    private volatile int refreshReasons;
    public synchronized void invalidate() { revision++;dirty=true; }
    public synchronized void invalidateForRefresh(int reasons) {
        invalidate();
        refreshReasons |= reasons;
    }
    public int getRefreshReasons() { return refreshReasons; }
    public synchronized long begin() { return revision; }
    public synchronized boolean complete(long builtRevision) {
        if(builtRevision!=revision) return false;
        refreshReasons=0;
        dirty=false;return true;
    }
    public boolean isDirty() { return dirty; }
}
