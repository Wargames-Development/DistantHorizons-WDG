package com.seibel.distanthorizons.core.dataObjects.transformers;
import java.util.Locale;
/** Render approximations only: no collision, world-state or texture assets are changed. */
public final class EfrLodCompat {
    private EfrLodCompat() { }
    public static String name(String serial) {
        if (serial == null) return "";
        String s = serial.toLowerCase(Locale.ROOT);
        if (!s.startsWith("etfuturum:")) return "";
        int colon=s.lastIndexOf(':');
        if (colon>9) { try { int m=Integer.parseInt(s.substring(colon+1)); if(m<0||m>15)return ""; }
            catch(NumberFormatException bad){return "";} s=s.substring(0,colon); }
        return s.substring(10);
    }
    public static boolean visibleVolume(String serial) { return name(serial).equals("powder_snow"); }
    public static boolean submergedPlant(String serial) {
        String n=name(serial);
        return containsWater(serial) && (EfrAquaticLodCompat.isKelp(serial) || n.equals("seagrass") || n.equals("tall_seagrass")
            || n.equals("sea_pickle") || n.endsWith("_coral") || n.endsWith("_coral_fan") || n.endsWith("_coral_wall_fan"));
    }
    public static boolean containsWater(String serial) { return !name(serial).isEmpty() && EfrAquaticLodCompat.containsSourceWater(serial); }
}
