#define GL_SILENCE_DEPRECATION
#include <OpenGL/OpenGL.h>
#include <OpenGL/gl3.h>
#include <stdio.h>
#include <stdlib.h>
#include <math.h>
static GLuint program,textures[4];static int checks;
static GLuint compile(GLenum type,const char *path) {
    FILE *f=fopen(path,"rb");if(!f) { perror(path);exit(1); }
    fseek(f,0,SEEK_END);long n=ftell(f);rewind(f);char *source=calloc(n+1,1);
    if(fread(source,1,n,f)!=(size_t)n) exit(1);fclose(f);
    GLuint shader=glCreateShader(type);const char *p=source;glShaderSource(shader,1,&p,0);glCompileShader(shader);free(source);
    GLint ok;glGetShaderiv(shader,GL_COMPILE_STATUS,&ok);
    if(!ok) { char log[4096];glGetShaderInfoLog(shader,sizeof(log),0,log);fprintf(stderr,"%s\n",log);exit(1); }return shader;
}
static void uniform(const char *name,int value) { glUniform1i(glGetUniformLocation(program,name),value); }
static void matrix(const char *name,float z) {
    float m[16]={1,0,0,0,0,1,0,0,0,0,1,0,0,0,z,1};
    glUniformMatrix4fv(glGetUniformLocation(program,name),1,GL_FALSE,m);
}
static void tex(int unit,float r,float g,float b,float a) {
    float data[4]={r,g,b,a};glActiveTexture(GL_TEXTURE0+unit);glBindTexture(GL_TEXTURE_2D,textures[unit]);
    glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA32F,1,1,0,GL_RGBA,GL_FLOAT,data);
}
static void check(const char *name,int visible,float expectedDepth) {
    glClearColor(0,0,0,0);glClearDepth(.9);glDepthMask(GL_TRUE);glClear(GL_COLOR_BUFFER_BIT|GL_DEPTH_BUFFER_BIT);
    glDrawArrays(GL_TRIANGLES,0,6);float pixel[4],depth;glReadPixels(2,2,1,1,GL_RGBA,GL_FLOAT,pixel);
    glReadPixels(2,2,1,1,GL_DEPTH_COMPONENT,GL_FLOAT,&depth);GLenum error=glGetError();
    if(error || fabsf(pixel[0]-(visible?.5f:0))>.003 || fabsf(depth-(visible?expectedDepth:.9f))>.00001) {
        fprintf(stderr,"FAIL %s colour=%g depth=%g error=%x\n",name,pixel[0],depth,error);exit(1);
    }
    printf("PASS %s\n",name);checks++;
}
static void perspective_check(float distance,int reverse) {
    float near=fmaxf(.05f,distance-8),far=distance+8;
    float a=-(far+near)/(far-near),b=-2*far*near/(far-near);
    float inverse[16]={1,0,0,0,0,1,0,0,0,0,0,1/b,0,0,-1,a/b};
    glUniformMatrix4fv(glGetUniformLocation(program,"uInverseModelMvp"),1,GL_FALSE,inverse);
    float dn=16,df=16384,da=-(df+dn)/(df-dn),db=-2*df*dn/(df-dn);
    if(reverse) { da=-da;db=-db; }
    float dh[16]={1,0,0,0,0,1,0,0,0,0,da,-1,0,0,db,0};
    glUniformMatrix4fv(glGetUniformLocation(program,"uDhMvp"),1,GL_FALSE,dh);
    float na=-(256+.05f)/(256-.05f),nb=-2*256*.05f/(256-.05f);
    float native[16]={1,0,0,0,0,1,0,0,0,0,na,-1,0,0,nb,0};
    glUniformMatrix4fv(glGetUniformLocation(program,"uNativeMvp"),1,GL_FALSE,native);
    glUniform3f(glGetUniformLocation(program,"uDhOriginOffset"),0,0,0);
    uniform("uDhReverseDepth",reverse);uniform("uWriteDhDepth",1);uniform("uUseNativeDepth",0);
    tex(1,((-a*distance+b)/distance+1)*.5f,0,0,1);tex(2,reverse?0:1,0,0,1);
    float expected=((-da*distance+db)/distance+1)*.5f;
    check("physical distant model reconstructed from local capture",1,expected);
    float occluder=distance-1;tex(2,((-da*occluder+db)/occluder+1)*.5f,0,0,1);
    check("physical distant model hidden by nearer terrain",0,0);
}
int main(int argc,char **argv) {
    if(argc!=3) return 1;
    CGLPixelFormatAttribute attrs[]={kCGLPFAOpenGLProfile,(CGLPixelFormatAttribute)kCGLOGLPVersion_3_2_Core,kCGLPFAAllowOfflineRenderers,0};
    CGLPixelFormatObj format;GLint count;CGLContextObj context;
    if(CGLChoosePixelFormat(attrs,&format,&count) || !count || CGLCreateContext(format,0,&context) || CGLSetCurrentContext(context)) return 1;
    program=glCreateProgram();glAttachShader(program,compile(GL_VERTEX_SHADER,argv[1]));glAttachShader(program,compile(GL_FRAGMENT_SHADER,argv[2]));
    glBindAttribLocation(program,0,"aPosition");glBindFragDataLocation(program,0,"fragColor");glLinkProgram(program);
    GLint ok;glGetProgramiv(program,GL_LINK_STATUS,&ok);if(!ok) return 1;glUseProgram(program);
    glGenTextures(4,textures);
    for(int i=0;i<4;i++) { glActiveTexture(GL_TEXTURE0+i);glBindTexture(GL_TEXTURE_2D,textures[i]);
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_NEAREST);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_NEAREST); }
    uniform("uModelColour",0);uniform("uModelDepth",1);uniform("uDhDepth",2);uniform("uNativeDepth",3);
    matrix("uInverseModelMvp",0);matrix("uDhMvp",0);matrix("uNativeMvp",0);
    uniform("uWriteDhDepth",1);
    GLuint fbo,colour,depth;glGenFramebuffers(1,&fbo);glBindFramebuffer(GL_FRAMEBUFFER,fbo);
    glGenRenderbuffers(1,&colour);glBindRenderbuffer(GL_RENDERBUFFER,colour);glRenderbufferStorage(GL_RENDERBUFFER,GL_RGBA8,4,4);
    glFramebufferRenderbuffer(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_RENDERBUFFER,colour);
    glGenRenderbuffers(1,&depth);glBindRenderbuffer(GL_RENDERBUFFER,depth);glRenderbufferStorage(GL_RENDERBUFFER,GL_DEPTH_COMPONENT24,4,4);
    glFramebufferRenderbuffer(GL_FRAMEBUFFER,GL_DEPTH_ATTACHMENT,GL_RENDERBUFFER,depth);
    if(glCheckFramebufferStatus(GL_FRAMEBUFFER)!=GL_FRAMEBUFFER_COMPLETE) return 1;
    float vertices[]={-1,-1,1,-1,1,1,-1,-1,1,1,-1,1};GLuint vao,vbo;glGenVertexArrays(1,&vao);glBindVertexArray(vao);
    glGenBuffers(1,&vbo);glBindBuffer(GL_ARRAY_BUFFER,vbo);glBufferData(GL_ARRAY_BUFFER,sizeof(vertices),vertices,GL_STATIC_DRAW);
    glVertexAttribPointer(0,2,GL_FLOAT,GL_FALSE,0,0);glEnableVertexAttribArray(0);glViewport(0,0,4,4);
    glEnable(GL_DEPTH_TEST);glDepthFunc(GL_ALWAYS);
    tex(0,.5,.25,.125,1);tex(1,.5,0,0,1);tex(2,1,0,0,1);tex(3,1,0,0,1);
    check("visible model writes physical DH depth",1,.5);
    tex(2,.25,0,0,1);check("DH terrain occludes model",0,0);
    tex(2,.75,0,0,1);check("model in front of terrain",1,.5);
    uniform("uDhReverseDepth",1);check("reverse depth terrain occludes model",0,0);
    tex(2,.25,0,0,1);check("reverse depth foreground model",1,.5);
    uniform("uDhReverseDepth",0);tex(2,1,0,0,1);uniform("uUseNativeDepth",1);tex(3,.25,0,0,1);
    check("native scene occludes model",0,0);tex(3,.75,0,0,1);check("model in front of native scene",1,.5);
    uniform("uUseNativeDepth",0);tex(0,.5,.25,.125,0);check("transparent model holes stay empty",0,0);
    tex(0,.5,.25,.125,1);tex(1,1,0,0,1);check("capture sky cannot become model",0,0);
    uniform("uModelReverseDepth",1);tex(1,0,0,0,1);check("reverse capture sky remains empty",0,0);
    tex(1,.5,0,0,1);check("reverse capture supports physical surface",1,.5);uniform("uModelReverseDepth",0);
    matrix("uInverseModelMvp",.2);check("inverse capture projection reconstructs physical depth",1,.6);
    glUniform3f(glGetUniformLocation(program,"uDhOriginOffset"),0,0,-.4);check("DH origin conversion applied once",1,.4);
    uniform("uWriteDhDepth",0);check("native target uses native depth",1,.6);
    uniform("uWriteDhDepth",1);matrix("uDhMvp",-3);check("before DH near plane remains valid",1,0);
    matrix("uDhMvp",3);check("beyond DH far plane rejected",0,0);
    matrix("uDhMvp",0);matrix("uInverseModelMvp",0);glUniform3f(glGetUniformLocation(program,"uDhOriginOffset"),0,0,0);
    tex(2,.5,0,0,1);check("equal terrain depth does not flicker out",1,.5);
    for(int reverse=0;reverse<2;reverse++) for(int i=0;i<7;i++) {
        float distances[]={64,128,256,512,750,1024,4096};perspective_check(distances[i],reverse);
    }
    printf("PASS %d actor model GPU checks\n",checks);return 0;
}
