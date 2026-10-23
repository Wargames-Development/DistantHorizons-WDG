package com.seibel.distanthorizons.core.dataObjects.transformers;
/** Pick a bark face for the single representative LOD colour, preserving native metadata/texture lookup. */
public final class LegacyLogFacePolicy {
    private LegacyLogFacePolicy() { }
    public static int representativeFace(int metadata) {
        int axis=metadata&12;
        return axis==8?5:axis==12?1:2; // Z axis: east; all-bark: up; Y/X axis: north.
    }
}
