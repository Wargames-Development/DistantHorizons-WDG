package com.seibel.distanthorizons.core.jar.updater;
/** This fork has no automatic update provider. User config cannot opt into upstream jars. */
public final class WdgUpdatePolicy {
    private WdgUpdatePolicy() { }
    public static boolean upstreamUpdatesDisabled() { return true; }
}
