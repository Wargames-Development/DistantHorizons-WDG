package com.seibel.distanthorizons.forge17.actors;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import io.netty.buffer.ByteBuf;
import java.io.*;
import java.util.Arrays;
import java.util.UUID;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;

/** Bounded, immutable appearance bytes; NBT is decoded only on the client thread. */
public final class PlayerAppearance {
    public static final int MAX_BYTES=16384, HEADER_BYTES=59;
    public final long subscription,revision;
    public final UUID viewer,player;
    public final int dimension,entityId;
    private final byte[] data;
    public PlayerAppearance(long subscription,UUID viewer,int dimension,UUID player,int entityId,long revision,byte[] data) {
        if(subscription==0 || viewer==null || player==null || player.equals(viewer) || entityId<0 || revision<=0
                || data==null || data.length==0 || data.length>MAX_BYTES) throw new IllegalArgumentException("Invalid player appearance");
        this.subscription=subscription;this.viewer=viewer;this.dimension=dimension;this.player=player;this.entityId=entityId;
        this.revision=revision;this.data=Arrays.copyOf(data,data.length);
    }
    public int bytes() { return data.length+HEADER_BYTES; }
    public void write(ByteBuf out) {
        out.writeByte(PlayerFrame.VERSION).writeLong(subscription);uuid(out,viewer);out.writeInt(dimension);uuid(out,player);
        out.writeInt(entityId).writeLong(revision).writeShort(data.length).writeBytes(data);
    }
    public static PlayerAppearance read(ByteBuf in) {
        if(in.readableBytes()<HEADER_BYTES+1 || in.readableBytes()>MAX_BYTES+HEADER_BYTES || in.readUnsignedByte()!=PlayerFrame.VERSION)
            throw new IllegalArgumentException("Invalid appearance envelope");
        long token=in.readLong();UUID viewer=uuid(in);int dimension=in.readInt();UUID player=uuid(in);
        int entity=in.readInt();long revision=in.readLong();int length=in.readUnsignedShort();
        if(length==0 || length>MAX_BYTES || length!=in.readableBytes()) throw new IllegalArgumentException("Invalid appearance length");
        byte[] data=new byte[length];in.readBytes(data);return new PlayerAppearance(token,viewer,dimension,player,entity,revision,data);
    }
    public NBTTagCompound decode() throws IOException {
        DataInputStream input=new DataInputStream(new ByteArrayInputStream(data));
        NBTTagCompound tag=CompressedStreamTools.func_152456_a(input,new NBTSizeTracker(MAX_BYTES*4L));
        if(tag==null || input.available()!=0) throw new IOException("Trailing appearance NBT");
        return tag;
    }
    static byte[] capture(EntityPlayerMP player) throws IOException {
        NBTTagCompound tag=new NBTTagCompound();
        String name=player.getGameProfile().getName();
        if(name==null || name.length()>16 || name.isEmpty()) throw new IOException("Invalid profile name");
        tag.setString("name",name);
        for(Property property:player.getGameProfile().getProperties().get("textures")) {
            if(property.getValue().length()>8192 || (property.hasSignature() && property.getSignature().length()>2048))
                throw new IOException("Oversized skin property");
            tag.setString("textures",property.getValue());
            if(property.hasSignature()) tag.setString("signature",property.getSignature());
            break;
        }
        NBTTagList equipment=new NBTTagList();
        for(int slot=0;slot<5;slot++) {
            NBTTagCompound item=new NBTTagCompound();ItemStack stack=player.getEquipmentInSlot(slot);
            if(stack!=null) stack.writeToNBT(item);item.setByte("slot",(byte)slot);equipment.appendTag(item);
        }
        tag.setTag("equipment",equipment);return encode(tag);
    }
    static byte[] encode(NBTTagCompound tag) throws IOException {
        final ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        OutputStream bounded=new OutputStream() {
            public void write(int value) throws IOException { if(bytes.size()>=MAX_BYTES) throw new IOException("Appearance byte budget");bytes.write(value); }
            public void write(byte[] values,int offset,int length) throws IOException {
                if(length>MAX_BYTES-bytes.size()) throw new IOException("Appearance byte budget");bytes.write(values,offset,length);
            }
        };
        CompressedStreamTools.write(tag,new DataOutputStream(bounded));return bytes.toByteArray();
    }
    public Decoded appearance() throws IOException {
        NBTTagCompound tag=decode();String name=tag.getString("name");
        if(name.isEmpty() || name.length()>16) throw new IOException("Invalid player profile name");
        GameProfile profile=new GameProfile(player,name);
        String texture=tag.getString("textures"),signature=tag.getString("signature");
        if(texture.length()>8192 || signature.length()>2048) throw new IOException("Oversized skin property");
        if(!texture.isEmpty()) profile.getProperties().put("textures",signature.isEmpty()?new Property("textures",texture):new Property("textures",texture,signature));
        NBTTagList items=tag.getTagList("equipment",10);
        if(items.tagCount()!=5) throw new IOException("Invalid equipment slots");
        ItemStack[] equipment=new ItemStack[5];int slots=0;
        for(int i=0;i<5;i++) {
            NBTTagCompound item=items.getCompoundTagAt(i);int slot=item.getByte("slot");
            if(slot<0 || slot>=5 || (slots & (1<<slot))!=0) throw new IOException("Duplicate equipment slot");
            slots|=1<<slot;equipment[slot]=ItemStack.loadItemStackFromNBT(item);
        }
        return new Decoded(profile,equipment);
    }
    public static final class Decoded {
        public final GameProfile profile;
        private final ItemStack[] equipment;
        private Decoded(GameProfile profile,ItemStack[] equipment) { this.profile=profile;this.equipment=equipment; }
        public ItemStack equipment(int slot) { return equipment[slot]==null?null:equipment[slot].copy(); }
    }
    private static void uuid(ByteBuf out,UUID id) { out.writeLong(id.getMostSignificantBits()).writeLong(id.getLeastSignificantBits()); }
    private static UUID uuid(ByteBuf in) { return new UUID(in.readLong(),in.readLong()); }
}
