package com.seibel.distanthorizons.core.dataObjects.transformers;
/** Detect the real colour callback, not bush/shearable inheritance. Both MCP and SRG names are supported. */
public final class LegacyTintPolicy {
    private LegacyTintPolicy() { }
    public static boolean hasCustomColorCallback(Class<?> blockType,Class<?> baseType,Class<?> accessType) {
        for(String name:new String[]{"colorMultiplier","func_149720_d"}) {
            try {return blockType.getMethod(name,accessType,int.class,int.class,int.class).getDeclaringClass()!=baseType;}
            catch(NoSuchMethodException absent) { }
            catch(SecurityException unavailable) {return true;}
        }
        return true; // Unknown mappings: preserve the actual callback rather than guess.
    }
}
