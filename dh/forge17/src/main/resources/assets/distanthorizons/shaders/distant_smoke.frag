#version 150
uniform bool uNearPass;
uniform bool uWholeModel;
uniform bool uShaderPass;
// Zero for the visible pass; 1/2/3 are bounded, write-disabled GPU probes.
uniform int uDiagnosticStage;
uniform sampler2D uNativeDepth;
uniform sampler2D uCloudDepth;
uniform bool uUseCloudDepth;
uniform float uCloudRenderDistance;
in float viewDistance;
uniform sampler2D uSmoke;
uniform sampler2D uLightmap;
uniform sampler2D uDhDepth;
uniform bool uUseLightmap;
uniform bool uDhReverseDepth;
uniform float uAlphaCutoff;
in vec4 nativeClip;
in vec4 dhClip;
in vec2 uv;
in vec2 lightUv;
in vec4 color;
out vec4 fragColor;
out vec4 fragParticleMaterial;
void main() {
    if (nativeClip.w <= 0.0) discard;
    if (!uShaderPass && !uWholeModel && (uNearPass ? nativeClip.z > nativeClip.w : nativeClip.z <= nativeClip.w)) discard;
    vec3 projected = dhClip.xyz / max(dhClip.w, 0.00001) * 0.5 + 0.5;
    bool insideDh = dhClip.w > 0.0 && projected.x >= 0.0 && projected.x <= 1.0
        && projected.y >= 0.0 && projected.y <= 1.0 && projected.z >= 0.0 && projected.z <= 1.0;
    if(uShaderPass) {
        bool beforeDh = dhClip.w > 0.0 && projected.x >= 0.0 && projected.x <= 1.0
            && projected.y >= 0.0 && projected.y <= 1.0
            && (uDhReverseDepth ? projected.z > 1.0 : projected.z < 0.0);
        if((!insideDh && !beforeDh) || nativeClip.z < -nativeClip.w) discard;
        if(uDiagnosticStage==1) { fragColor=vec4(1.0);gl_FragDepth=clamp(projected.z,0.0,1.0);return; }
    }
    // Close native particles may be before DH's near plane. Native projection/depth
    // still handles them; only far replay requires a valid DH depth coordinate.
    if (!uShaderPass && !insideDh && !uNearPass && !(uWholeModel && nativeClip.z<=nativeClip.w)) discard;
    if (insideDh) {
        float terrainDepth = texture(uDhDepth, projected.xy).r;
        if (uDhReverseDepth) {
            if (projected.z < terrainDepth - 0.000001) discard;
        } else {
            if (projected.z > terrainDepth + 0.000001) discard;
        }
    }
    if(uShaderPass) {
        vec3 nativeProjected=nativeClip.xyz/nativeClip.w*0.5+0.5;
        float sceneDepth=texture(uNativeDepth,nativeProjected.xy).r;
        if(sceneDepth<1.0 && nativeProjected.z>sceneDepth+0.000001) discard;
        if(uDiagnosticStage==2) { fragColor=vec4(1.0);gl_FragDepth=clamp(projected.z,0.0,1.0);return; }
        if(uUseCloudDepth) {
            // Match the pack's texelFetch; filtering can invent cloud hit distances.
            ivec2 cloudSize=textureSize(uCloudDepth,0);
            float cloud=texelFetch(uCloudDepth,clamp(ivec2(nativeProjected.xy*vec2(cloudSize)),ivec2(0),cloudSize-1),0).a;
            if(cloud>0.0 && cloud*cloud*uCloudRenderDistance<min(viewDistance,uCloudRenderDistance)) discard;
        }
        if(uDiagnosticStage==3) { fragColor=vec4(1.0);gl_FragDepth=clamp(projected.z,0.0,1.0);return; }
    }
    vec4 smoke = texture(uSmoke, uv) * color;
    if (uUseLightmap) smoke.rgb *= texture(uLightmap, lightUv).rgb;
    if (smoke.a <= uAlphaCutoff) discard;
    fragColor = smoke;
    // Match BountifulStars/Complementary gbuffers_textured. Its later TAA uses
    // colortex6.g=254 to preserve particles instead of reprojecting terrain history.
    // Alpha one replaces the marker even when scene colour uses source-alpha blend.
    // This output has no attachment on other packs or the shaderless path.
    fragParticleMaterial=vec4(0.0,254.0/255.0,0.0,1.0);
    // Shaderless near geometry uses native depth; far replay uses native sky.
    // The shader scene uses DH depth, clamped at its near plane for close effects.
    gl_FragDepth = uShaderPass ? clamp(projected.z,0.0,1.0) : (uNearPass || (uWholeModel && nativeClip.z<=nativeClip.w)) ? nativeClip.z / nativeClip.w * 0.5 + 0.5 : 1.0;
}
