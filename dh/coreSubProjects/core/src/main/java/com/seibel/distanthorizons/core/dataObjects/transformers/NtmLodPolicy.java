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
    /** Keep the shaded air/water below an NTM structure instead of filling it with its roof. */
    public static boolean preserveUnderside(String serial, byte material) {
        return structural(serial,
            material == com.seibel.distanthorizons.api.enums.rendering.EDhApiBlockMaterial.METAL.index
            || material == com.seibel.distanthorizons.api.enums.rendering.EDhApiBlockMaterial.STONE.index
            || material == com.seibel.distanthorizons.api.enums.rendering.EDhApiBlockMaterial.WOOD.index);
    }
    /** BlockPipe.getIcon uses Y as the pipe axis; placed metadata rotates it to X or Z. */
    public static int iconSide(String serial, int side, int meta) {
        if (!isDecorativePipe(serial)) return side;
        int axis = meta;
        if (axis == 4) return side == 4 || side == 5 ? 1 : 2;
        if (axis == 8) return side == 2 || side == 3 ? 1 : 2;
        return side;
    }
    /** A pipe's side paint represents it better than its dark end opening. */
    public static int representativeSide(String serial, int meta) {
        return isDecorativePipe(serial)
            ? (meta == 8 ? 4 : 2) : 1;
    }
    private static boolean isDecorativePipe(String serial) {
        return serial != null && (serial.equals("hbm:tile.deco_pipe")
            || serial.startsWith("hbm:tile.deco_pipe:") || serial.startsWith("hbm:tile.deco_pipe_"));
    }
    public static int color(boolean structural, int sampled, int materialColor) {
        if (!structural) return sampled;
        // Sparse scaffold/fence pixels represent opaque metal; texture holes must not
        // make their distant volume disappear. Model-only blocks use material colour.
        return 0xFF000000 | (((sampled >>> 24) == 0 ? materialColor : sampled) & 0xFFFFFF);
    }
}
