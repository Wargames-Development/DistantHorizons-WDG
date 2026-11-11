#version 150
uniform sampler2D uModelColour;
uniform sampler2D uModelDepth;
uniform sampler2D uDhDepth;
uniform sampler2D uNativeDepth;
uniform sampler2D uCloudDepth;
uniform bool uUseCloudDepth;
uniform float uCloudRenderDistance;
uniform mat4 uNativeView;
uniform bool uSceneDepth;
uniform mat4 uDhFromNativeClip;
uniform mat4 uInverseModelMvp;
uniform mat4 uDhMvp;
uniform mat4 uNativeMvp;
uniform vec3 uDhOriginOffset;
uniform bool uDhReverseDepth;
uniform bool uUseNativeDepth;
uniform bool uWriteDhDepth;
uniform bool uModelReverseDepth;
uniform bool uMarkerOverlay;
uniform bool uOpaqueDepthOnly;
uniform bool uOccludeWorld;
uniform float uMarkerDepth;
uniform float uMarkerTerrainDepth;
uniform vec2 uMarkerAnchor;
uniform bool uAuxiliary;
uniform bool uAuxiliaryHeat;
// native projection Z/offset, model-view eye origin, compression start
uniform vec4 uAuxiliaryDepth;
in vec2 screenUv;
out vec4 fragColor;
out vec4 fragActorMaterial;
out vec4 fragActorNormal;
void main() {
    float depth=uMarkerOverlay?uMarkerTerrainDepth:texture(uModelDepth,screenUv).r;
    if(!uMarkerOverlay && (uModelReverseDepth ? depth<=0.0 : depth>=1.0)) discard;
    vec4 colour=texture(uModelColour,screenUv);
    if(colour.a<=0.001 || (uOpaqueDepthOnly && colour.a<0.999)) discard;
    vec2 xy=screenUv*2.0-1.0;
    float z=depth*2.0-1.0;
    if(uSceneDepth) {
        // The shared scene stores native XY but DH Z. Their camera origins can
        // differ, so solve for native clip Z along this pixel's ray first.
        vec4 base=uDhFromNativeClip*vec4(xy,0.0,1.0);
        vec4 axis=uDhFromNativeClip[2];
        float denominator=axis.z-z*axis.w;
        if(abs(denominator)<0.0000000001) discard;
        z=(z*base.w-base.z)/denominator;
    }
    vec4 model=uInverseModelMvp*vec4(xy,z,1.0);
    if(abs(model.w)<0.000001) discard;
    model=vec4(model.xyz/model.w,1.0);
    if(uAuxiliary) {
        float distance=uAuxiliaryDepth.z-(uNativeView*model).z;
        if(distance<=0.0) discard;
        float start=uAuxiliaryDepth.w;
        float eye=uAuxiliaryDepth.z-distance;
        float encoded=0.5*((uAuxiliaryDepth.x*eye+uAuxiliaryDepth.y)/-eye+1.0);
        if(distance>start) {
            float beginEye=uAuxiliaryDepth.z-start;
            float beginDepth=0.5*((uAuxiliaryDepth.x*beginEye+uAuxiliaryDepth.y)/-beginEye+1.0);
            float span=log(4096.0/start);
            float shoulder=0.05/(1.0/start-0.80/(start*span));
            float capped=min(distance,4096.0);
            float fraction=0.05*(1.0-exp(-(capped-start)/shoulder))+0.80*log(capped/start)/span;
            if(distance>4096.0) fraction=0.85+0.15*(1.0-4096.0/distance);
            encoded=beginDepth+(1.0-beginDepth)*fraction;
        }
        fragColor=uAuxiliaryHeat?vec4(vec3(colour.a),colour.a):colour;
        fragActorMaterial=vec4(0.0);
        fragActorNormal=vec4(0.0);
        gl_FragDepth=clamp(encoded,0.0,1.0);
        return;
    }
    vec4 nativeClip=uNativeMvp*model;
    vec4 terrainModel=model;
    if(uMarkerOverlay) {
        terrainModel=uInverseModelMvp*vec4(uMarkerAnchor,uMarkerDepth*2.0-1.0,1.0);
        if(abs(terrainModel.w)<0.000001) discard;
        terrainModel=vec4(terrainModel.xyz/terrainModel.w,1.0);
    }
    vec4 dhClip=uDhMvp*vec4(terrainModel.xyz+uDhOriginOffset,1.0);
    if(nativeClip.w<=0.0 || dhClip.w<=0.0) discard;
    vec3 nativePosition=nativeClip.xyz/nativeClip.w*0.5+0.5;
    vec3 dhPosition=dhClip.xyz/dhClip.w*0.5+0.5;
    if(any(lessThan(dhPosition.xy,vec2(0.0))) || any(greaterThan(dhPosition.xy,vec2(1.0)))) discard;
    bool beforeDh=uDhReverseDepth ? dhPosition.z>1.0 : dhPosition.z<0.0;
    if(uOccludeWorld && !beforeDh) {
        if(dhPosition.z<0.0 || dhPosition.z>1.0) discard;
        float terrain=texture(uDhDepth,dhPosition.xy).r;
        if(uDhReverseDepth ? dhPosition.z<terrain-0.00000006 : dhPosition.z>terrain+0.00000006) discard;
    }
    if(uMarkerOverlay) nativePosition.z=uMarkerDepth;
    if(uOccludeWorld && uUseNativeDepth) {
        if(any(lessThan(nativePosition.xy,vec2(0.0))) || any(greaterThan(nativePosition.xy,vec2(1.0)))) discard;
        float nativeDepth=texture(uNativeDepth,nativePosition.xy).r;
        if(nativeDepth<1.0 && nativePosition.z>nativeDepth+0.00000006) discard;
    }
    if(uUseCloudDepth) {
        ivec2 size=textureSize(uCloudDepth,0);
        float cloud=texelFetch(uCloudDepth,clamp(ivec2(nativePosition.xy*vec2(size)),ivec2(0),size-1),0).a;
        float distance=length((uNativeView*model).xyz);
        if(cloud>0.0 && cloud*cloud*uCloudRenderDistance<min(distance,uCloudRenderDistance)) discard;
    }
    // Model captures use premultiplied colour. Preserve their original alpha.
    fragColor=colour;
    fragActorMaterial=vec4(0.0,254.0/255.0,0.0,1.0);
    // Capture is already lit. Never inherit the glossy terrain normal/fresnel underneath it.
    fragActorNormal=vec4(0.0,1.0,0.0,0.0);
    gl_FragDepth=uWriteDhDepth ? clamp(dhPosition.z,0.0,1.0) : clamp(nativePosition.z,0.0,1.0);
}
