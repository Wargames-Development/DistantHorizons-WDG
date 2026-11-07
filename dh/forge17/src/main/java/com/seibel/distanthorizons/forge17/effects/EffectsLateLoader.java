package com.seibel.distanthorizons.forge17.effects;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import com.gtnewhorizon.gtnhmixins.ILateMixinLoader;
import com.gtnewhorizon.gtnhmixins.LateMixin;
import cpw.mods.fml.relauncher.FMLLaunchHandler;

@LateMixin
public final class EffectsLateLoader implements ILateMixinLoader
{
    public String getMixinConfig() { return "DistantHorizons.effects.late.json"; }
    public List<String> getMixins(Set<String> mods)
    {
        List<String> result=new ArrayList<String>();
        boolean client=FMLLaunchHandler.side().isClient();
        if(mods.contains("mcheli"))
        {
            result.add("MixinMchTrailSource");
            if(client) { result.add("MixinMchSmokeCapture");result.add("MixinMchParticleRange"); }
        }
        if(mods.contains("hbm"))
        {
            result.add("MixinHbmTrailSource");result.add("MixinHbmExplosionSource");
            if(client) { result.add("MixinHbmParticleRange");result.add("MixinHbmMissileRender"); }
        }
        if(mods.contains("flansmod"))
        {
            result.add("MixinFlansTrailSource");
            if(client) { result.add("MixinFlansNativeSource");result.add("MixinFlansParticleRange"); }
        }
        org.apache.logging.log4j.LogManager.getLogger("DistantHorizons").info("Queued optional trail adapters ("+(client?"client":"server")+"): "+result);
        return result;
    }
}
