package com.seibel.distanthorizons.forge17.effects;

import org.lwjgl.opengl.GL15;

/** Bounded asynchronous GPU evidence. No readback wait, screenshots or flight commands.
 * At most 32 sampled frames per world, separated by two seconds; twelve reusable queries.
 * Diagnostic replays disable colour/depth writes and never change particle simulation. */
final class ShaderEffectsProbe {
    interface Queries {
        int current();int create();void begin(int id);void end();
        boolean available(int id);int result(int id);
    }
    private static final class OpenGlQueries implements Queries {
        public int current() { return GL15.glGetQueryi(GL15.GL_SAMPLES_PASSED,GL15.GL_CURRENT_QUERY); }
        public int create() { return GL15.glGenQueries(); }
        public void begin(int id) { GL15.glBeginQuery(GL15.GL_SAMPLES_PASSED,id); }
        public void end() { GL15.glEndQuery(GL15.GL_SAMPLES_PASSED); }
        public boolean available(int id) { return GL15.glGetQueryObjecti(id,GL15.GL_QUERY_RESULT_AVAILABLE)!=0; }
        // Angelica redirects by method name. Its runtime has the signed scalar
        // overload, but only a BUFFER overload of glGetQueryObjectui. Use the
        // supported scalar getter, retaining unsigned formatting for its raw bits.
        public int result(int id) { return GL15.glGetQueryObjecti(id,GL15.GL_QUERY_RESULT); }
    }
    private final Queries api;
    private boolean disabled,queryOpen;
    ShaderEffectsProbe() { this(new OpenGlQueries()); }
    ShaderEffectsProbe(Queries api) { this.api=api; }
    private final int[] queries=new int[12];
    private final boolean[] used=new boolean[12];
    private java.lang.ref.WeakReference<Object> world=new java.lang.ref.WeakReference<Object>(null);
    private int remaining;
    private long next;
    private boolean pending;
    private String context="",result="not sampled";
    boolean start(Object currentWorld,String description) {
        poll();
        if(disabled) return false;
        try {
            if(world.get()!=currentWorld) { world=new java.lang.ref.WeakReference<Object>(currentWorld);remaining=32;next=0; }
            if(pending || remaining==0 || System.nanoTime()<next
                || api.current()!=0) return false;
            remaining--;next=System.nanoTime()+2000000000L;context=description;
            java.util.Arrays.fill(used,false);pending=true;return true;
        } catch(RuntimeException | LinkageError error) { disable(error);return false; }
    }
    void begin(int kind,int stage) {
        if(disabled) return;
        try {
            int i=kind*4+stage;
            if(queries[i]==0) queries[i]=api.create();
            api.begin(queries[i]);queryOpen=true;used[i]=true;
        } catch(RuntimeException | LinkageError error) { disable(error); }
    }
    void end() {
        if(!queryOpen) return;
        try { api.end(); } catch(RuntimeException | LinkageError error) { disable(error); }
        finally { queryOpen=false; }
    }
    void poll() {
        if(disabled || !pending) return;
        try {
            for(int i=0;i<queries.length;i++) if(used[i]
                && !api.available(queries[i])) return;
            StringBuilder s=new StringBuilder(context).append(" GPU samples raster/depth/cloud/visible");
            String[] kinds={"body","plume","other"};
            for(int k=0;k<3;k++) {
                s.append(' ').append(kinds[k]).append('=');
                for(int stage=0;stage<4;stage++) {
                    if(stage>0) s.append('/');int i=k*4+stage;
                    if(used[i]) s.append(Integer.toUnsignedLong(api.result(queries[i])));
                    else s.append('-');
                }
            }
            result=s.toString();pending=false;
            org.apache.logging.log4j.LogManager.getLogger("DistantHorizons").info("DH effects pipeline probe: "+result);
        } catch(RuntimeException | LinkageError error) { disable(error); }
    }
    private void disable(Throwable error) {
        if(disabled) return;
        disabled=true;pending=false;queryOpen=false;
        result="diagnostics disabled: "+error.getClass().getSimpleName();
        org.apache.logging.log4j.LogManager.getLogger("DistantHorizons").warn(
            "Distant effects GPU diagnostics disabled; effect rendering continues",error);
    }
    String diagnostic() { return result; }
}
