package com.seibel.distanthorizons.forge17.effects;

import java.io.*;
import java.util.zip.ZipFile;
import org.spongepowered.asm.lib.*;
import org.spongepowered.asm.lib.tree.*;

/** Check optional reflection seams and the actual range supplier against real Angelica jars. */
final class ShaderCloudContractChecks {
    private static ClassNode read(ZipFile zip,String name) throws IOException {
        ClassNode node=new ClassNode();
        try(InputStream input=zip.getInputStream(zip.getEntry(name+".class"))) { new ClassReader(input).accept(node,0); }
        return node;
    }
    private static void require(boolean value,String message) { if(!value) throw new AssertionError(message); }
    private static boolean method(ClassNode node,String name,String desc) {
        for(MethodNode method:node.methods) if(method.name.equals(name) && method.desc.equals(desc)
            && (method.access&Opcodes.ACC_PUBLIC)!=0) return true;
        return false;
    }
    static int run(ZipFile zip) throws IOException {
        String iris="net/coderbot/iris/",matching=iris+"gbuffer_overrides/matching/";
        ClassNode pipeline=read(zip,iris+"pipeline/DeferredWorldRenderingPipeline");
        boolean table=false;
        for(FieldNode field:pipeline.fields) table|=field.name.equals("table") && field.desc.equals("L"+matching+"ProgramTable;");
        require(table,"Angelica particle program table seam changed");
        require(method(read(zip,matching+"ProgramTable"),"match","(L"+matching+"RenderCondition;L"+matching+"InputAvailability;)Ljava/lang/Object;"),"Particle program matcher changed");
        require(method(read(zip,matching+"InputAvailability"),"<init>","(ZZ)V"),"Particle input constructor changed");
        boolean condition=false;
        for(FieldNode field:read(zip,matching+"RenderCondition").fields) condition|=field.name.equals("PARTICLES_TRANSLUCENT") && (field.access&Opcodes.ACC_ENUM)!=0;
        require(condition,"Translucent particle program selector changed");
        require(method(read(zip,iris+"pipeline/DeferredWorldRenderingPipeline$Pass"),"getProgram","()L"+iris+"gl/program/Program;"),"Compiled particle program access changed");
        require(method(read(zip,iris+"gl/program/Program"),"getProgramId","()I"),"Compiled GL program ID access changed");
        require(method(read(zip,iris+"compat/dh/DHCompat"),"getRenderDistance","()I"),"Cloud range supplier access changed");
        boolean supplier=false;
        for(MethodNode method:read(zip,iris+"uniforms/CommonUniforms").methods) {
            boolean cloudRange=false;
            for(AbstractInsnNode instruction:method.instructions.toArray()) {
                if(instruction instanceof LdcInsnNode) cloudRange="dhRenderDistance".equals(((LdcInsnNode)instruction).cst);
                else if(cloudRange && instruction instanceof InvokeDynamicInsnNode) {
                    for(Object argument:((InvokeDynamicInsnNode)instruction).bsmArgs) if(argument instanceof Handle) {
                        Handle handle=(Handle)argument;
                        supplier|=handle.getOwner().equals(iris+"compat/dh/DHCompat") && handle.getName().equals("getRenderDistance") && handle.getDesc().equals("()I");
                    }
                    cloudRange=false;
                }
            }
        }
        require(supplier,"Cloud range must match the native shader's actual dhRenderDistance supplier");
        return 8;
    }
}
