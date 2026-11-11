package com.seibel.distanthorizons.forge17.actors;

/** Native torso orientation seeds the distant cosmetic pose; an idle handoff must not turn it. */
public final class PlayerBodyAlignment {
    private boolean seeded;
    private float offset,head,body;
    private long last;
    public void reset() { seeded=false;offset=0;last=0; }
    public float apply(float bodyYaw,float headYaw,Float nativeBody,long now) {
        if(nativeBody!=null) {
            offset=wrap(nativeBody-bodyYaw);head=headYaw;body=bodyYaw;seeded=true;last=now;
            return nativeBody;
        }
        if(!seeded) return bodyYaw;
        // The server torso field need not match the client's idle body helper.
        // Keep its native alignment while idle; release it smoothly when the
        // authoritative look/body direction changes, without turning the head.
        if(Math.abs(wrap(headYaw-head))>.01F || Math.abs(wrap(bodyYaw-body))>.01F) {
            double elapsed=Math.max(0,Math.min(.1,(now-last)/1_000_000_000.0));
            offset*=(float)Math.exp(-elapsed/.15);
        }
        last=now;return bodyYaw+offset;
    }
    public static float interpolate(float first,float last,float partial) { return first+wrap(last-first)*partial; }
    private static float wrap(float value) { return value-(float)Math.floor((value+180)/360)*360; }
}
