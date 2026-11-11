#define GL_SILENCE_DEPRECATION
#include <OpenGL/OpenGL.h>
#include <OpenGL/gl3.h>
#include <stdio.h>
#include <stdlib.h>
#include <math.h>
#include <string.h>
#define SIZE 16
static GLuint program,tex[2];static int checks,hardwareHalf;
static GLuint shader(GLenum kind,const char *source) {
 GLuint s=glCreateShader(kind);glShaderSource(s,1,&source,0);glCompileShader(s);GLint ok;glGetShaderiv(s,GL_COMPILE_STATUS,&ok);
 if(!ok){char log[8192];glGetShaderInfoLog(s,sizeof(log),0,log);fprintf(stderr,"%s\n",log);exit(1);}return s;
}
static char *read_file(const char *path) {
 FILE *f=fopen(path,"rb");if(!f){perror(path);exit(1);}fseek(f,0,SEEK_END);long n=ftell(f);rewind(f);char *s=calloc(n+1,1);if(fread(s,1,n,f)!=(size_t)n)exit(1);fclose(f);return s;
}
static GLuint make_program(const char *path) {
 const char *v="#version 330 core\nin vec2 position;out vec2 texCoord;void main(){texCoord=position*.5+.5;gl_Position=vec4(position,0,1);}";
 char *f=read_file(path);GLuint p=glCreateProgram();glAttachShader(p,shader(GL_VERTEX_SHADER,v));glAttachShader(p,shader(GL_FRAGMENT_SHADER,f));free(f);
 glBindAttribLocation(p,0,"position");glLinkProgram(p);GLint ok;glGetProgramiv(p,GL_LINK_STATUS,&ok);if(!ok){char log[8192];glGetProgramInfoLog(p,sizeof(log),0,log);fprintf(stderr,"%s\n",log);exit(1);}return p;
}
static void integer(const char *n,int v){glUniform1i(glGetUniformLocation(program,n),v);}
static void scalar(const char *n,float v){glUniform1f(glGetUniformLocation(program,n),v);}
static void upload(int unit,const float *data){glActiveTexture(GL_TEXTURE0+unit);glBindTexture(GL_TEXTURE_2D,tex[unit]);glTexImage2D(GL_TEXTURE_2D,0,GL_R32F,SIZE,SIZE,0,GL_RED,GL_FLOAT,data);}
static float depth(float distance,int reverse){float d=(100.0f-100.0f/distance)/99.0f;return reverse?(hardwareHalf?.5f+(1-d)*.5f:1-d):d;}
static void expect(const char *name,int x,int y,float expected) {
 glDrawArrays(GL_TRIANGLES,0,6);float pixel[4];glReadPixels(x,y,1,1,GL_RGBA,GL_FLOAT,pixel);GLenum error=glGetError();
 if(error || !isfinite(pixel[3]) || fabsf(pixel[3]-expected)>.005f){fprintf(stderr,"FAIL %s alpha=%g expected=%g error=%x\n",name,pixel[3],expected,error);exit(1);}checks++;printf("PASS %s\n",name);
}
int main(int argc,char **argv) {
 if(argc!=3)return 2;
 CGLPixelFormatAttribute attrs[]={kCGLPFAOpenGLProfile,(CGLPixelFormatAttribute)kCGLOGLPVersion_3_2_Core,kCGLPFAAllowOfflineRenderers,0};CGLPixelFormatObj format;GLint count;CGLContextObj context;
 if(CGLChoosePixelFormat(attrs,&format,&count)||!count||CGLCreateContext(format,0,&context)||CGLSetCurrentContext(context))return 1;
 GLuint fbo,target;glGenFramebuffers(1,&fbo);glBindFramebuffer(GL_FRAMEBUFFER,fbo);glGenTextures(1,&target);glBindTexture(GL_TEXTURE_2D,target);
 glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA32F,SIZE,SIZE,0,GL_RGBA,GL_FLOAT,0);glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,target,0);
 if(glCheckFramebufferStatus(GL_FRAMEBUFFER)!=GL_FRAMEBUFFER_COMPLETE)return 1;
 GLuint vao,vbo;glGenVertexArrays(1,&vao);glBindVertexArray(vao);glGenBuffers(1,&vbo);glBindBuffer(GL_ARRAY_BUFFER,vbo);float vertices[]={-1,-1,1,-1,1,1,-1,-1,1,1,-1,1};glBufferData(GL_ARRAY_BUFFER,sizeof(vertices),vertices,GL_STATIC_DRAW);glVertexAttribPointer(0,2,GL_FLOAT,GL_FALSE,0,0);glEnableVertexAttribArray(0);glViewport(0,0,SIZE,SIZE);
 glGenTextures(2,tex);for(int i=0;i<2;i++){glActiveTexture(GL_TEXTURE0+i);glBindTexture(GL_TEXTURE_2D,tex[i]);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_NEAREST);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_NEAREST);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,GL_REPEAT);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,GL_REPEAT);}
 program=make_program(argv[2]);glUseProgram(program);integer("uSourceColorTexture",0);integer("uSourceDepthTexture",1);integer("uBlurRadius",2);glUniform2f(glGetUniformLocation(program,"uViewSize"),SIZE,SIZE);
 float ao[SIZE*SIZE],z[SIZE*SIZE];
 for(int mode=0;mode<5;mode++){
  int reverse=mode==1||mode==2||mode==3,zero=mode>=3;hardwareHalf=mode==2;
  float a=reverse?((zero||hardwareHalf)?1.0f:101.0f)/99.0f:-(zero?100.0f:101.0f)/99.0f;
  float b=reverse?((zero||hardwareHalf)?100.0f:200.0f)/99.0f:-(zero?100.0f:200.0f)/99.0f;
  float inverse[16]={1,0,0,0,0,1,0,0,0,0,0,1/b,0,0,-1,a/b};
  glUniformMatrix4fv(glGetUniformLocation(program,"uInvProj"),1,GL_FALSE,inverse);
  integer("uDepthIsZeroToPositiveOne",zero);integer("uIsReverseZDepth",reverse);
  for(int i=0;i<SIZE*SIZE;i++){ao[i]=(i%SIZE>=SIZE-2)?0:1;z[i]=depth(20,reverse);}upload(0,ao);upload(1,z);
  expect("blur never wraps opposite edge into left edge",0,8,1);expect("blur never wraps opposite edge into bottom edge",0,0,1);
  for(int i=0;i<SIZE*SIZE;i++){ao[i]=(i/SIZE>=SIZE-2)?0:1;}upload(0,ao);
  expect("blur never wraps top edge into bottom edge",8,0,1);
  for(int i=0;i<SIZE*SIZE;i++){int sky=i%SIZE<8;ao[i]=sky?0:1;z[i]=sky?(reverse?0:1):depth(20,reverse);}upload(0,ao);upload(1,z);
  expect("sky cannot darken adjacent terrain",8,8,1);expect("sky remains neutral",7,8,1);
  for(int i=0;i<SIZE*SIZE;i++){int distant=i%SIZE>=8;ao[i]=distant?0:1;z[i]=depth(distant?50:20,reverse);}upload(0,ao);upload(1,z);
  expect("bilateral blur preserves physical depth discontinuity",7,8,1);
 }
 // Compile and execute the real AO stage on a flat plane over FOV/depth modes.
 glDeleteProgram(program);program=make_program(argv[1]);glUseProgram(program);integer("uDhDepthTexture",0);integer("uSampleCount",6);scalar("uRadius",4);scalar("uStrength",.2);scalar("uMinLight",.25);scalar("uBias",.02);scalar("uFadeDistanceInBlocks",1600);
 for(int reverse=0;reverse<2;reverse++)for(int zero=0;zero<2;zero++)for(int f=0;f<3;f++){
  float fov=f==0?70:f==1?30:4,scale=1/tanf(fov*.5f*3.141592653589793f/180);
  float a=zero?-100.0f/99:-101.0f/99,b=zero?-100.0f/99:-200.0f/99;
  if(reverse){if(zero){a=-1-a;b=-b;}else{a=-a;b=-b;}}
  float p[16]={scale,0,0,0,0,scale,0,0,0,0,a,-1,0,0,b,0};float inv[16]={1/scale,0,0,0,0,1/scale,0,0,0,0,0,1/b,0,0,-1,a/b};
  glUniformMatrix4fv(glGetUniformLocation(program,"uProj"),1,GL_FALSE,p);glUniformMatrix4fv(glGetUniformLocation(program,"uInvProj"),1,GL_FALSE,inv);integer("uIsReverseZDepth",reverse);integer("uDepthIsZeroToPositiveOne",zero);
  float ndc=(-a*20+b)/20,d=zero?ndc:ndc*.5f+.5f;for(int i=0;i<SIZE*SIZE;i++)z[i]=d;upload(0,z);glDrawArrays(GL_TRIANGLES,0,6);
  float pixels[SIZE*SIZE*4];glReadPixels(0,0,SIZE,SIZE,GL_RGBA,GL_FLOAT,pixels);
  for(int i=0;i<SIZE*SIZE;i++)if(!isfinite(pixels[i*4])||fabsf(pixels[i*4]-1)>.005f){fprintf(stderr,"FAIL planar AO FOV=%g reverse=%d zero=%d pixel=%d value=%g\n",fov,reverse,zero,i,pixels[i*4]);return 1;}
  checks++;printf("PASS planar AO finite and neutral FOV=%g reverse=%d zero=%d\n",fov,reverse,zero);
  // Equivalent physical scene in forward/reverse depth: sky at the upper edge,
  // a plane at four blocks and a closer raised strip. Unknown sky samples must
  // not dilute the reverse-depth occlusion estimate.
  for(int i=0;i<SIZE*SIZE;i++) {
   if(i/SIZE>=12) z[i]=reverse?0:1;
   else {float distance=i%SIZE>=10?3:4;float point=(-a*distance+b)/distance;z[i]=zero?point:point*.5f+.5f;}
  }
  upload(0,z);glDrawArrays(GL_TRIANGLES,0,6);glReadPixels(0,0,SIZE,SIZE,GL_RGBA,GL_FLOAT,pixels);
  static float scenes[2][3][SIZE*SIZE*4];
  if(!reverse) memcpy(scenes[zero][f],pixels,sizeof(pixels));
  else for(int i=0;i<SIZE*SIZE;i++) if(!isfinite(pixels[i*4])||fabsf(pixels[i*4]-scenes[zero][f][i*4])>.005f){fprintf(stderr,"FAIL forward/reverse scene parity fov=%g zero=%d pixel=%d value=%g reference=%g\n",fov,zero,i,pixels[i*4],scenes[zero][f][i*4]);return 1;}
  checks++;printf("PASS occluder/sky scene depth parity FOV=%g reverse=%d zero=%d\n",fov,reverse,zero);
 }
 printf("PASS %d SSAO GPU checks\n",checks);return 0;
}
