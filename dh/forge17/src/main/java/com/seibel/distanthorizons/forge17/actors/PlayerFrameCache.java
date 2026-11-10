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
    private final java.util.LinkedHashMap<UUID,PlayerAppearance> appearances=new java.util.LinkedHashMap<>();
    private List<PlayerFrame.Pose> players=Collections.emptyList();

    public void reset(Object connection,long subscription,int dimension,UUID viewer) {
        this.connection=connection;this.subscription=subscription;this.dimension=dimension;this.viewer=viewer;
        sequence=-1;lastReceived=0;range=0;players=Collections.emptyList();appearances.clear();
    }
    public boolean accept(Object source,PlayerFrame frame,long now) {
        if(connection==null || source!=connection || subscription==0 || subscription!=frame.subscription
                || dimension!=frame.dimension || !frame.viewer.equals(viewer) || frame.sequence<=sequence) return false;
        sequence=frame.sequence;lastReceived=now;range=frame.range;players=frame.players;
        java.util.HashSet<UUID> retained=new java.util.HashSet<>();for(PlayerFrame.Pose pose:players) retained.add(pose.uuid);
        appearances.keySet().retainAll(retained);return true;
    }
    public boolean accept(Object source,PlayerAppearance appearance) {
        if(connection==null || source!=connection || subscription==0 || subscription!=appearance.subscription
                || dimension!=appearance.dimension || !appearance.viewer.equals(viewer)) return false;
        PlayerAppearance previous=appearances.get(appearance.player);
        if(previous!=null && previous.revision>appearance.revision) return false;
        if(previous==null && appearances.size()>=PlayerFrame.MAX_PLAYERS) appearances.remove(appearances.keySet().iterator().next());
        appearances.put(appearance.player,appearance);return true;
    }
    public PlayerAppearance appearance(PlayerFrame.Pose pose) {
        PlayerAppearance appearance=appearances.get(pose.uuid);
        return appearance!=null && appearance.entityId==pose.entityId && appearance.revision==pose.appearanceRevision?appearance:null;
    }
    public void expire(long now) {
        if(now-lastReceived>1000000000L) { players=Collections.emptyList();range=0; }
    }
    public List<PlayerFrame.Pose> players() { return players; }
    public int range() { return range; }
}
