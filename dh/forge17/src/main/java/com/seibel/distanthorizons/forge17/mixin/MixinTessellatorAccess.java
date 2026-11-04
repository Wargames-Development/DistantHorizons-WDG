package com.seibel.distanthorizons.forge17.mixin;

import net.minecraft.client.renderer.Tessellator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Tessellator.class)
public interface MixinTessellatorAccess
{
    @Accessor("rawBuffer") int[] dh$getRawBuffer();
    @Accessor("rawBufferIndex") int dh$getRawBufferIndex();
    @Accessor("rawBufferIndex") void dh$setRawBufferIndex(int value);
    @Accessor("vertexCount") int dh$getVertexCount();
    @Accessor("vertexCount") void dh$setVertexCount(int value);
    @Accessor("addedVertices") int dh$getAddedVertices();
    @Accessor("addedVertices") void dh$setAddedVertices(int value);
    @Accessor("drawMode") int dh$getDrawMode();
    @Accessor("hasColor") boolean dh$hasColor();
    @Accessor("hasTexture") boolean dh$hasTexture();
    @Accessor("hasBrightness") boolean dh$hasBrightness();
}
