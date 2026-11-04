#define GL_SILENCE_DEPRECATION
#include <OpenGL/OpenGL.h>
#include <OpenGL/gl3.h>
#include <stdio.h>
#include <stdlib.h>
#include <math.h>
#include <stddef.h>

static char *read_source(const char *path) {
    FILE *f=fopen(path,"rb"); if(!f) { perror(path);exit(1); }
    fseek(f,0,SEEK_END);long n=ftell(f);rewind(f);char *s=calloc(n+1,1);
    if(fread(s,1,n,f)!=(size_t)n) exit(1);fclose(f);return s;
}
static GLuint compile(GLenum type,const char *path) {
    GLuint id=glCreateShader(type);char *s=read_source(path);const char *source=s;
    glShaderSource(id,1,&source,NULL);glCompileShader(id);free(s);
    GLint ok;glGetShaderiv(id,GL_COMPILE_STATUS,&ok);
    if(!ok) { char log[4096];glGetShaderInfoLog(id,sizeof(log),NULL,log);fprintf(stderr,"%s: %s\n",path,log);exit(1); }
    return id;
}
static GLuint program,dh_depth;
static float expected_visible=.5f,dh_projection_z=0;
static void texture(int unit,const float *rgba) {
    GLuint id;glGenTextures(1,&id);glActiveTexture(GL_TEXTURE0+unit);glBindTexture(GL_TEXTURE_2D,id);
    glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_NEAREST);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_NEAREST);
    glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA,1,1,0,GL_RGBA,GL_FLOAT,rgba);
}
static void check(const char *name,float native_z,float terrain_depth,float native_depth,int visible) {
    float identity[16]={1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1};
    identity[14]=dh_projection_z;
    float native[16]={1,0,0,0,0,1,0,0,0,0,1,0,0,0,native_z,1};
    glActiveTexture(GL_TEXTURE2);glBindTexture(GL_TEXTURE_2D,dh_depth);
    glTexImage2D(GL_TEXTURE_2D,0,GL_DEPTH_COMPONENT24,1,1,0,GL_DEPTH_COMPONENT,GL_FLOAT,&terrain_depth);
    glActiveTexture(GL_TEXTURE0);
    glUniformMatrix4fv(glGetUniformLocation(program,"uExtendedMvp"),1,GL_FALSE,identity);
    glUniformMatrix4fv(glGetUniformLocation(program,"uNativeMvp"),1,GL_FALSE,native);
    glUniform3f(glGetUniformLocation(program,"uDhOriginOffset"),0,0,0);
    glClearDepth(native_depth);glDepthMask(GL_TRUE);glClear(GL_COLOR_BUFFER_BIT|GL_DEPTH_BUFFER_BIT);glDepthMask(GL_FALSE);
    glDrawArrays(GL_TRIANGLES,0,6);
    float pixel[4],depth;glReadPixels(2,2,1,1,GL_RGBA,GL_FLOAT,pixel);glReadPixels(2,2,1,1,GL_DEPTH_COMPONENT,GL_FLOAT,&depth);
    GLenum error=glGetError();float expected=visible?expected_visible:0;
    if(error || fabsf(pixel[0]-expected)>.01f || fabsf(depth-native_depth)>.0001f) {
        fprintf(stderr,"FAIL %s red=%f depth=%f error=%x\n",name,pixel[0],depth,error);exit(1);
    }
    printf("PASS %s\n",name);
}
typedef struct { float xyz[3],uv[2];unsigned char color[4];int normal;short light[2]; } Vertex;
int main(int argc,char **argv) {
    if(argc!=3) { fprintf(stderr,"usage: distant_smoke_gl_test vertex-file fragment-file\n");return 1; }
    CGLPixelFormatAttribute attrs[]={kCGLPFAOpenGLProfile,(CGLPixelFormatAttribute)kCGLOGLPVersion_3_2_Core,kCGLPFAAllowOfflineRenderers,0};
    CGLPixelFormatObj format;GLint count;CGLContextObj context;
    CGLError error=CGLChoosePixelFormat(attrs,&format,&count);
    if(error || !count || CGLCreateContext(format,NULL,&context) || CGLSetCurrentContext(context)) {
        fprintf(stderr,"Cannot create Core OpenGL context\n");return 1;
    }
    program=glCreateProgram();glAttachShader(program,compile(GL_VERTEX_SHADER,argv[1]));glAttachShader(program,compile(GL_FRAGMENT_SHADER,argv[2]));
    glBindAttribLocation(program,0,"aPosition");glBindAttribLocation(program,1,"aUv");glBindAttribLocation(program,2,"aColor");glBindAttribLocation(program,3,"aLight");
    glBindFragDataLocation(program,0,"fragColor");glLinkProgram(program);GLint ok;glGetProgramiv(program,GL_LINK_STATUS,&ok);
    if(!ok) { char log[4096];glGetProgramInfoLog(program,sizeof(log),NULL,log);fprintf(stderr,"%s\n",log);return 1; }
    glUseProgram(program);glUniform1i(glGetUniformLocation(program,"uSmoke"),0);glUniform1i(glGetUniformLocation(program,"uLightmap"),1);
    glUniform1i(glGetUniformLocation(program,"uDhDepth"),2);glUniform1i(glGetUniformLocation(program,"uUseLightmap"),1);
    glUniform1f(glGetUniformLocation(program,"uAlphaCutoff"),.0039f);
    float white[4]={1,1,1,1},smoke[4]={1,1,1,.5};texture(0,smoke);texture(1,white);
    glGenTextures(1,&dh_depth);glActiveTexture(GL_TEXTURE2);glBindTexture(GL_TEXTURE_2D,dh_depth);
    glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_NEAREST);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_NEAREST);
    GLuint fbo,color,depth;glGenFramebuffers(1,&fbo);glBindFramebuffer(GL_FRAMEBUFFER,fbo);
    glGenRenderbuffers(1,&color);glBindRenderbuffer(GL_RENDERBUFFER,color);glRenderbufferStorage(GL_RENDERBUFFER,GL_RGBA8,4,4);
    glFramebufferRenderbuffer(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_RENDERBUFFER,color);
    glGenRenderbuffers(1,&depth);glBindRenderbuffer(GL_RENDERBUFFER,depth);glRenderbufferStorage(GL_RENDERBUFFER,GL_DEPTH_COMPONENT24,4,4);
    glFramebufferRenderbuffer(GL_FRAMEBUFFER,GL_DEPTH_ATTACHMENT,GL_RENDERBUFFER,depth);
    if(glCheckFramebufferStatus(GL_FRAMEBUFFER)!=GL_FRAMEBUFFER_COMPLETE) return 1;
    Vertex vertices[6];float xy[6][2]={{-1,-1},{1,-1},{1,1},{-1,-1},{1,1},{-1,1}};
    if(sizeof(Vertex)!=32) { fprintf(stderr,"Unexpected vertex layout\n");return 1; }
    for(int i=0;i<6;i++) {
        Vertex *v=&vertices[i];v->xyz[0]=xy[i][0];v->xyz[1]=xy[i][1];v->xyz[2]=0;v->uv[0]=v->uv[1]=.5;
        for(int c=0;c<4;c++) v->color[c]=255;v->normal=0;v->light[0]=v->light[1]=240;
    }
    GLuint vao,vbo;glGenVertexArrays(1,&vao);glBindVertexArray(vao);glGenBuffers(1,&vbo);glBindBuffer(GL_ARRAY_BUFFER,vbo);
    glBufferData(GL_ARRAY_BUFFER,sizeof(vertices),vertices,GL_STATIC_DRAW);
    glVertexAttribPointer(0,3,GL_FLOAT,GL_FALSE,32,(void*)0);glVertexAttribPointer(1,2,GL_FLOAT,GL_FALSE,32,(void*)12);
    glVertexAttribPointer(2,4,GL_UNSIGNED_BYTE,GL_TRUE,32,(void*)20);glVertexAttribPointer(3,2,GL_SHORT,GL_FALSE,32,(void*)28);
    for(int i=0;i<4;i++) glEnableVertexAttribArray(i);
    glViewport(0,0,4,4);glClearColor(0,0,0,0);glEnable(GL_BLEND);glBlendFunc(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA);glEnable(GL_DEPTH_TEST);glDepthFunc(GL_LEQUAL);
    check("beyond native far plane remains visible",2,1,1,1);
    check("near fragments remain owned by original renderer",.5,1,1,0);
    check("exact native far-plane boundary does not double draw",1,1,1,0);
    check("DH terrain hides distant smoke",2,.25,1,0);
    check("smoke in front of DH terrain remains visible",2,.75,1,1);
    check("native terrain hides distant smoke",2,1,.5,0);
    check("native scene depth remains unchanged",2,1,.25,0);
    float gray[4]={.5,.5,.5,1};texture(1,gray);expected_visible=.25;
    check("original lightmap brightness preserved",2,1,1,1);
    glUniform1i(glGetUniformLocation(program,"uUseLightmap"),0);expected_visible=.5;
    check("unlit source particles keep their colour",2,1,1,1);
    glUniform1f(glGetUniformLocation(program,"uAlphaCutoff"),.75);
    check("original source alpha threshold preserved",2,1,1,0);
    glUniform1f(glGetUniformLocation(program,"uAlphaCutoff"),.0039f);
    glUniform1i(glGetUniformLocation(program,"uDhReverseDepth"),1);
    check("reverse-depth sky does not hide smoke",2,0,1,1);
    check("reverse-depth farther terrain does not hide smoke",2,.25,1,1);
    check("reverse-depth nearer terrain hides smoke",2,.75,1,0);
    check("reverse-depth native terrain still hides smoke",2,0,.5,0);
    glUniform1i(glGetUniformLocation(program,"uDhReverseDepth"),0);
    check("forward-depth sky remains correct after changing mode",2,1,1,1);
    glUniform1i(glGetUniformLocation(program,"uNearPass"),1);
    check("post-fade near smoke is visible",.5,1,1,1);
    check("post-fade near smoke uses its real scene depth",.5,1,.9,1);
    check("native foreground terrain still hides post-fade near smoke",.5,1,.5,0);
    check("DH foreground terrain still hides post-fade near smoke",.5,.25,1,0);
    check("near pass cannot double draw far smoke",2,1,1,0);
    check("near boundary is owned exactly once by the near pass",1,1,1,1);
    dh_projection_z=3;
    check("close native smoke survives before the DH near plane",.5,1,1,1);
    check("close native smoke still respects native foreground depth",.5,1,.5,0);
    dh_projection_z=0;
    glUniform1i(glGetUniformLocation(program,"uDhReverseDepth"),1);
    check("near smoke respects reversed-depth sky",.5,0,1,1);
    check("near smoke respects reversed-depth foreground LOD",.5,.75,1,0);
    glUniform1i(glGetUniformLocation(program,"uNearPass"),0);
    glUniform1i(glGetUniformLocation(program,"uDhReverseDepth"),0);
    check("far ownership remains intact after near pass",2,1,1,1);
    printf("Shader compile/link and 26 Core-GL VBO pixel/depth checks passed (%s; %s).\n",glGetString(GL_RENDERER),glGetString(GL_VERSION));
    CGLSetCurrentContext(NULL);CGLDestroyContext(context);CGLDestroyPixelFormat(format);return 0;
}
