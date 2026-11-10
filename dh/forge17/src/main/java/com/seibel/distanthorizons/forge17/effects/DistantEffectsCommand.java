package com.seibel.distanthorizons.forge17.effects;

import java.util.List;
import com.seibel.distanthorizons.core.config.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.util.ChatComponentText;

/** Client command: no projectiles, gameplay explosions, packets to unknown servers, or chunk loads. */
public final class DistantEffectsCommand extends CommandBase
{
    public String getCommandName() { return "dheffects"; }
    public String getCommandUsage(ICommandSender sender) { return "/dheffects status|on|off|range <blocks>|debug|stop|probe <mcheli|hbm|fmur|rpg7> [distance] [seconds]"; }
    public int getRequiredPermissionLevel() { return 0; }
    public int compareTo(Object other) { return getCommandName().compareTo(((net.minecraft.command.ICommand)other).getCommandName()); }
    public void processCommand(ICommandSender sender,String[] args)
    {
        if(args.length==0 || args[0].equals("status")) {
            status(sender);
            com.seibel.distanthorizons.forge17.actors.PlayerClient players=com.seibel.distanthorizons.forge17.actors.PlayerClient.INSTANCE;
            if(players!=null) say(sender,players.diagnostic());
            if(com.seibel.distanthorizons.forge17.actors.VehicleClient.INSTANCE!=null) say(sender,com.seibel.distanthorizons.forge17.actors.VehicleClient.INSTANCE.diagnostic());
            if(ActorRenderer.INSTANCE!=null) say(sender,ActorRenderer.INSTANCE.diagnostic());
            return;
        }
        if(args[0].equals("on") || args[0].equals("off"))
        {
            Config.Client.Advanced.Graphics.GenericRendering.enableDistantMcheliSmoke.set(args[0].equals("on"));status(sender);return;
        }
        if(args[0].equals("range") && args.length==2)
        {
            Config.Client.Advanced.Graphics.GenericRendering.distantSmokeDistance.set(parseIntBounded(sender,args[1],64,16384));
            status(sender);return;
        }
        if(args[0].equals("debug"))
        {
            Config.Client.Advanced.Graphics.GenericRendering.showDistantEffectDiagnostics.set(
                !Config.Client.Advanced.Graphics.GenericRendering.showDistantEffectDiagnostics.get());status(sender);return;
        }
        if(args[0].equals("stop")) { TrailClient.INSTANCE.stopProbe();say(sender,"DH visual probe stopped; remaining puffs expire normally.");return; }
        if(!args[0].equals("probe") || args.length<2 || args.length>4) throw new WrongUsageException(getCommandUsage(sender));
        int family=args[1].equals("mcheli")?TrailSample.MCH:args[1].equals("hbm")?TrailSample.HBM:(args[1].equals("fmur") || args[1].equals("rpg7"))?TrailSample.FLANS:0;
        if(family==0 || (TrailClient.flags() & (1<<family))==0) { say(sender,"Enable DH distant effects and install/enable that source mod first.");return; }
        if(Minecraft.getMinecraft().thePlayer==null) return;
        int distance=args.length>2?parseIntBounded(sender,args[2],32,4096):400;
        int seconds=args.length>3?parseIntBounded(sender,args[3],1,30):15;
        if(args[1].equals("rpg7")) TrailClient.INSTANCE.probeRpg(distance,seconds);
        else TrailClient.INSTANCE.probe(family,distance,seconds);
        say(sender,"Stationary cosmetic probe "+distance+"m along your view, for "+seconds+"s. No missile is fired. Use /dheffects status or F3 diagnostics.");
        status(sender);
    }
    private static void say(ICommandSender sender,String text) { sender.addChatMessage(new ChatComponentText(text)); }
    private static void status(ICommandSender sender)
    {
        say(sender,TrailClient.INSTANCE.diagnostic());
        say(sender,TrailClient.INSTANCE.deliveryDiagnostic());
        if(DistantSmokeRenderer.INSTANCE!=null) say(sender,DistantSmokeRenderer.INSTANCE.diagnostic());
        TrailClient.INSTANCE.logStatus();
    }
    @Override
    public List addTabCompletionOptions(ICommandSender sender,String[] args)
    {
        if(args.length==1) return getListOfStringsMatchingLastWord(args,"status","on","off","range","debug","stop","probe");
        if(args.length==2 && args[0].equals("probe")) return getListOfStringsMatchingLastWord(args,"mcheli","hbm","fmur","rpg7");
        return null;
    }
}
