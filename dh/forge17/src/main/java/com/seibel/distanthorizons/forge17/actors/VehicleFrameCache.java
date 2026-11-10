package com.seibel.distanthorizons.forge17.actors;

import java.util.UUID;

/** Client-thread session and expiry gate; complete frames replace the recipient set. */
public final class VehicleFrameCache {
    private Object connection;
    private UUID viewer;
    private int dimension;
    private long token,sequence=-1,received;
    private VehicleFrame frame;
    public void reset(Object connection,long token,UUID viewer,int dimension) {
        this.connection=connection;this.token=token;this.viewer=viewer;this.dimension=dimension;sequence=-1;received=0;frame=null;
    }
    public boolean accept(Object source,VehicleFrame frame,long receipt) {
        if(connection==null || source!=connection || token==0 || frame.subscription!=token || !frame.viewer.equals(viewer)
                || dimension!=frame.dimension || frame.sequence<=sequence) return false;
        this.frame=frame;sequence=frame.sequence;received=receipt;return true;
    }
    public void expire(long now) { if(now-received>1000000000L) frame=null; }
    public VehicleFrame frame() { return frame; }
    public long received() { return received; }
}
