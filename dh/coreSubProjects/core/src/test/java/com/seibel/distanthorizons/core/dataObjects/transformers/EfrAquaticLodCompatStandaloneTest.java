package com.seibel.distanthorizons.core.dataObjects.transformers;

/** Standalone Java smoke test; no external dependencies. */
public final class EfrAquaticLodCompatStandaloneTest
{
    private static void assertWet(String id, boolean expected)
    {
        if (EfrAquaticLodCompat.containsSourceWater(id) != expected)
            throw new AssertionError(id + " expected wet=" + expected);
    }

    private static void assertKelp(String id, boolean expected)
    {
        if (EfrAquaticLodCompat.isKelp(id) != expected)
            throw new AssertionError(id + " expected kelp=" + expected);
    }

    private static void assertTint()
    {
        int water = 0x7A286AC3;
        int kelp = 0xFF287D32;
        int tint = EfrAquaticLodCompat.tintSubmergedKelp(water, kelp);
        if ((tint >>> 24) != (water >>> 24))
            throw new AssertionError("Submerged kelp must retain original water alpha");
        if (tint == water || (tint & 0xffffff) == (kelp & 0xffffff))
            throw new AssertionError("Expected a controlled mix of water and kelp, not either unchanged");
        if ((EfrAquaticLodCompat.tintSubmergedKelp(water, 0x00000000) & 0xffffff) == 0)
            throw new AssertionError("Transparent sprite samples must use visible fallback green");
    }

    public static void main(String[] args)
    {
        assertWet("etfuturum:kelp", true);
        assertWet("etfuturum:kelp_age_16:9", true);
        assertWet("etfuturum:kelp_plant", true);
        assertWet("etfuturum:seagrass", true);
        assertWet("etfuturum:tall_seagrass:1", true);
        assertWet("etfuturum:sea_pickle:4", true);
        assertWet("etfuturum:sea_pickle:3", false);
        assertWet("etfuturum:tube_coral:8", true);
        assertWet("etfuturum:dead_horn_coral_fan:8", true);
        assertWet("etfuturum:dead_horn_coral_wall_fan:2", false);
        assertWet("etfuturum:horn_coral_block", false);
        assertWet("etfuturum:small_dripleaf:8", true);
        assertWet("etfuturum:small_dripleaf:2", false);
        assertWet("etfuturum:big_dripleaf_stem:4", true);
        assertWet("etfuturum:big_dripleaf_stem:2", false);
        assertWet("etfuturum:big_dripleaf_wet", true);
        assertWet("etfuturum:big_dripleaf", false);
        assertWet("minecraft:water", false);
        assertWet("othermod:kelp:0", false);
        assertKelp("etfuturum:kelp", true);
        assertKelp("etfuturum:kelp:15", true);
        assertKelp("etfuturum:kelp_plant:0", true);
        assertKelp("etfuturum:kelp_age_16:9", true);
        assertKelp("etfuturum:seagrass", false);
        assertKelp("etfuturum:sea_pickle:4", false);
        assertKelp("othermod:kelp:2", false);
        assertKelp("etfuturum:kelp:garbage", false);
        assertTint();
        assertWet("etfuturum:sea_pickle:bad", false);
        System.out.println("PASS: EFR aquatic metadata; only submerged kelp receives water-alpha-preserving tint");
    }
}
