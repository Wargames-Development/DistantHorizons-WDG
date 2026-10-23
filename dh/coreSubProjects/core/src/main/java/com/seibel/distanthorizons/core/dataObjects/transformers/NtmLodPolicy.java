package com.seibel.distanthorizons.core.dataObjects.transformers;

/** Conservative colour/volume approximation for NTM structural blocks, never a model renderer. */
public final class NtmLodPolicy {
    private NtmLodPolicy() { }
    public static boolean hidden(String serial) {
        if(serial==null)return false;
        return serial.equals("hbm:tile.logic_block_invis") || serial.startsWith("hbm:tile.logic_block_invis:")
            || serial.equals("hbm:tile.wand_jigsaw") || serial.startsWith("hbm:tile.wand_jigsaw:")
            || serial.startsWith("hbm:tile.dummy_plate_");
    }
    public static boolean structural(String serial, boolean structuralMaterial) {
        if (!structuralMaterial || serial == null || !serial.startsWith("hbm:") || hidden(serial)) return false;
        return true;
    }
    public static int color(boolean structural, int sampled, int materialColor) {
        if (!structural) return sampled;
        // Sparse scaffold/fence pixels represent opaque metal; texture holes must not
        // make their distant volume disappear. Model-only blocks use material colour.
        return 0xFF000000 | (((sampled >>> 24) == 0 ? materialColor : sampled) & 0xFFFFFF);
    }
}
