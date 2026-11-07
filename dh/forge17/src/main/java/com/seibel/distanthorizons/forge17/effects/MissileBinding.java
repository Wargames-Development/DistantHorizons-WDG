package com.seibel.distanthorizons.forge17.effects;

/** Forge 1.7.10 synchronizes spawn IDs, not non-player UUIDs. Scope bindings to a client world. */
final class MissileBinding {
    static boolean matches(int sourceId,String variant,int clientId,String clientClass) {
        if(sourceId!=clientId || variant==null || clientClass==null) return false;
        String prefix="com.hbm.entity.missile.";
        if(!clientClass.startsWith(prefix)) return false;
        int split=Math.max(clientClass.lastIndexOf('$'),clientClass.lastIndexOf('.'));
        return variant.equals(clientClass.substring(split+1));
    }
}
