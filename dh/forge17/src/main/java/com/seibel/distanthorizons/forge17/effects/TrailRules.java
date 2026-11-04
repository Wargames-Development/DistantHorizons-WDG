package com.seibel.distanthorizons.forge17.effects;

/** Source-specific emission decisions, kept independent of rendering and networking. */
public final class TrailRules
{
    private TrailRules() { }
    public static String flansPrimary(boolean trails,int age,boolean vls,int delay,String trail,String boost)
    {
        if(!trails || age<=1) return null;
        if(delay>0 && boost!=null) return boost;
        return !vls || delay<=0?trail:null;
    }
    public static String flansBoost(boolean trails,int age,boolean accelerating,String boost)
    {
        return trails && age>1 && accelerating?boost:null;
    }
    public static String hbmFuel(String fuel)
    {
        if("BALEFIRE".equals(fuel)) return "exBalefire";
        if("HYDROGEN".equals(fuel)) return "exHydrogen";
        if("SOLID".equals(fuel)) return "exSolid";
        if("KEROSENE".equals(fuel) || "HYDRAZINE".equals(fuel) || "METHALOX".equals(fuel)) return "exKerosene";
        return null; // Xenon and unreviewed fuels do not acquire a substitute trail.
    }
    /** Trim only path already covered by an actual native emission; independent of client/server clock skew. */
    public static double coveredFraction(double ax,double ay,double az,double bx,double by,double bz,double px,double py,double pz)
    {
        double x=bx-ax,y=by-ay,z=bz-az,length=x*x+y*y+z*z;
        if(length<0.000001) return 0;
        double u=((px-ax)*x+(py-ay)*y+(pz-az)*z)/length;
        double dx=px-(ax+x*u),dy=py-(ay+y*u),dz=pz-(az+z*u);
        if(!Double.isFinite(u) || dx*dx+dy*dy+dz*dz>256) return 0;
        return Math.max(0,Math.min(1,u));
    }
    public static boolean continuous(long previousTick,String previousStyle,long tick,String style)
    {
        return tick>=previousTick && tick-previousTick<=1 && previousStyle.equals(style);
    }
}
