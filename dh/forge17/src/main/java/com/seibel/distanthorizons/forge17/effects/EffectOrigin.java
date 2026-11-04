package com.seibel.distanthorizons.forge17.effects;

import net.minecraft.entity.Entity;

/** Native source attribution only; common-safe because the same missile method runs on both sides. */
public final class EffectOrigin
{
    public static final class Source
    {
        public final Entity emitter;
        public final int family;
        public final boolean remote;
        public Source(Entity emitter, int family, boolean remote) { this.emitter=emitter; this.family=family; this.remote=remote; }
    }
    private static final ThreadLocal<Source> CURRENT = new ThreadLocal<Source>();
    private EffectOrigin() { }
    public static Source get() { return CURRENT.get(); }
    public static Source enter(Entity emitter, int family, boolean remote)
    {
        Source previous=CURRENT.get(); CURRENT.set(new Source(emitter, family, remote)); return previous;
    }
    public static void restore(Source previous)
    {
        if (previous == null) CURRENT.remove(); else CURRENT.set(previous);
    }
}
