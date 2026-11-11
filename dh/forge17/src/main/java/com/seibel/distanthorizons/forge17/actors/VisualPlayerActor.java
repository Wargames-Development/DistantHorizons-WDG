package com.seibel.distanthorizons.forge17.actors;

import com.mojang.authlib.GameProfile;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

/** Detached render model: never inserted into a world or ticked as a gameplay entity. */
@SideOnly(Side.CLIENT)
public final class VisualPlayerActor extends EntityOtherPlayerMP implements AutoCloseable {
    private final FmurPlayerBridge.Visual extension;
    private PlayerAppearance.Decoded appearance;
    private boolean fmur;
    private int visualFlags,visualBrightness;
    private float bedYaw;
    public VisualPlayerActor(World world,GameProfile profile,int entityId) throws ReflectiveOperationException {
        super(world,profile);setEntityId(entityId);extension=FmurPlayerBridge.createVisual(this);
    }
    public void appearance(PlayerAppearance.Decoded appearance,boolean fmur) throws ReflectiveOperationException {
        this.appearance=appearance;this.fmur=fmur;
        for(int slot=0;slot<5;slot++) {
            ItemStack stack=PlayerAppearance.visibleEquipment(appearance.equipment(slot),fmur);
            setCurrentItemOrArmor(slot,stack);
        }
        extension.appearance(appearance.extension(),fmur);
    }
    public void support(boolean fmur) throws ReflectiveOperationException { if(this.fmur!=fmur && appearance!=null) appearance(appearance,fmur); }
    public float pose(PlayerTrack.Sample sample) throws ReflectiveOperationException {
        if(fmur) extension.pose(sample);
        setPosition(sample.x,sample.y,sample.z);
        lastTickPosX=prevPosX=posX;lastTickPosY=prevPosY=posY;lastTickPosZ=prevPosZ=posZ;
        prevRotationYaw=rotationYaw=sample.yaw;prevRotationPitch=rotationPitch=sample.pitch;
        prevRotationYawHead=rotationYawHead=sample.headYaw;prevRenderYawOffset=renderYawOffset=sample.bodyYaw;
        prevLimbSwingAmount=limbSwingAmount=sample.amount;ticksExisted=(int)sample.age;
        float partial=sample.age-ticksExisted;limbSwing=sample.phase+sample.amount*(1-partial);
        prevSwingProgress=swingProgress=sample.swing;bedYaw=sample.bedYaw;visualFlags=sample.flags;visualBrightness=sample.brightness;
        setSneaking((visualFlags & 1)!=0);setSprinting((visualFlags & 2)!=0);setInvisible((visualFlags & 4)!=0);
        if((visualFlags & 64)!=0 && getHeldItem()!=null) setItemInUse(getHeldItem(),Math.min(sample.useTicks,getHeldItem().getMaxItemUseDuration()));else clearItemInUse();
        return partial;
    }
    public AutoCloseable beginExtensions() throws ReflectiveOperationException { return extension.begin(); }
    @Override public boolean hasCape() {
        return com.seibel.distanthorizons.core.config.Config.Client.Advanced.Graphics.GenericRendering.enablePlayerLodCapes.get()
            && super.hasCape();
    }
    @Override public net.minecraft.util.ResourceLocation getLocationCape() {
        return com.seibel.distanthorizons.core.config.Config.Client.Advanced.Graphics.GenericRendering.enablePlayerLodCapes.get()
            ?super.getLocationCape():null;
    }
    @Override public boolean isRiding() { return (visualFlags & 16)!=0; }
    @Override public boolean isPlayerSleeping() { return (visualFlags & 32)!=0; }
    @Override public float getBedOrientationInDegrees() { return bedYaw; }
    @Override public int getBrightnessForRender(float partial) { return (visualFlags & 8)!=0?0xF000F0:visualBrightness; }
    @Override public float getBrightness(float partial) {
        int block=(visualBrightness & 65535)>>4,sky=Math.max(0,(visualBrightness>>>20)-worldObj.skylightSubtracted);
        return worldObj.provider.lightBrightnessTable[Math.min(15,Math.max(block,sky))];
    }
    public void close() { extension.close(); }
}
