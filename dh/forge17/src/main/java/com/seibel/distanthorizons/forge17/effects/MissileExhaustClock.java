package com.seibel.distanthorizons.forge17.effects;

/** Emission clocks have different epochs: local ticks for native poses and
 * server-world presentation ticks for relayed poses. Never compare the two. */
final class MissileExhaustClock {
    private int lastNativeTick=Integer.MIN_VALUE;
    private double lastRelayTick=Double.NEGATIVE_INFINITY;

    boolean admit(boolean tracked,int clientTick,double presentationTick) {
        if(tracked) {
            if(clientTick<=lastNativeTick) return false;
            lastNativeTick=clientTick;
        } else {
            if(presentationTick<=lastRelayTick) return false;
            lastRelayTick=presentationTick;
        }
        return true;
    }
}
