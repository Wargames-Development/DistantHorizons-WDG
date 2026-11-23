package com.seibel.distanthorizons.forge17.actors;

import java.lang.reflect.Method;
import net.minecraft.entity.player.EntityPlayer;

/** Optional EFR bridge for the pose carried in the awake player's bed-yaw field. */
public final class EfrPlayerPose {
    private static final Method CAPTURE = find("captureLodOrientation");
    private static final Method BEGIN = find("beginLodRender");

    private EfrPlayerPose() {}

    public static float capture(EntityPlayer player, float bedYaw) {
        if (CAPTURE != null) {
            try { return (Float) CAPTURE.invoke(null, player, bedYaw); }
            catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) { }
        }
        return bedYaw;
    }

    public static Runnable begin(EntityPlayer actor, float poseOrBedYaw) {
        if (BEGIN != null && poseOrBedYaw < 0F) {
            try { return (Runnable) BEGIN.invoke(null, actor, poseOrBedYaw); }
            catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) { }
        }
        return null;
    }

    private static Method find(String name) {
        try {
            Class<?> api = Class.forName("ganymedes01.etfuturum.movement.PlayerRenderPose", false,
                    EfrPlayerPose.class.getClassLoader());
            return api.getMethod(name, EntityPlayer.class, float.class);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
            return null;
        }
    }
}
