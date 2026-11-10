package com.seibel.distanthorizons.forge17.mixin;

import net.minecraft.entity.player.EntityPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** EntityPlayer's public use-count getter is client-only in Forge 1.7.10. */
@Mixin(EntityPlayer.class)
public interface MixinPlayerUseState {
    @Accessor("itemInUseCount") int dh$getItemInUseCount();
}
