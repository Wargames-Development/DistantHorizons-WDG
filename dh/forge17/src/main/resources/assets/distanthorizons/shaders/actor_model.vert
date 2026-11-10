#version 150
in vec2 aPosition;
out vec2 screenUv;
void main() {
    screenUv=aPosition*0.5+0.5;
    gl_Position=vec4(aPosition,0.0,1.0);
}
