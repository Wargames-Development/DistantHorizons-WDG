#version 150
uniform bool uNearPass;
uniform bool uWholeModel;
uniform bool uShaderPass;
uniform mat4 uExtendedMvp;
uniform mat4 uNativeMvp;
uniform mat4 uNativeView;
out float viewDistance;
uniform vec3 uDhOriginOffset;
in vec3 aPosition;
in vec2 aUv;
in vec4 aColor;
in vec2 aLight;
out vec4 nativeClip;
out vec4 dhClip;
out vec2 uv;
out vec2 lightUv;
out vec4 color;
void main() {
    vec4 position = vec4(aPosition, 1.0);
    viewDistance=length((uNativeView*position).xyz);
    nativeClip = uNativeMvp * position;
    // All geometry uses the same native screen coordinates. The DH matrix has
    // a different camera origin and is used only to look up terrain depth.
    // Clamp far clip Z without changing XY/W, retaining the real near plane.
    gl_Position = (uNearPass && !uWholeModel) ? nativeClip
        : vec4(nativeClip.xy, min(nativeClip.z, nativeClip.w), nativeClip.w);
    dhClip = uExtendedMvp * vec4(aPosition + uDhOriginOffset, 1.0);
    // DH's terrain near plane is far outside the native camera near plane.
    // Keep native XY/W for the entire effect; fragment depth is supplied below.
    // Using dhClip here clips near smoke before the fragment shader can admit it.
    if(uShaderPass) gl_Position=vec4(nativeClip.xy,0.0,nativeClip.w);
    uv = aUv;
    lightUv = (aLight + vec2(8.0)) / 256.0;
    color = aColor;
}
