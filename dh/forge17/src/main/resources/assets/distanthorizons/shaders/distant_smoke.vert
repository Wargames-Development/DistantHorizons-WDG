#version 150
uniform bool uNearPass;
uniform mat4 uExtendedMvp;
uniform mat4 uNativeMvp;
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
    gl_Position = (uNearPass ? uNativeMvp : uExtendedMvp) * position;
    nativeClip = uNativeMvp * position;
    dhClip = uExtendedMvp * vec4(aPosition + uDhOriginOffset, 1.0);
    uv = aUv;
    lightUv = (aLight + vec2(8.0)) / 256.0;
    color = aColor;
}
