#version 150
uniform bool uNearPass;
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
void main() {
    if (nativeClip.w <= 0.0) discard;
    if (uNearPass ? nativeClip.z > nativeClip.w : nativeClip.z <= nativeClip.w) discard;
    vec3 projected = dhClip.xyz / max(dhClip.w, 0.00001) * 0.5 + 0.5;
    bool insideDh = dhClip.w > 0.0 && projected.x >= 0.0 && projected.x <= 1.0
        && projected.y >= 0.0 && projected.y <= 1.0 && projected.z >= 0.0 && projected.z <= 1.0;
    // Close native particles may be before DH's near plane. Native projection/depth
    // still handles them; only far replay requires a valid DH depth coordinate.
    if (!insideDh && !uNearPass) discard;
    if (insideDh) {
        float terrainDepth = texture(uDhDepth, projected.xy).r;
        if (uDhReverseDepth) {
            if (projected.z < terrainDepth - 0.000001) discard;
        } else {
            if (projected.z > terrainDepth + 0.000001) discard;
        }
    }
    vec4 smoke = texture(uSmoke, uv) * color;
    if (uUseLightmap) smoke.rgb *= texture(uLightmap, lightUv).rgb;
    if (smoke.a <= uAlphaCutoff) discard;
    fragColor = smoke;
    // Native near geometry retains its real depth test after the fade. Neither
    // pass writes depth; far fragments retain the existing native-sky test.
    gl_FragDepth = uNearPass ? nativeClip.z / nativeClip.w * 0.5 + 0.5 : 1.0;
}
