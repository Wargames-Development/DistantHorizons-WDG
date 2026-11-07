package com.seibel.distanthorizons.forge17.effects;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.particle.EntityFX;
import com.seibel.distanthorizons.core.config.Config;

/** Exact reviewed renderers plus attributed vanilla sprites; no namespace-wide particle matching. */
public final class EffectSources
{
    private static final Map<String,Integer> TYPES=new HashMap<String,Integer>();
    private static final WeakHashMap<EntityFX,Boolean> RELAYED=new WeakHashMap<EntityFX,Boolean>();
    private static final WeakHashMap<EntityFX,Integer> TAGGED=new WeakHashMap<EntityFX,Integer>();
    static
    {
        TYPES.put("com.seibel.distanthorizons.forge17.effects.MissileVisual",TrailSample.HBM);
        add(TrailSample.MCH,"mcheli.particles.","MCH_EntityParticleSmoke","MCH_EntityParticleExplode");
        add(TrailSample.HBM,"com.hbm.particle.","ParticleContrail","ParticleRocketFlame","ParticleRocketBF","ParticleRocketMUD",
            "ParticleRocketSCH","ParticleRocketUP","ParticleRocketFusion","ParticleExSmoke","ParticleExplosionSmall",
            "ParticleMukeWave","ParticleSmokePlume","ParticleMukeCloud","ParticleMukeCloudBF");
        add(TrailSample.FLANS,"com.flansmod.client.particle.","EntityRocketexhaust","EntityFMSmoke","EntityFMFlame",
            "EntitySmokeBurst","EntityFlare","EntityAfterburn","EntityCloudFX2","EntityCinematicSmoke","EntityEnvironmentalFX");
    }
    private EffectSources() { }
    private static void add(int family,String prefix,String... names) { for(String name:names) TYPES.put(prefix+name,family); }
    public static boolean enabled()
    {
        // Preserve the X3 file key; the UI label now describes all supported weapon effects.
        return Config.Client.Advanced.Graphics.GenericRendering.enableDistantMcheliSmoke.get()
            && Config.Client.Advanced.Graphics.GenericRendering.enableGenericRendering.get();
    }
    public static boolean enabled(int family)
    {
        if (!enabled()) return false;
        if (family==TrailSample.MCH) return Config.Client.Advanced.Graphics.GenericRendering.extendMcheliTrails.get();
        if (family==TrailSample.HBM) return Config.Client.Advanced.Graphics.GenericRendering.extendHbmTrails.get();
        if (family==TrailSample.FLANS) return Config.Client.Advanced.Graphics.GenericRendering.extendFlansTrails.get();
        return false;
    }
    public static int family(EntityFX particle)
    {
        Integer known=TYPES.get(particle.getClass().getName());
        if (known==null) known=TAGGED.get(particle);
        return known==null?0:known;
    }
    public static void added(EntityFX particle)
    {
        EffectOrigin.Source source=EffectOrigin.get();
        if (particle==null || source==null || !enabled(source.family)) return;
        if(source.remote && RELAYED.size()<8192) RELAYED.put(particle,Boolean.TRUE);
        String type=particle.getClass().getName();
        if (type.equals("net.minecraft.client.particle.EntitySmokeFX") || type.equals("net.minecraft.client.particle.EntityExplodeFX")
            || type.equals("net.minecraft.client.particle.EntityFlameFX") || type.equals("net.minecraft.client.particle.EntityCloudFX")
            || type.equals("net.minecraft.client.particle.EntityLargeExplodeFX"))
        {
            if (TAGGED.size()<2048) TAGGED.put(particle,source.family);
        }
        if (!source.remote && source.emitter!=null) TrailClient.nativeEmission(source.emitter);
    }
    public static boolean isRelayed(EntityFX particle) { return RELAYED.containsKey(particle); }
    public static void clear() { TAGGED.clear();RELAYED.clear(); }
    public static double extendedRange(double original)
    {
        EffectOrigin.Source source=EffectOrigin.get();
        return source!=null && enabled(source.family)
            ? Math.max(original,Config.Client.Advanced.Graphics.GenericRendering.distantSmokeDistance.get()) : original;
    }
}
