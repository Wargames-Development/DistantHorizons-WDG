package com.seibel.distanthorizons.forge17.actors;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** Main-thread session gate. A full frame replaces the previous recipient set. */
public final class PlayerFrameCache {
    private Object connection;
    private long subscription,sequence=-1,lastReceived;
    private int dimension,range;
    private UUID viewer;
    private List<PlayerFrame.Pose> players=Collections.emptyList();

    public void reset(Object connection,long subscription,int dimension,UUID viewer) {
        this.connection=connection;this.subscription=subscription;this.dimension=dimension;this.viewer=viewer;
        sequence=-1;lastReceived=0;range=0;players=Collections.emptyList();
    }
    public boolean accept(Object source,PlayerFrame frame,long now) {
        if(connection==null || source!=connection || subscription==0 || subscription!=frame.subscription
                || dimension!=frame.dimension || !frame.viewer.equals(viewer) || frame.sequence<=sequence) return false;
        sequence=frame.sequence;lastReceived=now;range=frame.range;players=frame.players;return true;
    }
    public void expire(long now) {
        if(now-lastReceived>1000000000L) { players=Collections.emptyList();range=0; }
    }
    public List<PlayerFrame.Pose> players() { return players; }
    public int range() { return range; }
}
