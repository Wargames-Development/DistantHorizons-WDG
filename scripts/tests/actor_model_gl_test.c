#define GL_SILENCE_DEPRECATION
#include <OpenGL/OpenGL.h>
#include <OpenGL/gl3.h>
#include <stdio.h>
#include <stdlib.h>
#include <math.h>
static GLuint program,textures[5];static int checks;
static void native_scene_identity_check(void) {
    GLuint fb[3],depth[2],colour;glGenFramebuffers(3,fb);glGenTextures(2,depth);glGenTextures(1,&colour);
    glBindTexture(GL_TEXTURE_2D,colour);glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA8,4,4,0,GL_RGBA,GL_UNSIGNED_BYTE,0);
    for(int i=0;i<2;i++) {
        glBindTexture(GL_TEXTURE_2D,depth[i]);glTexImage2D(GL_TEXTURE_2D,0,GL_DEPTH_COMPONENT24,4,4,0,GL_DEPTH_COMPONENT,GL_UNSIGNED_INT,0);
    }
    GLint types[3],objects[3];
    for(int i=0;i<3;i++) {
        glBindFramebuffer(GL_FRAMEBUFFER,fb[i]);
        glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,colour,0);
        glFramebufferTexture2D(GL_FRAMEBUFFER,GL_DEPTH_ATTACHMENT,GL_TEXTURE_2D,depth[i==2?1:0],0);
        glDrawBuffer(GL_COLOR_ATTACHMENT0);
        if(glCheckFramebufferStatus(GL_FRAMEBUFFER)!=GL_FRAMEBUFFER_COMPLETE) { fprintf(stderr,"FAIL actor ownership framebuffer\n");exit(1); }
        glGetFramebufferAttachmentParameteriv(GL_DRAW_FRAMEBUFFER,GL_DEPTH_ATTACHMENT,GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE,&types[i]);
        glGetFramebufferAttachmentParameteriv(GL_DRAW_FRAMEBUFFER,GL_DEPTH_ATTACHMENT,GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME,&objects[i]);
    }
    // Terrain and entity shader phases have different FBO identities but one native depth image.
    if(fb[0]==fb[1] || types[0]!=GL_TEXTURE || types[0]!=types[1] || objects[0]!=objects[1] || objects[0]==objects[2] || glGetError()) {
        fprintf(stderr,"FAIL shader-phase native ownership / private-camera separation\n");exit(1);
    }
    checks++;printf("PASS different shader FBOs share native depth; private camera remains distinct\n");
    glBindFramebuffer(GL_FRAMEBUFFER,0);glDeleteFramebuffers(3,fb);glDeleteTextures(2,depth);glDeleteTextures(1,&colour);
}
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
static void scene_check(int reverse) {
    uniform("uDhReverseDepth",reverse);uniform("uWriteDhDepth",1);uniform("uUseNativeDepth",0);uniform("uModelReverseDepth",0);
    matrix("uInverseModelMvp",0);matrix("uNativeMvp",0);
    float dh[16]={.5f,0,0,0,0,.75f,0,0,0,0,reverse?-1:1,0,0,0,0,1};
    glUniformMatrix4fv(glGetUniformLocation(program,"uDhMvp"),1,GL_FALSE,dh);
    glUniform3f(glGetUniformLocation(program,"uDhOriginOffset"),0,0,0);tex(2,reverse?0:1,0,0,1);
    glClearColor(0,0,0,0);glClearDepth(reverse?0:1);glClear(GL_COLOR_BUFFER_BIT|GL_DEPTH_BUFFER_BIT);
    glDepthFunc(reverse?GL_GEQUAL:GL_LEQUAL);
    tex(0,.1,.1,.1,1);tex(1,.6,0,0,1);glDrawArrays(GL_TRIANGLES,0,6);
    tex(0,.5,.25,.125,1);tex(1,.4,0,0,1);glDrawArrays(GL_TRIANGLES,0,6);
    float pixel[4],d;glReadPixels(2,2,1,1,GL_RGBA,GL_FLOAT,pixel);glReadPixels(2,2,1,1,GL_DEPTH_COMPONENT,GL_FLOAT,&d);
    if(fabsf(pixel[0]-.5f)>.003 || fabsf(d-(reverse?.6f:.4f))>.00001) { fprintf(stderr,"FAIL inter-actor depth\n");exit(1); }
    checks++;
    GLuint source[2];glGenTextures(2,source);
    for(int i=0;i<2;i++) {
        glActiveTexture(GL_TEXTURE0+i);glBindTexture(GL_TEXTURE_2D,source[i]);
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_NEAREST);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_NEAREST);
        glCopyTexImage2D(GL_TEXTURE_2D,0,i==0?GL_RGBA8:GL_DEPTH_COMPONENT24,0,0,4,4,0);
    }
    // The second pass reconstructs from DH's scene coordinates, then writes native depth.
    matrix("uInverseModelMvp",0);uniform("uSceneDepth",1);
    glUniformMatrix4fv(glGetUniformLocation(program,"uDhFromNativeClip"),1,GL_FALSE,dh);
    tex(3,1,0,0,1);uniform("uUseNativeDepth",1);
    uniform("uModelReverseDepth",reverse);uniform("uWriteDhDepth",0);glDepthFunc(GL_ALWAYS);
    check("combined actor scene retains physical native depth",1,.4f);
    glReadPixels(3,2,1,1,GL_RGBA,GL_FLOAT,pixel);
    if(fabsf(pixel[0]-.5f)>.003f) { fprintf(stderr,"FAIL native/DH FOV mismatch reconstruction\n");exit(1); }
    checks++;
    glDeleteTextures(2,source);uniform("uModelReverseDepth",0);uniform("uSceneDepth",0);
}
static void different_camera_check(int reverse) {
    matrix("uInverseModelMvp",0);matrix("uNativeMvp",0);uniform("uSceneDepth",1);
    // Independent oracle point at the centre sample: native=(.25,.25,.2,1).
    // DH has a different perspective denominator, as camera-origin offsets produce.
    float h[16]={1,0,0,.25f,0,1,0,0,0,0,reverse?-.8f:.8f,.5f,0,0,reverse?-.1f:.1f,1};
    glUniformMatrix4fv(glGetUniformLocation(program,"uDhMvp"),1,GL_FALSE,h);
    glUniformMatrix4fv(glGetUniformLocation(program,"uDhFromNativeClip"),1,GL_FALSE,h);
    float depth=(.26f/1.1625f+1)*.5f;if(reverse) depth=1-depth;
    tex(0,.5,.25,.125,1);tex(1,depth,0,0,1);tex(2,reverse?0:1,0,0,1);tex(3,1,0,0,1);
    uniform("uDhReverseDepth",reverse);uniform("uModelReverseDepth",reverse);uniform("uWriteDhDepth",0);uniform("uUseNativeDepth",1);
    check("different native/DH camera bases reconstruct physical model depth",1,.6f);
    tex(3,.55f,0,0,1);check("native occlusion remains valid with different camera bases",0,0);
    tex(3,1,0,0,1);tex(2,reverse?.9f:.1f,0,0,1);check("DH occlusion remains valid with different camera bases",0,0);
    uniform("uSceneDepth",0);uniform("uModelReverseDepth",0);
}
static void partial_cover_check(void) {
    matrix("uInverseModelMvp",0);matrix("uNativeMvp",0);matrix("uDhMvp",0);
    uniform("uDhReverseDepth",0);uniform("uWriteDhDepth",1);uniform("uModelReverseDepth",0);uniform("uUseNativeDepth",0);
    tex(0,.5,.25,.125,1);tex(1,.5,0,0,1);
    float terrain[64];for(int i=0;i<16;i++) { terrain[i*4]=i%4<2?.25f:.75f;terrain[i*4+1]=terrain[i*4+2]=0;terrain[i*4+3]=1; }
    glActiveTexture(GL_TEXTURE2);glBindTexture(GL_TEXTURE_2D,textures[2]);glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA32F,4,4,0,GL_RGBA,GL_FLOAT,terrain);
    glClearColor(0,0,0,0);glClearDepth(.9);glClear(GL_COLOR_BUFFER_BIT|GL_DEPTH_BUFFER_BIT);glDrawArrays(GL_TRIANGLES,0,6);
    float left[4],right[4];glReadPixels(0,2,1,1,GL_RGBA,GL_FLOAT,left);glReadPixels(3,2,1,1,GL_RGBA,GL_FLOAT,right);
    if(left[0]>.001f || fabsf(right[0]-.5f)>.003f) { fprintf(stderr,"FAIL partial model cover\n");exit(1); }
    checks++;printf("PASS terrain hides only covered model pixels\n");
}
static void cloud_check(void) {
    matrix("uInverseModelMvp",0);matrix("uNativeMvp",0);matrix("uDhMvp",0);matrix("uNativeView",-100);
    uniform("uDhReverseDepth",0);uniform("uWriteDhDepth",0);uniform("uModelReverseDepth",0);uniform("uUseNativeDepth",0);
    tex(0,.5,.25,.125,1);tex(1,.5,0,0,1);tex(2,1,0,0,1);
    glUniform1f(glGetUniformLocation(program,"uCloudRenderDistance"),1024);uniform("uUseCloudDepth",1);
    tex(4,0,0,0,.2);check("dense shader clouds occlude actor pixels",0,0);
    tex(4,0,0,0,.5);check("actors before shader clouds remain visible",1,.5);
    tex(4,0,0,0,0);check("empty cloud distance does not occlude actors",1,.5);
    tex(4,0,0,0,1);check("cloud horizon does not occlude actors",1,.5);
    tex(4,0,0,0,.2);uniform("uUseCloudDepth",0);check("cloud-disabled variant ignores retained mask",1,.5);
    uniform("uUseCloudDepth",1);
    float cloud[64];for(int i=0;i<16;i++) { cloud[i*4]=cloud[i*4+1]=cloud[i*4+2]=0;cloud[i*4+3]=i%4<2?.2f:.5f; }
    glActiveTexture(GL_TEXTURE4);glBindTexture(GL_TEXTURE_2D,textures[4]);glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA32F,4,4,0,GL_RGBA,GL_FLOAT,cloud);
    glClearColor(0,0,0,0);glClearDepth(.9);glClear(GL_COLOR_BUFFER_BIT|GL_DEPTH_BUFFER_BIT);glDrawArrays(GL_TRIANGLES,0,6);
    float left[4],right[4];glReadPixels(0,2,1,1,GL_RGBA,GL_FLOAT,left);glReadPixels(3,2,1,1,GL_RGBA,GL_FLOAT,right);
    if(left[0]>.001f || fabsf(right[0]-.5f)>.003f) { fprintf(stderr,"FAIL partial cloud cover\n");exit(1); }
    checks++;printf("PASS clouds hide only covered actor pixels\n");uniform("uUseCloudDepth",0);
}
static double camera_depth(double d,double origin,double a,double b,double start) {
    double eye=origin-d,base=.5*((a*eye+b)/-eye+1);
    if(d<=start)return base;
    eye=origin-start;double transition=.5*((a*eye+b)/-eye+1);
    double span=log(4096/start),shoulder=.05/(1/start-.80/(start*span));
    double fraction=.05*(1-exp(-(fmin(d,4096)-start)/shoulder))+.8*log(fmin(d,4096)/start)/span;
    if(d>4096)fraction=.85+.15*(1-4096/d);
    return transition+(1-transition)*fraction;
}
static void auxiliary_check(float d,int heat) {
    float near=fmaxf(.05f,d-8),far=d+8,a=-(far+near)/(far-near),b=-2*far*near/(far-near);
    float inverse[16]={1,0,0,0,0,1,0,0,0,0,0,1/b,0,0,-1,a/b};
    glUniformMatrix4fv(glGetUniformLocation(program,"uInverseModelMvp"),1,GL_FALSE,inverse);
    matrix("uNativeView",0);uniform("uAuxiliary",1);uniform("uAuxiliaryHeat",heat);
    float wa=-(256+.05f)/(256-.05f),wb=-2*256*.05f/(256-.05f);
    glUniform4f(glGetUniformLocation(program,"uAuxiliaryDepth"),wa,wb,0,32);
    tex(1,((-a*d+b)/d+1)*.5f,0,0,1);tex(0,.5,.25,.125,heat?.5:1);
    glDepthFunc(GL_ALWAYS);
    check(heat?"isolated heat preserves capture alpha and physical depth":"isolated colour matches PiP terrain depth",1,camera_depth(d,0,wa,wb,32));
    // The private scene, rather than the primary framebuffer, owns actual occlusion.
    glDepthFunc(GL_LESS);check("nearer private-camera cover blocks actor",0,0);glDepthFunc(GL_ALWAYS);
    uniform("uAuxiliary",0);uniform("uAuxiliaryHeat",0);tex(0,.5,.25,.125,1);
}
static void marker_check(void) {
    uniform("uMarkerOverlay",1);glUniform1f(glGetUniformLocation(program,"uMarkerDepth"),.5f);glUniform1f(glGetUniformLocation(program,"uMarkerTerrainDepth"),.6f);
    uniform("uUseNativeDepth",1);uniform("uSceneDepth",0);uniform("uWriteDhDepth",0);uniform("uDhReverseDepth",0);
    matrix("uInverseModelMvp",0);matrix("uNativeMvp",0);matrix("uDhMvp",0);
    tex(0,.5,.25,.125,1);tex(1,1,0,0,1);tex(2,1,0,0,1);tex(3,.55,0,0,1);
    check("BVR glyph is in front of its target model",1,.5);
    tex(3,.1,0,0,1);check("near cockpit covers BVR glyph",0,0);
    tex(3,1,0,0,1);tex(2,.55,0,0,1);check("near-target ground cannot clip offset BVR text",1,.5);
    tex(2,.3,0,0,1);check("terrain in front of target envelope covers BVR glyph",0,0);
    uniform("uDhReverseDepth",1);tex(2,.7,0,0,1);check("reverse DH terrain covers BVR glyph",0,0);
    uniform("uMarkerOverlay",0);uniform("uDhReverseDepth",0);
}
static void native_depth_copy_check(void) {
    GLint previous;glGetIntegerv(GL_FRAMEBUFFER_BINDING,&previous);
    GLenum formats[]={GL_DEPTH_COMPONENT24,GL_DEPTH24_STENCIL8,GL_DEPTH_COMPONENT32F};
    for(int n=0;n<3;n++) {
        GLuint fbo,source,copy;glGenFramebuffers(1,&fbo);glGenTextures(1,&source);glGenTextures(1,&copy);
        glActiveTexture(GL_TEXTURE0);glBindTexture(GL_TEXTURE_2D,source);
        glTexImage2D(GL_TEXTURE_2D,0,formats[n],4,4,0,n==1?GL_DEPTH_STENCIL:GL_DEPTH_COMPONENT,n==1?GL_UNSIGNED_INT_24_8:GL_FLOAT,0);
        glBindFramebuffer(GL_FRAMEBUFFER,fbo);glFramebufferTexture2D(GL_FRAMEBUFFER,GL_DEPTH_ATTACHMENT,GL_TEXTURE_2D,source,0);
        glDrawBuffer(GL_NONE);glReadBuffer(GL_NONE);
        if(glCheckFramebufferStatus(GL_FRAMEBUFFER)!=GL_FRAMEBUFFER_COMPLETE) {fprintf(stderr,"FAIL native source format %x\n",formats[n]);exit(1);}checks++;
        glDepthMask(GL_TRUE);glClearDepth(.25);glClear(GL_DEPTH_BUFFER_BIT);
        glBindTexture(GL_TEXTURE_2D,copy);glCopyTexImage2D(GL_TEXTURE_2D,0,GL_DEPTH_COMPONENT32F,0,0,4,4,0);
        float values[16];glGetTexImage(GL_TEXTURE_2D,0,GL_DEPTH_COMPONENT,GL_FLOAT,values);
        if(fabsf(values[0]-.25f)>.000001f || glGetError()) {fprintf(stderr,"FAIL immutable native depth copy %x\n",formats[n]);exit(1);}checks++;
        glBindFramebuffer(GL_FRAMEBUFFER,previous);glDeleteFramebuffers(1,&fbo);glDeleteTextures(1,&source);glDeleteTextures(1,&copy);
    }
    printf("PASS native depth snapshots for depth24, packed stencil and depth32f\n");
}
static void sampler_state_check(void) {
    uniform("uMarkerOverlay",0);uniform("uDhReverseDepth",0);uniform("uWriteDhDepth",0);uniform("uUseNativeDepth",1);
    matrix("uInverseModelMvp",0);matrix("uNativeMvp",0);matrix("uDhMvp",0);
    tex(0,.5,.25,.125,1);tex(1,.5,0,0,1);tex(2,1,0,0,1);
    GLuint native,sampler;glGenTextures(1,&native);glActiveTexture(GL_TEXTURE3);glBindTexture(GL_TEXTURE_2D,native);
    float depth=.25;glTexImage2D(GL_TEXTURE_2D,0,GL_DEPTH_COMPONENT32F,1,1,0,GL_DEPTH_COMPONENT,GL_FLOAT,&depth);
    glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_NEAREST);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_NEAREST);
    glGenSamplers(1,&sampler);glSamplerParameteri(sampler,GL_TEXTURE_COMPARE_MODE,GL_COMPARE_REF_TO_TEXTURE);glSamplerParameteri(sampler,GL_TEXTURE_COMPARE_FUNC,GL_ALWAYS);
    glBindSampler(3,sampler);GLint original;glGetIntegerv(GL_SAMPLER_BINDING,&original);
    glBindSampler(3,0);check("native cover remains correct after clearing inherited comparison sampler",0,0);
    glBindSampler(3,original);GLint restored;glGetIntegerv(GL_SAMPLER_BINDING,&restored);
    if(restored!=(GLint)sampler){fprintf(stderr,"FAIL caller sampler restoration\n");exit(1);}checks++;
    glBindSampler(3,0);glDeleteSamplers(1,&sampler);glDeleteTextures(1,&native);
}
static void jitter_alignment_check(void) {
    uniform("uAuxiliary",0);uniform("uMarkerOverlay",0);uniform("uOpaqueDepthOnly",0);uniform("uSceneDepth",0);
    uniform("uOccludeWorld",1);uniform("uDhReverseDepth",0);uniform("uModelReverseDepth",0);uniform("uWriteDhDepth",1);
    tex(0,.5,.25,.125,1);tex(1,.5,0,0,1);tex(2,1,0,0,1);
    float inverse[]={1,0,0,0,0,1,0,0,0,0,1,0,-.5,0,0,1};
    float jittered[]={1,0,0,0,0,1,0,0,0,0,1,0,.5,0,0,1};
    glUniformMatrix4fv(glGetUniformLocation(program,"uInverseModelMvp"),1,GL_FALSE,inverse);
    matrix("uDhMvp",0);matrix("uNativeMvp",0);glUniform3f(glGetUniformLocation(program,"uDhOriginOffset"),0,0,0);
    float edge[]={.25,0,0,1,.75,0,0,1};
    glActiveTexture(GL_TEXTURE3);glBindTexture(GL_TEXTURE_2D,textures[3]);glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA32F,2,1,0,GL_RGBA,GL_FLOAT,edge);
    uniform("uUseNativeDepth",1);glDepthFunc(GL_ALWAYS);
    check("unmatched native jitter samples the wrong foreground pixel",0,0);
    glUniformMatrix4fv(glGetUniformLocation(program,"uNativeMvp"),1,GL_FALSE,jittered);
    check("matched native jitter keeps moving actor visible beside foreground",1,.5);
    uniform("uUseNativeDepth",0);
    glActiveTexture(GL_TEXTURE2);glBindTexture(GL_TEXTURE_2D,textures[2]);glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA32F,2,1,0,GL_RGBA,GL_FLOAT,edge);
    check("unmatched DH jitter samples the wrong terrain pixel",0,0);
    glUniformMatrix4fv(glGetUniformLocation(program,"uDhMvp"),1,GL_FALSE,jittered);
    check("matched DH jitter keeps moving actor visible beside terrain",1,.5);
}
static void stationary_actor_jitter_check(void) {
    uniform("uAuxiliary",0);uniform("uMarkerOverlay",0);uniform("uOpaqueDepthOnly",0);
    uniform("uOccludeWorld",1);uniform("uDhReverseDepth",0);uniform("uModelReverseDepth",0);
    uniform("uWriteDhDepth",1);uniform("uUseNativeDepth",1);uniform("uUseCloudDepth",0);
    matrix("uInverseModelMvp",0);glUniform3f(glGetUniformLocation(program,"uDhOriginOffset"),0,0,0);
    tex(1,.5,0,0,1);tex(2,1,0,0,1);tex(3,1,0,0,1);
    float colour[64]={0},reference[64],observed[64];
    for(int i=0;i<16;i++) if(i%4==1 || i%4==2) { colour[i*4]=.5;colour[i*4+3]=1; }
    glActiveTexture(GL_TEXTURE0);glBindTexture(GL_TEXTURE_2D,textures[0]);
    glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA32F,4,4,0,GL_RGBA,GL_FLOAT,colour);
    const float x[]={.125,-.125,.625,.375,-.625,-.875,.375,.875};
    const float y[]={-.375,.375,.125,-.625,.625,-.125,-.875,.875};
    for(int scene=0;scene<2;scene++) for(int frame=0;frame<8;frame++) {
        float jitter[]={1,0,0,0,0,1,0,0,0,0,1,0,x[frame]*.125f/4,y[frame]*.125f/4,0,1};
        glUniformMatrix4fv(glGetUniformLocation(program,"uNativeMvp"),1,GL_FALSE,jitter);
        glUniformMatrix4fv(glGetUniformLocation(program,"uDhMvp"),1,GL_FALSE,jitter);
        glUniformMatrix4fv(glGetUniformLocation(program,"uDhFromNativeClip"),1,GL_FALSE,jitter);
        uniform("uSceneDepth",scene);
        glClearColor(0,0,0,0);glClearDepth(1);glClear(GL_COLOR_BUFFER_BIT|GL_DEPTH_BUFFER_BIT);
        glDepthFunc(GL_ALWAYS);glDrawArrays(GL_TRIANGLES,0,6);glReadPixels(0,0,4,4,GL_RGBA,GL_FLOAT,observed);
        if(frame==0 && scene==0) for(int i=0;i<64;i++) reference[i]=observed[i];
        for(int i=0;i<64;i++) if(fabsf(observed[i]-reference[i])>.00001) {
            fprintf(stderr,"FAIL stationary actor raster shifts in jitter frame %d scene %d\n",frame,scene);exit(1);
        }
        if(glGetError() || fabsf(observed[4]-.5f)>.003f || observed[0]>.003f) exit(1);
        checks++;
    }
    printf("PASS stable actor colour across eight shader jitter phases and both scene stages\n");
    uniform("uSceneDepth",0);
}
static void beam_segment_check(void) {
    uniform("uAuxiliary",0);uniform("uMarkerOverlay",0);uniform("uOccludeWorld",1);uniform("uSceneDepth",0);
    uniform("uDhReverseDepth",0);uniform("uModelReverseDepth",0);uniform("uUseNativeDepth",0);uniform("uWriteDhDepth",0);
    matrix("uInverseModelMvp",0);matrix("uDhMvp",0);matrix("uNativeMvp",0);glUniform3f(glGetUniformLocation(program,"uDhOriginOffset"),0,0,0);
    tex(0,.5,.25,.125,1);tex(2,.5,0,0,1);
    float depths[]={.6,0,0,1,.6,0,0,1,.4,0,0,1,.4,0,0,1};
    glActiveTexture(GL_TEXTURE1);glBindTexture(GL_TEXTURE_2D,textures[1]);glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA32F,1,4,0,GL_RGBA,GL_FLOAT,depths);
    glClearColor(0,0,0,0);glClear(GL_COLOR_BUFFER_BIT|GL_DEPTH_BUFFER_BIT);glDepthFunc(GL_ALWAYS);glDrawArrays(GL_TRIANGLES,0,6);
    float lower[4],upper[4];glReadPixels(2,0,1,1,GL_RGBA,GL_FLOAT,lower);glReadPixels(2,3,1,1,GL_RGBA,GL_FLOAT,upper);
    if(lower[0]>.003 || fabsf(upper[0]-.5f)>.003 || glGetError()) { fprintf(stderr,"FAIL per-fragment beam terrain cover\n");exit(1); }
    checks++;printf("PASS terrain hides lower beam while upper segment remains visible\n");
}
static void actor_surface_check(void) {
    GLuint surface;glGenRenderbuffers(1,&surface);glBindRenderbuffer(GL_RENDERBUFFER,surface);
    glRenderbufferStorage(GL_RENDERBUFFER,GL_RGBA8,4,4);
    glFramebufferRenderbuffer(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT2,GL_RENDERBUFFER,surface);
    GLenum outputs[]={GL_COLOR_ATTACHMENT0,GL_NONE,GL_COLOR_ATTACHMENT2};glDrawBuffers(3,outputs);
    uniform("uAuxiliary",0);uniform("uMarkerOverlay",0);uniform("uOccludeWorld",1);
    uniform("uSceneDepth",0);uniform("uUseCloudDepth",0);uniform("uUseNativeDepth",0);uniform("uDhReverseDepth",0);uniform("uModelReverseDepth",0);
    matrix("uInverseModelMvp",0);matrix("uNativeMvp",0);matrix("uDhMvp",0);
    glUniform3f(glGetUniformLocation(program,"uDhOriginOffset"),0,0,0);
    tex(0,.5,.25,.125,1);tex(1,.5,0,0,1);tex(2,1,0,0,1);
    float inherited[]={1,0,0,1};glClearBufferfv(GL_COLOR,2,inherited);
    glEnable(GL_BLEND);glBlendFunc(GL_ONE,GL_ONE_MINUS_SRC_ALPHA);glDisablei(GL_BLEND,2);
    glClearDepth(1);glClear(GL_DEPTH_BUFFER_BIT);glDepthFunc(GL_ALWAYS);glDrawArrays(GL_TRIANGLES,0,6);
    glReadBuffer(GL_COLOR_ATTACHMENT2);float pixel[4];glReadPixels(2,2,1,1,GL_RGBA,GL_FLOAT,pixel);
    if(fabsf(pixel[0])>.003 || fabsf(pixel[1]-1)>.003 || fabsf(pixel[2])>.003 || fabsf(pixel[3])>.003 || glGetError()) {
        fprintf(stderr,"FAIL actor inherited terrain reflection %.3f %.3f %.3f %.3f\n",pixel[0],pixel[1],pixel[2],pixel[3]);exit(1);
    }
    checks++;printf("PASS actor replaces inherited glossy terrain normal and fresnel\n");
    glReadBuffer(GL_COLOR_ATTACHMENT0);glDrawBuffer(GL_COLOR_ATTACHMENT0);glDisable(GL_BLEND);
    glFramebufferRenderbuffer(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT2,GL_RENDERBUFFER,0);glDeleteRenderbuffers(1,&surface);
}
int main(int argc,char **argv) {
    if(argc!=3) return 1;
    CGLPixelFormatAttribute attrs[]={kCGLPFAOpenGLProfile,(CGLPixelFormatAttribute)kCGLOGLPVersion_3_2_Core,kCGLPFAAllowOfflineRenderers,0};
    CGLPixelFormatObj format;GLint count;CGLContextObj context;
    if(CGLChoosePixelFormat(attrs,&format,&count) || !count || CGLCreateContext(format,0,&context) || CGLSetCurrentContext(context)) return 1;
    native_scene_identity_check();
    program=glCreateProgram();glAttachShader(program,compile(GL_VERTEX_SHADER,argv[1]));glAttachShader(program,compile(GL_FRAGMENT_SHADER,argv[2]));
    glBindAttribLocation(program,0,"aPosition");glBindFragDataLocation(program,0,"fragColor");glBindFragDataLocation(program,1,"fragActorMaterial");glBindFragDataLocation(program,2,"fragActorNormal");glLinkProgram(program);
    GLint ok;glGetProgramiv(program,GL_LINK_STATUS,&ok);if(!ok) return 1;glUseProgram(program);
    glGenTextures(5,textures);
    for(int i=0;i<5;i++) { glActiveTexture(GL_TEXTURE0+i);glBindTexture(GL_TEXTURE_2D,textures[i]);
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_NEAREST);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_NEAREST); }
    uniform("uOccludeWorld",1);uniform("uModelColour",0);uniform("uModelDepth",1);uniform("uDhDepth",2);uniform("uNativeDepth",3);
    uniform("uCloudDepth",4);uniform("uUseCloudDepth",0);tex(4,0,0,0,0);
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
    uniform("uOccludeWorld",0);check("through-terrain beam ignores foreground native depth",1,.5);
    tex(2,.1,0,0,1);check("through-terrain beam ignores DH cover",1,.5);
    uniform("uOccludeWorld",1);check("terrain-hidden beam restores DH cover",0,0);tex(2,1,0,0,1);
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
    scene_check(0);scene_check(1);partial_cover_check();
    cloud_check();different_camera_check(0);different_camera_check(1);
    for(int i=0;i<5;i++){float d[]={16,64,128,750,4096};auxiliary_check(d[i],0);auxiliary_check(d[i],1);}
    marker_check();native_depth_copy_check();sampler_state_check();
    uniform("uOpaqueDepthOnly",1);tex(0,.5,.25,.125,.5);tex(1,.5,0,0,1);tex(2,1,0,0,1);
    uniform("uUseNativeDepth",0);uniform("uModelReverseDepth",0);uniform("uSceneDepth",0);uniform("uDhReverseDepth",0);
    matrix("uInverseModelMvp",0);matrix("uDhMvp",0);matrix("uNativeMvp",0);glUniform3f(glGetUniformLocation(program,"uDhOriginOffset"),0,0,0);
    glDepthFunc(GL_ALWAYS);check("translucent actor pixels do not enter opaque depth",0,0);
    tex(0,.5,.25,.125,1);check("solid actor pixels enter opaque depth",1,.5);uniform("uOpaqueDepthOnly",0);
    jitter_alignment_check();stationary_actor_jitter_check();beam_segment_check();actor_surface_check();
    printf("PASS %d actor model GPU checks\n",checks);return 0;
}
