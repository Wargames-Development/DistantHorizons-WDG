package com.wimbli.WorldBorder;
import java.util.*;
/** Test-only fixture. */
public final class Config {
    public static Map<String,Border> borders=new HashMap<>();public static boolean round;
    public static Object Border(String world){return borders.get(world);}public static boolean ShapeRound(){return round;}
    public static final class Border {
        public double x,z;public int rx,rz;public Boolean shape;
        public double getX(){return x;}public double getZ(){return z;}public int getRadiusX(){return rx;}public int getRadiusZ(){return rz;}public Boolean getShape(){return shape;}
    }
}
