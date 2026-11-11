package com.seibel.distanthorizons.forge17.effects;

import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.forge17.actors.*;
import java.lang.reflect.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;

/** Exercise native local-vehicle identity and cape policy without world/GL construction. */
public final class ActorNearViewChecks {
    private static Entity local,remote,pilot,view;
    private static boolean fail;
    public static Entity ridden(Entity rider) {
        if(fail) throw new IllegalStateException("injected resolver failure");
        return rider==pilot?local:rider==view?remote:null;
    }
    private static void set(Object object,String name,Object value) throws Exception {
        Field field=object.getClass().getDeclaredField(name);field.setAccessible(true);field.set(object,value);
    }
    static int run() {
        try { return check(); }catch(Exception error){throw new AssertionError("Near-view actor fixture",error);}
    }
    private static int check() throws Exception {
        Field access=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");access.setAccessible(true);sun.misc.Unsafe allocator=(sun.misc.Unsafe)access.get(null);
        Field singleton=null;for(Field f:Minecraft.class.getDeclaredFields()) if(f.getType()==Minecraft.class && Modifier.isStatic(f.getModifiers())) singleton=f;
        singleton.setAccessible(true);Object oldMinecraft=singleton.get(null);
        boolean capes=Config.Client.Advanced.Graphics.GenericRendering.enablePlayerLodCapes.get();int checks=0;
        try {
            require(ActorRenderer.sameNativeDepth(5890,41,5890,41),"different shader FBOs sharing native depth belong to one scene");checks++;
            require(!ActorRenderer.sameNativeDepth(5890,41,5890,42),"private scope and shadow depth cannot claim primary native draws");checks++;
            require(!ActorRenderer.sameNativeDepth(5890,41,36161,41),"texture and renderbuffer names are separate namespaces");checks++;
            require(!ActorRenderer.sameNativeDepth(0,0,0,0),"missing attachments cannot establish shared ownership");checks++;
            Minecraft mc=(Minecraft)allocator.allocateInstance(Minecraft.class);singleton.set(null,mc);
            mc.thePlayer=(net.minecraft.client.entity.EntityClientPlayerMP)allocator.allocateInstance(net.minecraft.client.entity.EntityClientPlayerMP.class);
            mc.renderViewEntity=(net.minecraft.entity.EntityLivingBase)allocator.allocateInstance(net.minecraft.entity.passive.EntityPig.class);
            pilot=mc.thePlayer;view=mc.renderViewEntity;local=(Entity)allocator.allocateInstance(net.minecraft.entity.passive.EntityPig.class);remote=(Entity)allocator.allocateInstance(net.minecraft.entity.passive.EntityPig.class);
            McheliActors vehicles=(McheliActors)allocator.allocateInstance(McheliActors.class);set(vehicles,"riddenVehicle",ActorNearViewChecks.class.getMethod("ridden",Entity.class));
            require(vehicles.localVehicle(local),"local pilot vehicle stays native");checks++;
            require(vehicles.localVehicle(remote),"vehicle attached to a separate view entity stays native");checks++;
            require(!vehicles.localVehicle(pilot) && !vehicles.localVehicle(null),"unrelated models retain distant eligibility");checks++;
            local=null;remote=null;
            require(!vehicles.localVehicle(pilot),"dismount releases native-only ownership");checks++;
            fail=true;require(vehicles.localVehicle(pilot),"resolver failure cannot steal native cockpit rendering");checks++;fail=false;
            VisualPlayerActor actor=(VisualPlayerActor)allocator.allocateInstance(VisualPlayerActor.class);
            AbstractClientPlayer nativePlayer=(AbstractClientPlayer)allocator.allocateInstance(net.minecraft.client.entity.EntityOtherPlayerMP.class);
            ResourceLocation cape=new ResourceLocation("test","cape.png");
            Field capeField=AbstractClientPlayer.class.getDeclaredField("locationCape");capeField.setAccessible(true);capeField.set(actor,cape);capeField.set(nativePlayer,cape);
            require(!Config.Client.Advanced.Graphics.GenericRendering.enablePlayerLodCapes.getDefaultValue(),"distant cape default is off");checks++;
            Config.Client.Advanced.Graphics.GenericRendering.enablePlayerLodCapes.setWithoutFiringEvents(false);
            require(!actor.hasCape() && actor.getLocationCape()==null,"disabled LOD cape cannot reach the native cape draw");checks++;
            require(nativePlayer.hasCape() && nativePlayer.getLocationCape()==cape,"nearby native capes are unaffected");checks++;
            Config.Client.Advanced.Graphics.GenericRendering.enablePlayerLodCapes.setWithoutFiringEvents(true);
            require(actor.hasCape() && actor.getLocationCape()==cape,"explicit opt-in preserves the native cape resource");checks++;
            for(Class<?> type:new Class<?>[]{ActorRenderer.class,McheliActors.class}) try(java.io.InputStream stream=type.getResourceAsStream(type.getSimpleName()+".class")) {
                org.spongepowered.asm.lib.tree.ClassNode node=new org.spongepowered.asm.lib.tree.ClassNode();new org.spongepowered.asm.lib.ClassReader(stream).accept(node,0);
                for(String methodName:type==ActorRenderer.class?new String[]{"replaceVehicle","observedVehicle"}:new String[]{"collect"}) {
                    boolean guarded=false;for(org.spongepowered.asm.lib.tree.MethodNode method:node.methods) if(method.name.equals(methodName))
                        for(org.spongepowered.asm.lib.tree.AbstractInsnNode insn:method.instructions.toArray()) if(insn instanceof org.spongepowered.asm.lib.tree.MethodInsnNode)
                            guarded|=((org.spongepowered.asm.lib.tree.MethodInsnNode)insn).name.equals("localVehicle");
                    require(guarded,"production "+methodName+" honours native cockpit ownership");checks++;
                }
            }
            try(java.io.InputStream stream=ActorRenderer.class.getResourceAsStream("ActorRenderer.class")) {
                org.spongepowered.asm.lib.tree.ClassNode node=new org.spongepowered.asm.lib.tree.ClassNode();new org.spongepowered.asm.lib.ClassReader(stream).accept(node,0);
                for(String name:new String[]{"nativePre","nativePost","primaryNativeView"}) {
                    boolean matched=false;
                    for(org.spongepowered.asm.lib.tree.MethodNode method:node.methods) if(method.name.equals(name))
                        for(org.spongepowered.asm.lib.tree.AbstractInsnNode insn:method.instructions.toArray()) if(insn instanceof org.spongepowered.asm.lib.tree.MethodInsnNode)
                            matched|=((org.spongepowered.asm.lib.tree.MethodInsnNode)insn).name.equals("nativeTarget");
                    require(matched,"native handoff uses shared depth identity in "+name);checks++;
                }
            }
            return checks;
        }finally {singleton.set(null,oldMinecraft);Config.Client.Advanced.Graphics.GenericRendering.enablePlayerLodCapes.setWithoutFiringEvents(capes);local=remote=pilot=view=null;fail=false;}
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
