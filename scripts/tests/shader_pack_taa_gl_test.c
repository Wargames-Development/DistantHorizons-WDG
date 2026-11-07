#define GL_SILENCE_DEPRECATION
#include <OpenGL/OpenGL.h>
#include <OpenGL/gl3.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <math.h>
/* Executes the pack's unmodified taa.glsl in a small static-camera LOD fixture.
 * Pass an extracted shaders/lib/antialiasing/taa.glsl as the sole argument. */
static GLuint compile(GLenum type,const char *source) {
    GLuint shader=glCreateShader(type);glShaderSource(shader,1,&source,NULL);glCompileShader(shader);
    GLint ok;glGetShaderiv(shader,GL_COMPILE_STATUS,&ok);
    if(!ok) { char log[8192];glGetShaderInfoLog(shader,sizeof(log),NULL,log);fprintf(stderr,"%s\n",log);exit(1); }
    return shader;
}
static GLuint texture(int unit,const float *pixels) {
    GLuint t;glGenTextures(1,&t);glActiveTexture(GL_TEXTURE0+unit);glBindTexture(GL_TEXTURE_2D,t);
    glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_NEAREST);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,GL_CLAMP_TO_EDGE);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,GL_CLAMP_TO_EDGE);
    glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA32F,16,16,0,GL_RGBA,GL_FLOAT,pixels);return t;
}
static void fill(float *p,float v) { for(int i=0;i<16*16;i++) { for(int c=0;c<3;c++) p[i*4+c]=v;p[i*4+3]=1; } }
int main(int argc,char **argv) {
    if(argc!=2) return 2;
    FILE *f=fopen(argv[1],"rb");if(!f) return 2;fseek(f,0,SEEK_END);long n=ftell(f);rewind(f);
    char *pack=calloc(n+1,1);if(fread(pack,1,n,f)!=(size_t)n) return 2;fclose(f);
    const char *prefix="#version 150\n#define TAA_SMOOTHING 3\n#define TAA_MOVEMENT_IMPROVEMENT_FILTER 1\n#define DISTANT_HORIZONS\n#define ENTITY_TAA_NOISY_CLOUD_FIX\n#define texture2D texture\n#define texture2DLod textureLod\nuniform sampler2D colortex2,colortex3,colortex5,colortex6,depthtex0,depthtex1,dhDepthTex1;\nvec2 view=vec2(16.0);\nivec2 texelCoord=ivec2(gl_FragCoord.xy);\nvec2 texCoord=gl_FragCoord.xy/view;\nfloat near=0.1,far=256.0,renderDistance=16384.0;\nvec3 cameraPosition=vec3(0),previousCameraPosition=vec3(0);\nmat4 gbufferProjectionInverse=mat4(1),gbufferModelViewInverse=mat4(1),gbufferPreviousModelView=mat4(1),gbufferPreviousProjection=mat4(1),dhProjectionInverse=mat4(1),dhPreviousProjection=mat4(1);\nfloat pow2(float x) { return x*x; }\nfloat GetLinearDepth(float depth) { return (2.0*near)/(far+near-depth*(far-near)); }\n";
    const char *suffix="\nout vec4 result;void main() { vec3 color=texelFetch(colortex3,texelCoord,0).rgb;vec3 temp=vec3(0);DoTAA(color,temp,texelFetch(depthtex1,texelCoord,0).r);result=vec4(color,1); }\n";
    char *source=malloc(strlen(prefix)+n+strlen(suffix)+1);strcpy(source,prefix);strcat(source,pack);strcat(source,suffix);
    CGLPixelFormatAttribute attrs[]={kCGLPFAOpenGLProfile,(CGLPixelFormatAttribute)kCGLOGLPVersion_3_2_Core,kCGLPFAAllowOfflineRenderers,0};
    CGLPixelFormatObj pf;GLint count;CGLContextObj ctx;
    if(CGLChoosePixelFormat(attrs,&pf,&count)||!count||CGLCreateContext(pf,NULL,&ctx)||CGLSetCurrentContext(ctx)) return 2;
    GLuint program=glCreateProgram();glAttachShader(program,compile(GL_VERTEX_SHADER,"#version 150\nin vec2 position;void main(){gl_Position=vec4(position,0,1);}"));
    glAttachShader(program,compile(GL_FRAGMENT_SHADER,source));glBindAttribLocation(program,0,"position");glBindFragDataLocation(program,0,"result");glLinkProgram(program);
    GLint ok;glGetProgramiv(program,GL_LINK_STATUS,&ok);if(!ok) return 2;glUseProgram(program);
    const char *samplers[]={"colortex2","colortex3","colortex5","colortex6","depthtex0","depthtex1","dhDepthTex1"};
    float pixels[16*16*4];GLuint images[7];
    for(int i=0;i<7;i++) { fill(pixels,i==0||i==1?.8f:i==6?.75f:i==3?0:1);images[i]=texture(i,pixels);glUniform1i(glGetUniformLocation(program,samplers[i]),i); }
    GLuint fbo,out;glGenFramebuffers(1,&fbo);glBindFramebuffer(GL_FRAMEBUFFER,fbo);glGenRenderbuffers(1,&out);glBindRenderbuffer(GL_RENDERBUFFER,out);
    glRenderbufferStorage(GL_RENDERBUFFER,GL_RGBA32F,16,16);glFramebufferRenderbuffer(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_RENDERBUFFER,out);
    if(glCheckFramebufferStatus(GL_FRAMEBUFFER)!=GL_FRAMEBUFFER_COMPLETE) return 2;
    float vertices[]={-1,-1,1,-1,1,1,-1,-1,1,1,-1,1};GLuint vao,vbo;glGenVertexArrays(1,&vao);glBindVertexArray(vao);
    glGenBuffers(1,&vbo);glBindBuffer(GL_ARRAY_BUFFER,vbo);glBufferData(GL_ARRAY_BUFFER,sizeof(vertices),vertices,GL_STATIC_DRAW);glVertexAttribPointer(0,2,GL_FLOAT,GL_FALSE,0,0);glEnableVertexAttribArray(0);
    glViewport(0,0,16,16);
    for(int kind=0;kind<2;kind++) {
        float effect=kind==0?.1f:.45f,observed[2];
        fill(pixels,.8f);for(int c=0;c<3;c++) pixels[(8*16+8)*4+c]=effect;
        glActiveTexture(GL_TEXTURE1);glBindTexture(GL_TEXTURE_2D,images[1]);glTexSubImage2D(GL_TEXTURE_2D,0,0,0,16,16,GL_RGBA,GL_FLOAT,pixels);
        for(int marked=0;marked<2;marked++) {
            fill(pixels,0);pixels[(8*16+8)*4+1]=marked?254.0f/255.0f:0;
            glActiveTexture(GL_TEXTURE3);glBindTexture(GL_TEXTURE_2D,images[3]);glTexSubImage2D(GL_TEXTURE_2D,0,0,0,16,16,GL_RGBA,GL_FLOAT,pixels);
            glDrawArrays(GL_TRIANGLES,0,6);float result[4];glReadPixels(8,8,1,1,GL_RGBA,GL_FLOAT,result);observed[marked]=result[0];
        }
        if(glGetError() || fabsf(observed[1]-effect)>.0001f || fabsf(observed[0]-(effect*.1f+.8f*.9f))>.0001f) {
            fprintf(stderr,"FAIL pack TAA kind=%d unmarked=%f marked=%f source=%f\n",kind,observed[0],observed[1],effect);return 1;
        }
        printf("PASS installed pack TAA %s: missing marker retains 10%% contrast (%f); native marker preserves source (%f)\n",kind==0?"moving body":"fresh smoke",observed[0],observed[1]);
    }
    CGLSetCurrentContext(NULL);CGLDestroyContext(ctx);CGLDestroyPixelFormat(pf);free(source);free(pack);return 0;
}
