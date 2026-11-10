package com.seibel.distanthorizons.forge17.actors;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** A sampled visual model on the render thread. Expensive preparation happens only after terrain/bounds gates. */
@SideOnly(Side.CLIENT)
public interface ActorModel {
    double x();
    double y();
    double centerY();
    double z();
    double radius();
    boolean prepare() throws Exception;
    int light();
    float partial();
    void draw(double relativeX,double relativeY,double relativeZ) throws Exception;
}
