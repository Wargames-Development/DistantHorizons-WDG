package com.seibel.distanthorizons.forge17.actors;

import cpw.mods.fml.common.Loader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.entity.player.EntityPlayer;

/** Optional FMUR spectator filtering, isolated from client and optional class links. */
final class PlayerVisibility {
    private static boolean resolved,failed,present;
    private static Method data;
    private static Field team,spectators;
    static boolean allowed(EntityPlayer player) {
        if(!resolved) {
            resolved=true;present=Loader.isModLoaded("flansmod");
            if(present) try {
                ClassLoader loader=PlayerVisibility.class.getClassLoader();
                data=Class.forName("com.flansmod.common.PlayerHandler",false,loader).getMethod("getPlayerData",EntityPlayer.class);
                team=Class.forName("com.flansmod.common.PlayerData",false,loader).getField("team");
                spectators=Class.forName("com.flansmod.common.FlansMod",false,loader).getField("spectators");
            } catch(ReflectiveOperationException | LinkageError error) { fail(error); }
        }
        if(!present) return true;
        if(failed) return false;
        try { Object state=data.invoke(null,player);return state!=null && team.get(state)!=spectators.get(null); }
        catch(ReflectiveOperationException | RuntimeException | LinkageError error) { fail(error);return false; }
    }
    static boolean visibleTo(net.minecraft.entity.player.EntityPlayerMP target,net.minecraft.entity.player.EntityPlayerMP viewer) {
        if(!target.isInvisible()) return true;
        net.minecraft.scoreboard.Team team=target.getTeam();
        return team instanceof net.minecraft.scoreboard.ScorePlayerTeam && team==viewer.getTeam()
            && ((net.minecraft.scoreboard.ScorePlayerTeam)team).func_98297_h();
    }
    private static void fail(Throwable error) {
        failed=true;
        org.apache.logging.log4j.LogManager.getLogger("DistantHorizons").warn("DH player visual delivery disabled: FMUR visibility policy unavailable",error);
    }
}
