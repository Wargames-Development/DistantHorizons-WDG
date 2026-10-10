/*
 * EFR aquatic LOD compatibility for Minecraft 1.7.10.
 * No dependency on EFR's classes, client-side texture objects or world access.
 * Only interpret identities and bit masks that EFR persists in ID + metadata.
 */
package com.seibel.distanthorizons.core.dataObjects.transformers;

import java.util.Locale;

/** Minecraft 1.7.10 EFR submerged-cell classification and colour approximation. */
public final class EfrAquaticLodCompat
{
    private static final String PREFIX = "etfuturum:";
    private EfrAquaticLodCompat() { }

    /** Only EFR kelp (including the extended-age and body variants) is drawn below water. */
    public static boolean isKelp(String serial)
    {
        if (serial == null) return false;
        String value = serial.toLowerCase(Locale.ROOT);
        if (!value.startsWith(PREFIX)) return false;

        int lastColon = value.lastIndexOf(':');
        String name;
        if (lastColon >= PREFIX.length())
        {
            try { Integer.parseInt(value.substring(lastColon + 1)); }
            catch (NumberFormatException ignored) { return false; }
            name = value.substring(PREFIX.length(), lastColon);
        }
        else
        {
            name = value.substring(PREFIX.length());
        }

        return name.equals("kelp") || name.equals("kelp_age_16") || name.equals("kelp_plant");
    }

    /**
     * A DH render cell cannot contain both its water cube and a thin plant mesh.
     * Keep the *water* material and its existing alpha, but colour submerged kelp
     * water volumes toward the actual plant colour. This is a conservative visual
     * approximation, not a model or block-state replacement.
     *
     * Do not colour a lake's top cell: the caller enforces water above the kelp.
     */
    public static int tintSubmergedKelp(int waterArgb, int kelpArgb)
    {
        // Some cross-plane textures return fully transparent samples. Use a
        // restrained green fallback rather than a black or invisible LOD.
        int plantRgb = ((kelpArgb >>> 24) & 0xff) == 0 ? 0x376C32 : (kelpArgb & 0xffffff);
        final int waterWeight = 45;
        final int kelpWeight = 55;
        int r = ((((waterArgb >>> 16) & 0xff) * waterWeight) + (((plantRgb >>> 16) & 0xff) * kelpWeight)) / 100;
        int g = ((((waterArgb >>>  8) & 0xff) * waterWeight) + (((plantRgb >>>  8) & 0xff) * kelpWeight)) / 100;
        int b = (((waterArgb & 0xff) * waterWeight) + ((plantRgb & 0xff) * kelpWeight)) / 100;
        return (waterArgb & 0xff000000) | (r << 16) | (g << 8) | b;
    }

    /**
     * The EFR aquatic identities encode water in metadata rather than providing
     * a vanilla water block at the same coordinates. Do not guess water from
     * the block material: dry coral and sea pickles use similar materials.
     */
    public static boolean containsSourceWater(String serial)
    {
        if (serial == null) return false;
        String value = serial.toLowerCase(Locale.ROOT);
        if (!value.startsWith(PREFIX)) return false;

        int lastColon = value.lastIndexOf(':');
        int meta = 0;
        if (lastColon > PREFIX.length() - 1)
        {
            try { meta = Integer.parseInt(value.substring(lastColon + 1)); }
            catch (NumberFormatException ignored) { return false; }
        }
        String name = value.substring(PREFIX.length(),
            lastColon > PREFIX.length() - 1 ? lastColon : value.length());

        if (name.equals("kelp") || name.equals("kelp_age_16")
            || name.equals("kelp_plant") || name.equals("seagrass")
            || name.equals("tall_seagrass")) return true;
        if (name.equals("sea_pickle")) return (meta & 4) != 0;

        if (name.equals("small_dripleaf")) return (meta & 8) != 0;
        if (name.equals("big_dripleaf_stem")) return (meta & 4) != 0;
        if (name.equals("big_dripleaf_wet")) return true;

        // EFR's non-cubic coral/fan identities use bit 8; the opaque
        // coral _block variants have no waterlogged state.
        return (name.endsWith("_coral") || name.endsWith("_coral_fan")
            || name.endsWith("_coral_wall_fan")) && (meta & 8) != 0;
    }

}
