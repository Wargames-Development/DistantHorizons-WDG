#version 150
uniform sampler2D uModelColour;
uniform sampler2D uModelDepth;
uniform sampler2D uDhDepth;
uniform sampler2D uNativeDepth;
uniform mat4 uInverseModelMvp;
uniform mat4 uDhMvp;
uniform mat4 uNativeMvp;
uniform vec3 uDhOriginOffset;
uniform bool uDhReverseDepth;
uniform bool uUseNativeDepth;
uniform bool uWriteDhDepth;
uniform bool uModelReverseDepth;
in vec2 screenUv;
out vec4 fragColor;
void main() {
    float depth=texture(uModelDepth,screenUv).r;
    if(uModelReverseDepth ? depth<=0.0 : depth>=1.0) discard;
    vec4 colour=texture(uModelColour,screenUv);
    if(colour.a<=0.001) discard;
    vec4 model=uInverseModelMvp*vec4(screenUv*2.0-1.0,depth*2.0-1.0,1.0);
    if(abs(model.w)<0.000001) discard;
    model=vec4(model.xyz/model.w,1.0);
    vec4 nativeClip=uNativeMvp*model;
    vec4 dhClip=uDhMvp*vec4(model.xyz+uDhOriginOffset,1.0);
    if(nativeClip.w<=0.0 || dhClip.w<=0.0) discard;
    vec3 nativePosition=nativeClip.xyz/nativeClip.w*0.5+0.5;
    vec3 dhPosition=dhClip.xyz/dhClip.w*0.5+0.5;
    if(any(lessThan(dhPosition.xy,vec2(0.0))) || any(greaterThan(dhPosition.xy,vec2(1.0)))) discard;
    bool beforeDh=uDhReverseDepth ? dhPosition.z>1.0 : dhPosition.z<0.0;
    if(!beforeDh) {
        if(dhPosition.z<0.0 || dhPosition.z>1.0) discard;
        float terrain=texture(uDhDepth,dhPosition.xy).r;
        if(uDhReverseDepth ? dhPosition.z<terrain-0.00000006 : dhPosition.z>terrain+0.00000006) discard;
    }
    if(uUseNativeDepth) {
        if(any(lessThan(nativePosition.xy,vec2(0.0))) || any(greaterThan(nativePosition.xy,vec2(1.0)))) discard;
        float nativeDepth=texture(uNativeDepth,nativePosition.xy).r;
        if(nativeDepth<1.0 && nativePosition.z>nativeDepth+0.00000006) discard;
    }
    // Model captures use premultiplied colour. Preserve their original alpha.
    fragColor=colour;
    gl_FragDepth=uWriteDhDepth ? clamp(dhPosition.z,0.0,1.0) : clamp(nativePosition.z,0.0,1.0);
}
