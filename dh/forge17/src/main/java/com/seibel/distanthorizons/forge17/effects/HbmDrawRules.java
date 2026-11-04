package com.seibel.distanthorizons.forge17.effects;

/** Strict eligibility for moving an inspected native append to the post-fade pass. */
public final class HbmDrawRules
{
    // Vanilla's existing layer limit: 4000 particles, ten quads per reviewed rocket puff.
    public static final int NEAR_CAPACITY=40000;
    private HbmDrawRules() { }
    public static boolean canRelocate(String type,int layer,int start,int end,int vertices,int added,int used,int needed)
    {
        return "com.hbm.particle.ParticleRocketFlame".equals(type) && layer==1
            && start>=0 && end-start==320 && vertices>=40 && added>=40
            && used>=0 && needed>0 && needed<=10 && used<=NEAR_CAPACITY-needed;
    }
}
