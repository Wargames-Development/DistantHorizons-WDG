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
static int write_depth,checks,check_material,physical_projection;
static float physical_distance,physical_near;
static float expected_written_depth=.5f;
static float expected_visible=.5f,dh_projection_z=0,dh_projection_x=0,dh_projection_y=0,origin_x=0,origin_y=0;
static void texture(int unit,const float *rgba) {
    GLuint id;glGenTextures(1,&id);glActiveTexture(GL_TEXTURE0+unit);glBindTexture(GL_TEXTURE_2D,id);
    glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_NEAREST);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_NEAREST);
    glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA,1,1,0,GL_RGBA,GL_FLOAT,rgba);
}
static void check(const char *name,float native_z,float terrain_depth,float native_depth,int visible) {
    float identity[16]={1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1};
    identity[12]=dh_projection_x;identity[13]=dh_projection_y;identity[14]=dh_projection_z;
    float native[16]={1,0,0,0,0,1,0,0,0,0,1,0,0,0,native_z,1};
    if(physical_projection) {
        // XY fills the fixture; Z/W retain real perspective depths from the
        // recorded terminal flight. Both samplers and the hardware depth target
        // use Angelica's terrain projection, independently of the effect matrix.
        float f=23894.552f,d=physical_distance,n=physical_near;
        float a=-(f+n)/(f-n),b=-2*f*n/(f-n);
        identity[0]=identity[5]=d;identity[10]=a;identity[11]=-1;identity[14]=b-a*d;identity[15]=d;
        a=-(256.0f+.05f)/(256.0f-.05f);b=-2*256.0f*.05f/(256.0f-.05f);
        native[0]=native[5]=d;native[10]=a;native[11]=-1;native[14]=b-a*d;native[15]=d;
    }
    glActiveTexture(GL_TEXTURE2);glBindTexture(GL_TEXTURE_2D,dh_depth);
    glTexImage2D(GL_TEXTURE_2D,0,GL_DEPTH_COMPONENT24,1,1,0,GL_DEPTH_COMPONENT,GL_FLOAT,&terrain_depth);
    glActiveTexture(GL_TEXTURE0);
    glUniformMatrix4fv(glGetUniformLocation(program,"uExtendedMvp"),1,GL_FALSE,identity);
    glUniformMatrix4fv(glGetUniformLocation(program,"uNativeMvp"),1,GL_FALSE,native);
    glUniform3f(glGetUniformLocation(program,"uDhOriginOffset"),origin_x,origin_y,0);
    glClearDepth(native_depth);glDepthMask(GL_TRUE);glClear(GL_COLOR_BUFFER_BIT|GL_DEPTH_BUFFER_BIT);glDepthMask(write_depth?GL_TRUE:GL_FALSE);
    if(check_material) { float previous[4]={.25,.25,.25,1};glClearBufferfv(GL_COLOR,1,previous); }
    glDrawArrays(GL_TRIANGLES,0,6);
    float pixel[4],depth;glReadPixels(2,2,1,1,GL_RGBA,GL_FLOAT,pixel);glReadPixels(2,2,1,1,GL_DEPTH_COMPONENT,GL_FLOAT,&depth);
    if(check_material) {
        float material[4];glReadBuffer(GL_COLOR_ATTACHMENT1);glReadPixels(2,2,1,1,GL_RGBA,GL_FLOAT,material);glReadBuffer(GL_COLOR_ATTACHMENT0);
        float expected_material=visible?254.0f/255.0f:.25f;
        if(fabsf(material[1]-expected_material)>.003f) { fprintf(stderr,"FAIL %s material=%f expected=%f\n",name,material[1],expected_material);exit(1); }
    }
    GLenum error=glGetError();float expected=visible?expected_visible:0;
    if(error || fabsf(pixel[0]-expected)>.01f || fabsf(depth-(write_depth && visible?expected_written_depth:native_depth))>.0001f) {
        fprintf(stderr,"FAIL %s red=%f depth=%f error=%x\n",name,pixel[0],depth,error);exit(1);
    }
    checks++;printf("PASS %s\n",name);
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
    glBindFragDataLocation(program,0,"fragColor");glBindFragDataLocation(program,1,"fragParticleMaterial");glLinkProgram(program);GLint ok;glGetProgramiv(program,GL_LINK_STATUS,&ok);
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
    glUniform1i(glGetUniformLocation(program,"uWholeModel"),1);
    check("whole missile keeps near faces",.5,1,1,1);
    check("whole missile keeps far faces",2,1,1,1);
    check("whole missile keeps far-plane boundary faces",1,1,1,1);
    check("whole missile near faces respect native foreground",.5,1,.5,0);
    check("whole missile far faces respect DH foreground",2,.25,1,0);
    dh_projection_z=3;
    check("whole missile remains visible before DH near plane",.5,1,1,1);
    dh_projection_z=0;
    glUniform1i(glGetUniformLocation(program,"uWholeModel"),0);
    check("particle far ownership unchanged after whole-model pass",.5,1,1,0);
    // The two cameras can have different origins. DH origin conversion must
    // affect depth lookup, never move a plume relative to its native model.
    dh_projection_x=2;origin_x=-2;
    check("far plume keeps native screen X despite DH camera origin",2,1,1,1);
    glUniform1i(glGetUniformLocation(program,"uWholeModel"),1);
    check("whole missile shares the plume screen X",2,1,1,1);
    glUniform1i(glGetUniformLocation(program,"uWholeModel"),0);
    dh_projection_x=0;origin_x=0;dh_projection_y=2;origin_y=-2;
    check("far plume keeps native screen Y despite DH camera origin",2,1,1,1);
    glUniform1i(glGetUniformLocation(program,"uWholeModel"),1);
    check("whole missile shares the plume screen Y",2,1,1,1);
    glUniform1i(glGetUniformLocation(program,"uWholeModel"),0);
    check("aligned plume still respects DH terrain depth",2,.25,1,0);
    dh_projection_y=0;origin_y=0;
    float sky_depth[4]={1,1,1,1};texture(3,sky_depth);glActiveTexture(GL_TEXTURE0);
    glUniform1i(glGetUniformLocation(program,"uNativeDepth"),3);
    glUniform1i(glGetUniformLocation(program,"uShaderPass"),1);
    check("shader scene draws effects with DH projection depth",2,1,1,1);
    check("DH cloud or terrain depth hides effects behind it",2,.25,1,0);
    check("DH clouds disabled or farther away leave effects visible",2,.75,1,1);
    float foreground_depth[4]={.25,.25,.25,1};texture(3,foreground_depth);glActiveTexture(GL_TEXTURE0);
    check("native foreground occludes shader scene effects",2,1,1,0);
    texture(3,sky_depth);glActiveTexture(GL_TEXTURE0);
    glUniform1i(glGetUniformLocation(program,"uWholeModel"),1);write_depth=1;
    check("opaque missile writes DH depth for later shader clouds and fog",2,1,1,1);
    write_depth=0;glUniform1i(glGetUniformLocation(program,"uWholeModel"),0);
    check("transparent plume preserves existing DH scene depth",2,1,1,1);
    glUniform1i(glGetUniformLocation(program,"uDhReverseDepth"),1);glDepthFunc(GL_GEQUAL);
    check("shader reversed-depth cloud sky remains visible",2,0,0,1);
    check("shader reversed-depth foreground cloud occludes effect",2,.75,0,0);
    glUniform1i(glGetUniformLocation(program,"uDhReverseDepth"),0);glDepthFunc(GL_LEQUAL);
    glUniform1i(glGetUniformLocation(program,"uShaderPass"),0);
    check("shader toggle restores shaderless far rendering",2,1,1,1);
    glUniform1i(glGetUniformLocation(program,"uShaderPass"),1);
    check("shader relay smoke remains visible inside native far plane",.5,1,1,1);
    check("shader relay smoke remains visible outside native far plane",2,1,1,1);
    dh_projection_z=-3;
    check("shader near plume survives in front of DH near plane",.5,1,1,1);
    float near_foreground[4]={.25,.25,.25,1};texture(3,near_foreground);glActiveTexture(GL_TEXTURE0);
    check("before-DH-near plume still respects native foreground",.5,1,1,0);
    texture(3,sky_depth);glActiveTexture(GL_TEXTURE0);
    glUniform1i(glGetUniformLocation(program,"uWholeModel"),1);write_depth=1;expected_written_depth=0;
    check("near shader body writes clamped forward DH depth",.5,1,1,1);
    write_depth=0;expected_written_depth=.5f;glUniform1i(glGetUniformLocation(program,"uWholeModel"),0);
    check("shader retains native camera near clipping",-2,1,1,0);
    dh_projection_z=3;
    check("shader retains DH far clipping",2,1,1,0);
    glUniform1i(glGetUniformLocation(program,"uDhReverseDepth"),1);glDepthFunc(GL_GEQUAL);
    check("reversed-depth plume survives in front of DH near plane",.5,0,0,1);
    glUniform1i(glGetUniformLocation(program,"uWholeModel"),1);write_depth=1;expected_written_depth=1;
    check("near shader body writes clamped reversed DH depth",.5,0,0,1);
    write_depth=0;expected_written_depth=.5f;glUniform1i(glGetUniformLocation(program,"uWholeModel"),0);
    dh_projection_z=-3;
    check("shader retains reversed DH far clipping",2,0,0,0);
    glUniform1i(glGetUniformLocation(program,"uDhReverseDepth"),0);glDepthFunc(GL_LEQUAL);
    dh_projection_z=0;
    float viewMatrix[16]={1,0,0,0,0,1,0,0,0,0,1,0,0,0,-100,1};
    glUniformMatrix4fv(glGetUniformLocation(program,"uNativeView"),1,GL_FALSE,viewMatrix);
    glUniform1i(glGetUniformLocation(program,"uUseCloudDepth"),1);
    glUniform1i(glGetUniformLocation(program,"uCloudDepth"),4);
    glUniform1f(glGetUniformLocation(program,"uCloudRenderDistance"),1000);
    float dense_cloud[4]={0,0,0,.2};texture(4,dense_cloud);glActiveTexture(GL_TEXTURE0);
    check("Bountiful dense cloud mask hides plume behind cloud",2,1,1,0);
    glUniform1i(glGetUniformLocation(program,"uWholeModel"),1);
    check("Bountiful dense cloud mask also hides missile body",2,1,1,0);
    glUniform1i(glGetUniformLocation(program,"uWholeModel"),0);
    float distant_cloud[4]={0,0,0,.5};texture(4,distant_cloud);glActiveTexture(GL_TEXTURE0);
    check("plume in front of Bountiful cloud stays visible",2,1,1,1);
    float no_cloud[4]={0,0,0,1};texture(4,no_cloud);glActiveTexture(GL_TEXTURE0);
    check("clear sky cloud mask does not hide plume",2,1,1,1);
    texture(4,dense_cloud);glActiveTexture(GL_TEXTURE0);
    glUniform1i(glGetUniformLocation(program,"uUseCloudDepth"),0);
    check("cloud-disabled shader variant ignores stale cloud texture",2,1,1,1);
    // Probe stages must isolate the reason while leaving all scene writes to the caller.
    glUniform1i(glGetUniformLocation(program,"uDiagnosticStage"),1);expected_visible=1;glDisable(GL_DEPTH_TEST);
    check("GPU raster probe survives terrain and foreground rejection",2,.25,.25,1);
    glUniform1i(glGetUniformLocation(program,"uDiagnosticStage"),2);glEnable(GL_DEPTH_TEST);
    check("GPU depth probe identifies terrain rejection",2,.25,1,0);
    check("GPU depth probe identifies visible geometry",2,1,1,1);
    glUniform1i(glGetUniformLocation(program,"uUseCloudDepth"),1);
    check("GPU depth probe excludes cloud rejection",2,1,1,1);
    glUniform1i(glGetUniformLocation(program,"uDiagnosticStage"),3);
    check("GPU cloud probe identifies dense-mask rejection",2,1,1,0);
    texture(4,no_cloud);glActiveTexture(GL_TEXTURE0);
    check("GPU cloud probe identifies clear geometry",2,1,1,1);
    glUniform1f(glGetUniformLocation(program,"uAlphaCutoff"),.75);
    check("GPU cloud probe excludes source alpha rejection",2,1,1,1);
    glUniform1i(glGetUniformLocation(program,"uDiagnosticStage"),0);expected_visible=.5;
    check("visible pass still enforces source alpha after probes",2,1,1,0);
    glUniform1f(glGetUniformLocation(program,"uAlphaCutoff"),.0039f);
    glUniform1i(glGetUniformLocation(program,"uShaderPass"),0);
    check("shaderless native ownership still rejects replay of near smoke",.5,1,1,0);
    // Native particle metadata is an independent MRT output. It must replace
    // terrain material even when the original effect uses additive blending.
    GLuint material;glGenRenderbuffers(1,&material);glBindRenderbuffer(GL_RENDERBUFFER,material);glRenderbufferStorage(GL_RENDERBUFFER,GL_RGBA8,4,4);
    glFramebufferRenderbuffer(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT1,GL_RENDERBUFFER,material);
    GLenum outputs[2]={GL_COLOR_ATTACHMENT0,GL_COLOR_ATTACHMENT1};glDrawBuffers(2,outputs);glDisablei(GL_BLEND,1);check_material=1;
    glUniform1i(glGetUniformLocation(program,"uShaderPass"),1);glUniform1i(glGetUniformLocation(program,"uUseCloudDepth"),0);
    check("shader plume writes native particle material without changing colour blend",.5,1,1,1);
    glBlendFunc(GL_ONE,GL_ZERO);expected_visible=1;
    glUniform1i(glGetUniformLocation(program,"uWholeModel"),1);write_depth=1;
    check("opaque shader missile writes native material and depth",2,1,1,1);
    glUniform1i(glGetUniformLocation(program,"uWholeModel"),0);write_depth=0;
    glBlendFunc(GL_SRC_ALPHA,GL_ONE);expected_visible=.5;
    check("additive explosion replaces material instead of adding to terrain marker",2,1,1,1);
    glUniform1i(glGetUniformLocation(program,"uUseCloudDepth"),1);texture(4,dense_cloud);glActiveTexture(GL_TEXTURE0);
    check("cloud-hidden fragments leave original terrain material intact",2,1,1,0);
    glUniform1i(glGetUniformLocation(program,"uUseCloudDepth"),0);
    check("depth-hidden fragments leave original terrain material intact",2,.25,1,0);
    glUniform1f(glGetUniformLocation(program,"uAlphaCutoff"),.75);
    check("transparent texels leave original terrain material intact",2,1,1,0);
    glUniform1f(glGetUniformLocation(program,"uAlphaCutoff"),.0039f);
    glColorMask(GL_FALSE,GL_FALSE,GL_FALSE,GL_FALSE);
    check("diagnostic draws cannot write colour or native particle marker",2,1,1,0);
    glColorMask(GL_TRUE,GL_TRUE,GL_TRUE,GL_TRUE);check_material=0;
    glDrawBuffer(GL_COLOR_ATTACHMENT0);glFramebufferRenderbuffer(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT1,GL_RENDERBUFFER,0);
    glUniform1i(glGetUniformLocation(program,"uShaderPass"),0);glBlendFunc(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA);
    check("shaderless far rendering works with material output detached",2,1,1,1);
    // Reconstruct the actual pack/core projection disagreement. The recorded
    // body is at y=137.88, ~28.5 blocks above the impact plane. The old effects
    // matrix uses n=7.5; Angelica's dhProjection uses the uncapped API near plane.
    // Demonstrate both the reported shader-only failure and the matching-matrix
    // result without changing placement, blending, cloud or handoff behaviour.
    physical_projection=1;physical_distance=322.70f;physical_near=7.5f;
    float actualNear=15.4502f,actualFar=23894.552f,groundDistance=364.01f;
    float actualGround=actualFar/(actualFar-actualNear)*(1-actualNear/groundDistance);
    float coreGround=actualFar/(actualFar-7.5f)*(1-7.5f/groundDistance);
    glUniform1i(glGetUniformLocation(program,"uShaderPass"),1);
    check("X45 recorded descent reproducer: core projection falsely puts missile behind ground",2,actualGround,actualGround,0);
    physical_near=actualNear;
    check("same recorded descent with terrain projection keeps missile above ground",2,actualGround,actualGround,1);
    physical_near=7.5f;glUniform1i(glGetUniformLocation(program,"uShaderPass"),0);
    check("same recorded descent shaderless uses matching core terrain depth",2,coreGround,1,1);
    glUniform1i(glGetUniformLocation(program,"uShaderPass"),1);
    check("projection mismatch stays hidden against clear sky",2,1,1,1);
    physical_near=actualNear;physical_distance=343.0f;
    float behindGround=actualFar/(actualFar-actualNear)*(1-actualNear/340.0f);
    check("matched projection still hides geometry behind ground",2,behindGround,behindGround,0);
    physical_projection=0;
    printf("Shader compile/link and %d Core-GL VBO pixel/depth checks passed (%s; %s).\n",checks,glGetString(GL_RENDERER),glGetString(GL_VERSION));
    CGLSetCurrentContext(NULL);CGLDestroyContext(context);CGLDestroyPixelFormat(format);return 0;
}
