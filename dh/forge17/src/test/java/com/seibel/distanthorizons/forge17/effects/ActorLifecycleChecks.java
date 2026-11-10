package com.seibel.distanthorizons.forge17.effects;

import com.seibel.distanthorizons.forge17.actors.*;
import java.lang.reflect.*;
import java.util.*;

/** Exercise family cleanup without constructing GL/network owners or requiring a Minecraft launch. */
public final class ActorLifecycleChecks {
    private static void set(Object target,String name,Object value) throws ReflectiveOperationException {
        Field field=target.getClass().getDeclaredField(name);field.setAccessible(true);field.set(target,value);
    }
    public static int run() {
        try { return runChecked(); }
        catch(ReflectiveOperationException failure) { throw new AssertionError("Actor lifecycle fixture failed",failure); }
    }
    private static int runChecked() throws ReflectiveOperationException {
        Field access=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");access.setAccessible(true);sun.misc.Unsafe allocator=(sun.misc.Unsafe)access.get(null);
        ActorRenderer renderer=(ActorRenderer)allocator.allocateInstance(ActorRenderer.class);
        McheliActors vehicles=(McheliActors)allocator.allocateInstance(McheliActors.class);
        // Opaque vehicle handles must never be inspected or disposed by player-family cleanup.
        Map<UUID,Object> vehicleContacts=new HashMap<>();Object vehicle=new Object();UUID id=new UUID(1,2);vehicleContacts.put(id,vehicle);
        set(vehicles,"contacts",vehicleContacts);set(renderer,"vehicles",vehicles);
        Class<?> contact=Class.forName(ActorRenderer.class.getName()+"$Contact");
        Constructor<?> constructor=contact.getDeclaredConstructor(ActorRenderer.class,UUID.class);constructor.setAccessible(true);
        Map<UUID,Object> players=new HashMap<>();players.put(new UUID(3,4),constructor.newInstance(renderer,new UUID(3,4)));set(renderer,"contacts",players);
        PlayerFrameCache empty=new PlayerFrameCache();set(renderer,"sequence",empty.sequence());set(renderer,"receipt",empty.received());
        Method update=ActorRenderer.class.getDeclaredMethod("update",PlayerFrameCache.class);update.setAccessible(true);update.invoke(renderer,empty);
        if(!players.isEmpty()) throw new AssertionError("Empty player frame retained player models");
        if(vehicleContacts.size()!=1 || vehicleContacts.get(id)!=vehicle) throw new AssertionError("Player cleanup changed MCHELI model ownership");
        return 2;
    }
}
