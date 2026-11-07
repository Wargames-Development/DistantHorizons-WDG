import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import com.gtnewhorizons.angelica.glsm.redirect.GLSMRedirector;

/** Run with Java 17+ and the installed Angelica/GTNHLib/ASM/Log4j/Guava jars on
 * the classpath. Transforms real effect bytecode without initializing GL/Minecraft.
 * Argument: compiled classes directory or packaged DH jar. */
class AngelicaEffectsLinkCheck {
    private static final String PREFIX="com/seibel/distanthorizons/forge17/effects/";
    private static final String TARGET="com/gtnewhorizons/angelica/glsm/GLStateManager";
    public static void main(String[] args) throws Exception {
        ClassNode state=new ClassNode();
        try(InputStream in=GLSMRedirector.class.getClassLoader().getResourceAsStream(TARGET+".class")) {
            new ClassReader(in).accept(state,ClassReader.SKIP_CODE);
        }
        Set<String> methods=new HashSet<>();
        for(MethodNode method:state.methods) if((method.access&Opcodes.ACC_STATIC)!=0) methods.add(method.name+method.desc);
        Map<String,byte[]> inputs=new TreeMap<>();Path input=Path.of(args[0]);
        if(Files.isDirectory(input)) {
            try(var paths=Files.walk(input.resolve(PREFIX))) {
                for(Path p:paths.filter(p->p.toString().endsWith(".class")).toList())
                    inputs.put(input.relativize(p).toString(),Files.readAllBytes(p));
            }
        } else try(JarFile jar=new JarFile(input.toFile(),true,JarFile.OPEN_READ,Runtime.version())) {
            for(JarEntry entry:jar.stream().filter(e->e.getName().startsWith(PREFIX)&&e.getName().endsWith(".class")).toList())
                try(InputStream in=jar.getInputStream(entry)) { inputs.put(entry.getName(),in.readAllBytes()); }
        }
        GLSMRedirector redirector=new GLSMRedirector();int checked=0;List<String> missing=new ArrayList<>();
        for(var inputClass:inputs.entrySet()) {
            ClassNode node=new ClassNode();new ClassReader(inputClass.getValue()).accept(node,0);
            redirector.transformClassNode(node.name,node,false);
            for(MethodNode method:node.methods) for(AbstractInsnNode instruction:method.instructions) {
                if(instruction instanceof MethodInsnNode call && call.owner.equals(TARGET)) {
                    checked++;
                    if(!methods.contains(call.name+call.desc)) missing.add(node.name+"."+method.name+" -> "+call.name+call.desc);
                }
            }
        }
        if(checked==0) throw new AssertionError("No transformed GL calls were checked");
        if(!missing.isEmpty()) throw new AssertionError("Missing runtime GL methods after Angelica transformation: "+missing);
        System.out.println("PASS "+checked+" transformed effects GL calls link against actual Angelica runtime (Java "+Runtime.version()+")");
    }
}
