package com.seibel.distanthorizons.forge17.effects;

/** Shared visual subscription mask. Pure Java; safe on the dedicated server. */
final class EffectSupportPolicy {
    private EffectSupportPolicy() { }

    static int allowedFlags(boolean ntm, boolean mcheli, boolean fmur) {
        return (ntm ? 1 << TrailSample.HBM : 0)
            | (mcheli ? 1 << TrailSample.MCH : 0)
            | (fmur ? 1 << TrailSample.FLANS : 0);
    }

    static boolean permits(int flags, int family) {
        return family >= TrailSample.MCH && family <= TrailSample.FLANS
            && (flags & (1 << family)) != 0;
    }
}
