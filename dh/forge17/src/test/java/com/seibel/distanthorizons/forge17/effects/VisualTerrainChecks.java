package com.seibel.distanthorizons.forge17.effects;

import com.seibel.distanthorizons.forge17.actors.ActorModel;
import com.seibel.distanthorizons.forge17.actors.ActorTerrainVisibility;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/** Exercises the production queue and callback lifecycle without a world or GL context. */
public final class VisualTerrainChecks {
    private static int checks;
    private static void require(boolean condition,String message) {
        if(!condition) throw new AssertionError(message);checks++;
    }
    private static void set(Object target,String name,Object value) throws Exception {
        Field field=target.getClass().getDeclaredField(name);field.setAccessible(true);field.set(target,value);
    }
    public static int run() {
        try {
            VisualTerrainApi.beginFrame();
            List<Integer> collected=new ArrayList<>();
            VisualTerrainApi.register(1,p -> {
                require(VisualTerrainApi.collecting(),"collector is active only inside its view");collected.add(1);
                try { VisualTerrainApi.collect((float)p);throw new AssertionError("nested collection accepted"); }
                catch(IllegalStateException expected) { checks++; }
                java.util.concurrent.atomic.AtomicBoolean leaked=new java.util.concurrent.atomic.AtomicBoolean();
                Thread worker=new Thread(() -> leaked.set(VisualTerrainApi.collecting()));
                worker.start();try { worker.join(); } catch(InterruptedException error) { throw new AssertionError(error); }
                require(!leaked.get(),"render ownership cannot leak to a worker");
            });
            VisualTerrainApi.register(2,p -> collected.add(2));
            VisualTerrainApi.collect(.5f);
            require(collected.equals(java.util.Arrays.asList(1,2)),"both companion families collect once");
            require(!VisualTerrainApi.collecting(),"collection releases view ownership");
            VisualTerrainApi.register(1,p -> { throw new IllegalArgumentException("fixture"); });
            try { VisualTerrainApi.collect(0);throw new AssertionError("failure swallowed"); }
            catch(IllegalArgumentException expected) { checks++; }
            require(!VisualTerrainApi.collecting(),"failed collector releases view ownership");
            require(!VisualTerrainApi.submit(null,1,0,0,0,1,8,0,0,()->{}),"submission outside a view is rejected");
            Field access=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");access.setAccessible(true);
            ActorRenderer renderer=(ActorRenderer)((sun.misc.Unsafe)access.get(null)).allocateInstance(ActorRenderer.class);
            List<ActorModel> primary=new ArrayList<>();set(renderer,"submitted",primary);
            set(renderer,"terrain",new ActorTerrainVisibility());
            set(renderer,"cameraTerrain",new ActorTerrainVisibility());
            ActorCameraPass camera=new ActorCameraPass(renderer);set(renderer,"cameraPass",camera);
            int[] drawn={0};
            ActorModel model=new VisualTerrainApi.SubmittedModel(2,1,2,3,4,106,.5f,0,()->drawn[0]++);
            for(int i=0;i<VisualTerrainApi.MAX_MODELS;i++) require(renderer.submit(model),"bounded primary capacity");
            require(!renderer.submit(model),"primary overflow rejected");
            model.draw(1,2,3);require(drawn[0]==1 && model.radius()==106,"large animated geometry callback survives submission");
            set(camera,"active",true);
            for(int i=0;i<VisualTerrainApi.MAX_MODELS;i++) require(renderer.submit(model),"private view has its own bounded queue");
            require(!renderer.submit(model),"private overflow rejected");
            camera.end();set(camera,"active",true);
            require(renderer.submit(model),"ending a view discards its queued callbacks");
            require(primary.size()==VisualTerrainApi.MAX_MODELS,"private submissions never enter the primary queue");
            camera.end();
            return checks;
        } catch(Exception failure) { throw new AssertionError("Visual terrain fixture",failure); }
        finally { VisualTerrainApi.register(1,null);VisualTerrainApi.register(2,null); }
    }
}
