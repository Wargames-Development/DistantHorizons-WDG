package com.seibel.distanthorizons.forge17.effects;

import com.seibel.distanthorizons.api.objects.math.DhApiMat4f;
import java.lang.reflect.*;
import java.nio.*;
import java.util.regex.*;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

/** Read the active pack's compiled DH jitter; no frame counter guesses or shader-pack edits. */
final class ShaderActorJitter {
    private static Object previousPipeline;
    private static int previousProgram=-1,frameLocation,widthLocation,heightLocation;
    private static float strength;
    private static Method solid,terrainSource,vertexSource;
    private static Field programId;
    private static final FloatBuffer value=BufferUtils.createFloatBuffer(16);
    private static final float[] X={.125f,-.125f,.625f,.375f,-.625f,-.875f,.375f,.875f};
    private static final float[] Y={-.375f,.375f,.125f,-.625f,.625f,-.125f,-.875f,.875f};
    static void read(Object pipeline,Object dh,float[] out) throws ReflectiveOperationException {
        out[0]=out[1]=0;
        if(solid==null) {
            solid=dh.getClass().getMethod("getSolidShader");
            // This version exposes uniform locations but no program handle/getter.
            // A read-only handle is needed for glGetUniform, never bind/fillUniformData.
            programId=solid.getReturnType().getDeclaredField("id");programId.setAccessible(true);
            terrainSource=pipeline.getClass().getMethod("getDHTerrainShader");
            vertexSource=Class.forName("net.coderbot.iris.shaderpack.ProgramSource").getMethod("getVertexSource");
        }
        Object shader=solid.invoke(dh);if(shader==null) return;
        int program=programId.getInt(shader);if(program<=0) return;
        if(previousPipeline!=pipeline || previousProgram!=program) {
            previousPipeline=pipeline;previousProgram=program;strength=0;
            frameLocation=GL20.glGetUniformLocation(program,"framemod8");
            widthLocation=GL20.glGetUniformLocation(program,"viewWidth");heightLocation=GL20.glGetUniformLocation(program,"viewHeight");
            // ProgramSource is the option-preprocessed input used to create this exact
            // pipeline's terrain program. Do not depend on retained GL shader objects.
            java.util.Optional<?> terrain=(java.util.Optional<?>)terrainSource.invoke(pipeline);
            String source="";
            if(terrain.isPresent()) source=(String)((java.util.Optional<?>)vertexSource.invoke(terrain.get())).orElse(null);
            String reason=source==null || source.isEmpty()?"missing source":"unsupported pattern";
            if(source!=null && matchingOffsets(source)) {
                strength=parseStrength(source);reason=strength==0?"disabled or unsupported function":"matched preprocessed terrain";
            }
            if(frameLocation<0 || widthLocation<0 || heightLocation<0) { strength=0;reason="missing jitter uniforms"; }
            org.apache.logging.log4j.LogManager.getLogger("DistantHorizons").info("DH actor shader jitter: strength="+strength+" program="+program+" source="+reason);

        }
        if(strength==0) return;
        float frame=uniform(program,frameLocation),width=uniform(program,widthLocation),height=uniform(program,heightLocation);
        if(!Float.isFinite(frame) || width<=0 || height<=0) return;
        int index=((int)frame)&7;
        out[0]=X[index]*strength/width;out[1]=Y[index]*strength/height;
    }
    private static float uniform(int program,int location) { value.clear();GL20.glGetUniform(program,location,value);return value.get(0); }
    static boolean matchingOffsets(String source) {
        Matcher declaration=Pattern.compile("jitterOffsets\\s*\\[").matcher(source);
        if(!declaration.find()) return false;int table=declaration.start();
        int end=source.indexOf(";",table);if(end<0) return false;
        Matcher pair=Pattern.compile("vec2\\s*\\(\\s*([-+.0-9]+)\\s*,\\s*([-+.0-9]+)\\s*\\)").matcher(source.substring(table,end));
        int count=0;
        while(pair.find()) {
            if(count>=8 || Math.abs(Float.parseFloat(pair.group(1))-X[count])>1e-6 || Math.abs(Float.parseFloat(pair.group(2))-Y[count])>1e-6) return false;
            count++;
        }
        return count==8;
    }
    static float parseStrength(String source) {
        String compact=source.replaceAll("\\s+","");
        if(!compact.contains("jitterOffsets[int(framemod8)]") || !compact.contains("returncoord+offset;")) return 0;
        Matcher definition=Pattern.compile("(?m)^\\s*#define\\s+TAA_JITTER\\s+([0-9]+)").matcher(source);
        if(definition.find()) {
            int mode=Integer.parseInt(definition.group(1));return mode==0?0:mode==1?.125f:mode==2?.33f:1;
        }
        // Iris normally expands the options before handing source to OpenGL.
        boolean small=compact.contains("offset*=0.125"),medium=compact.contains("offset*=0.33");
        if(small && medium) return 0; // Unexpanded/unknown option: never invent an offset.
        return small?.125f:medium?.33f:1;
    }
    static void apply(DhApiMat4f matrix,float x,float y) {
        matrix.m00+=x*matrix.m30;matrix.m01+=x*matrix.m31;matrix.m02+=x*matrix.m32;matrix.m03+=x*matrix.m33;
        matrix.m10+=y*matrix.m30;matrix.m11+=y*matrix.m31;matrix.m12+=y*matrix.m32;matrix.m13+=y*matrix.m33;
    }
}
