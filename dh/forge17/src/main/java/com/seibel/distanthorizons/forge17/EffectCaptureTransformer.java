package com.seibel.distanthorizons.forge17;

import org.spongepowered.asm.lib.Opcodes;
import org.spongepowered.asm.lib.tree.*;

/** Final, narrowly scoped particle dispatch patch. Uses no optional-mod class references.
 * Applied by the low-priority capture mixin's postApply, after injection has replaced calls. */
public final class EffectCaptureTransformer
{
    private static final String PARTICLE="net/minecraft/client/particle/EntityFX";
    private static final String RENDERER="com/seibel/distanthorizons/forge17/effects/DistantSmokeRenderer";
    private static final String ARGS="(Lnet/minecraft/client/renderer/Tessellator;FFFFFF)V";
    private static final String STATIC_ARGS="(L"+PARTICLE+";Lnet/minecraft/client/renderer/Tessellator;FFFFFF)V";
    public static boolean shaderFinalizationHookInstalled;
    private EffectCaptureTransformer() { }
    /** Angelica finalizes BEFORE Forge world-last. Patch the merged callback, not
     * renderWorld's call to Forge, so mixin injection order cannot move us after it. */
    public static int patchShaderFinalization(ClassNode target) {
        int sites=0;
        for(MethodNode method:target.methods) for(AbstractInsnNode node:method.instructions.toArray()) {
            if(!(node instanceof MethodInsnNode)) continue;
            MethodInsnNode call=(MethodInsnNode)node;
            if(!call.owner.equals("net/coderbot/iris/pipeline/WorldRenderingPipeline")
                || !call.name.equals("finalizeLevelRendering") || !call.desc.equals("()V")) continue;
            AbstractInsnNode previous=node.getPrevious();
            if(previous instanceof MethodInsnNode && ((MethodInsnNode)previous).owner.equals(RENDERER)
                && ((MethodInsnNode)previous).name.equals("beforeShaderComposite")) continue;
            // The pipeline receiver stays on the operand stack across this no-arg call.
            method.instructions.insertBefore(node,new MethodInsnNode(Opcodes.INVOKESTATIC,
                RENDERER,"beforeShaderComposite","()V",false));sites++;
        }
        if(sites>0) shaderFinalizationHookInstalled=true;
        return sites;
    }
    /** Returns capture sites and culling gates, packed into the low/high 16 bits. */
    public static int patch(ClassNode target)
    {
        int sites=0,gates=0;
        for(MethodNode method:target.methods)
        {
            for(AbstractInsnNode node=method.instructions.getFirst();node!=null;)
            {
                AbstractInsnNode next=node.getNext();
                if(node instanceof MethodInsnNode)
                {
                    MethodInsnNode call=(MethodInsnNode)node;
                    if(call.owner.equals(PARTICLE) && call.desc.equals(ARGS)
                        && (call.name.equals("renderParticle") || call.name.equals("func_70539_a")))
                    {
                        method.instructions.set(call,new MethodInsnNode(Opcodes.INVOKESTATIC,RENDERER,"renderParticle",STATIC_ARGS,false));
                        sites++;
                    }
                }
                node=next;
            }
            // Angelica tests the terrain viewport before calling the particle renderer.
            // Only reviewed particles inside DH's view/range may bypass that gate.
            if(method.desc.equals("(L"+PARTICLE+";Lnet/minecraft/client/renderer/Tessellator;FFFFFF)V")
                && (method.name.endsWith("$angelica$renderParticles") || method.name.endsWith("$angelica$renderLitParticles")))
            {
                LabelNode normal=new LabelNode();InsnList prefix=new InsnList();
                prefix.add(new VarInsnNode(Opcodes.ALOAD,1));
                prefix.add(new MethodInsnNode(Opcodes.INVOKESTATIC,RENDERER,"needsExtendedDispatch","(L"+PARTICLE+";)Z",false));
                prefix.add(new JumpInsnNode(Opcodes.IFEQ,normal));
                prefix.add(new VarInsnNode(Opcodes.ALOAD,1));prefix.add(new VarInsnNode(Opcodes.ALOAD,2));
                for(int slot=3;slot<=8;slot++) prefix.add(new VarInsnNode(Opcodes.FLOAD,slot));
                prefix.add(new MethodInsnNode(Opcodes.INVOKESTATIC,RENDERER,"renderParticle",STATIC_ARGS,false));
                prefix.add(new InsnNode(Opcodes.RETURN));prefix.add(normal);
                prefix.add(new FrameNode(Opcodes.F_SAME,0,null,0,null));
                method.instructions.insert(prefix);method.maxStack=Math.max(method.maxStack,8);gates++;
            }
        }
        return sites | gates<<16;
    }
}
