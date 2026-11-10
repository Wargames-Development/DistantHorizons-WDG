package com.seibel.distanthorizons.forge17.actors;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.*;

public final class PlayerProtocolChecks {
    private static int checks;
    private static final UUID VIEWER=new UUID(1,2), TARGET=new UUID(3,4);
    private static void check(boolean value,String reason) { if(!value) throw new AssertionError(reason);checks++; }
    private static PlayerFrame.Pose pose(UUID uuid,int id,double x) {
        return new PlayerFrame.Pose(uuid,id,x,100,-200,359,-20,10,20,0.5F,65,100,0xF000F0);
    }
    private static PlayerFrame frame(long token,long seq,int dim,int range) {
        return new PlayerFrame(token,seq,VIEWER,dim,range,range==0?Collections.emptyList():Collections.singletonList(pose(TARGET,2,10)));
    }
    private static void invalid(Runnable action,String reason) {
        try { action.run();throw new AssertionError(reason); } catch(IllegalArgumentException | IndexOutOfBoundsException expected) { checks++; }
    }
    public static int run() {
        checks=0;
        for(int requested=0;requested<=1024;requested++) for(int maximum:new int[]{-1,0,1,511,512,750,1024,4096}) {
            int effective=PlayerFrame.effectiveRange(requested,maximum,true);
            check(effective>=0 && effective<=requested && effective<=Math.max(0,maximum),"server range constrains requests");
            check(PlayerFrame.effectiveRange(requested,maximum,false)==0,"server category disable wins");
        }
        check(PlayerFrame.effectiveRange(Integer.MAX_VALUE,1024,true)==0,"invalid client radius denied");
        check(PlayerFrame.effectiveRange(-1,1024,true)==0,"negative client radius denied");
        ByteBuf out=Unpooled.buffer();
        try {
            final ByteBuf packet=out;
            frame(7,1,0,512).write(out);
            PlayerFrame decoded=PlayerFrame.read(out.duplicate());
            check(decoded.players.size()==1 && decoded.players.get(0).uuid.equals(TARGET),"player identity round trip");
            check(decoded.players.get(0).x==10 && decoded.players.get(0).swing==0.5F,"pose round trip");
            for(int length=0;length<out.readableBytes();length++) {
                final int n=length;invalid(()->PlayerFrame.read(packet.slice(0,n)),"truncated player frame accepted");
            }
            out.writeByte(0);invalid(()->PlayerFrame.read(packet.duplicate()),"trailing data accepted");out.writerIndex(out.writerIndex()-1);
            out.setByte(0,99);invalid(()->PlayerFrame.read(packet.duplicate()),"unknown protocol accepted");out.setByte(0,PlayerFrame.VERSION);
        } finally { out.release(); }
        invalid(()->pose(TARGET,2,Double.NaN),"NaN pose accepted");
        invalid(()->pose(TARGET,2,Double.POSITIVE_INFINITY),"infinite pose accepted");
        invalid(()->pose(TARGET,2,30000001),"out of world pose accepted");
        invalid(()->new PlayerFrame(1,1,VIEWER,0,512,Arrays.asList(pose(TARGET,2,1),pose(TARGET,3,1))),"duplicate UUID accepted");
        invalid(()->new PlayerFrame(1,1,VIEWER,0,512,Arrays.asList(pose(TARGET,2,1),pose(new UUID(5,6),2,1))),"duplicate entity ID accepted");
        invalid(()->new PlayerFrame(1,1,VIEWER,0,512,Collections.singletonList(pose(VIEWER,2,1))),"self accepted");
        invalid(()->frame(0,1,0,512),"zero subscription accepted");
        List<PlayerFrame.Pose> source=new ArrayList<PlayerFrame.Pose>();source.add(pose(TARGET,2,1));
        PlayerFrame copied=new PlayerFrame(1,1,VIEWER,0,512,source);source.clear();
        check(copied.players.size()==1,"frame copies mutable source list");
        try { copied.players.clear();throw new AssertionError("mutable frame"); } catch(UnsupportedOperationException expected) { checks++; }
        List<PlayerFrame.Pose> maximum=new ArrayList<PlayerFrame.Pose>();
        for(int i=0;i<PlayerFrame.MAX_PLAYERS;i++) maximum.add(new PlayerFrame.Pose(new UUID(100,i),i,0,100,0,0,0,0,0,0,0,1,0,1,0,0,new byte[PlayerFrame.MAX_EXTENSION_BYTES]));
        out=Unpooled.buffer();
        try {
            new PlayerFrame(1,1,VIEWER,0,1024,maximum).write(out);
            check(out.readableBytes()<=PlayerFrame.MAX_BYTES,"maximum player frame byte bound");
            check(PlayerFrame.read(out).players.size()==PlayerFrame.MAX_PLAYERS,"maximum frame round trip");
        } finally { out.release(); }
        maximum.add(pose(new UUID(200,1),200,0));
        invalid(()->new PlayerFrame(1,1,VIEWER,0,512,maximum),"oversized actor set accepted");
        byte[] extension=new byte[PlayerFrame.MAX_EXTENSION_BYTES];extension[0]=7;
        PlayerFrame.Pose extended=new PlayerFrame.Pose(TARGET,2,0,0,0,0,0,0,0,0,0,1,0,1,0,0,extension);
        extension[0]=9;check(extended.extension()[0]==7,"source animation buffer is copied");
        byte[] exported=extended.extension();exported[0]=10;check(extended.extension()[0]==7,"animation accessor cannot mutate a frame");
        invalid(()->new PlayerFrame.Pose(TARGET,2,0,0,0,0,0,0,0,0,0,1,0,1,0,0,new byte[PlayerFrame.MAX_EXTENSION_BYTES+1]),"oversized extension accepted");
        ByteBuf extensionPacket=Unpooled.buffer();
        try {
            new PlayerFrame(1,1,VIEWER,0,512,Collections.singletonList(extended)).write(extensionPacket);
            check(PlayerFrame.read(extensionPacket.duplicate()).players.get(0).extension()[0]==7,"animation bytes follow pose through wire");
            int size=extensionPacket.writerIndex();extensionPacket.writerIndex(size-1);
            invalid(()->PlayerFrame.read(extensionPacket.duplicate()),"truncated animation accepted");extensionPacket.writerIndex(size);
            extensionPacket.setByte(42+89,255);
            invalid(()->PlayerFrame.read(extensionPacket.duplicate()),"oversized wire animation accepted");
        } finally { extensionPacket.release(); }
        Object connection=new Object();PlayerFrameCache cache=new PlayerFrameCache();
        cache.reset(connection,7,0,VIEWER);
        check(cache.accept(connection,frame(7,1,0,512),100),"matching frame accepted");
        check(!cache.accept(connection,frame(7,1,0,512),200),"duplicate sequence rejected");
        check(!cache.accept(new Object(),frame(7,2,0,512),200),"wrong connection rejected");
        check(!cache.accept(connection,frame(8,2,0,512),200),"old subscription rejected");
        check(!cache.accept(connection,frame(7,2,1,512),200),"wrong dimension rejected");
        cache.expire(1000000101L);check(cache.players().isEmpty() && cache.range()==0,"stale frame expires");
        check(!cache.accept(connection,frame(7,1,0,512),1000000200L),"expired old sequence can't revive players");
        check(cache.accept(connection,frame(7,2,0,0),1000000200L) && cache.players().isEmpty(),"server disable clears actors");
        cache.reset(connection,8,1,VIEWER);check(cache.players().isEmpty(),"world reset clears actors");
        check(!cache.accept(connection,frame(7,3,0,512),1000000300L),"previous world packet rejected");
        for(int version:new int[]{0,PlayerFrame.VERSION,PlayerFrame.VERSION+1}) for(int range:new int[]{0,512,1024,1025,65535}) {
            out=Unpooled.buffer();
            try {
                out.writeByte(version).writeLong(1).writeShort(range).writeByte(1);
                PlayerRelay.Settings settings=new PlayerRelay.Settings();settings.fromBytes(out);
                check(settings.valid==(version==PlayerFrame.VERSION && range<=1024),"subscription validates version and radius");
            } finally { out.release(); }
        }
        try {
            net.minecraft.nbt.NBTTagCompound tag=new net.minecraft.nbt.NBTTagCompound();tag.setString("name","Fixture");
            net.minecraft.nbt.NBTTagList equipment=new net.minecraft.nbt.NBTTagList();
            for(int slot=0;slot<5;slot++) { net.minecraft.nbt.NBTTagCompound item=new net.minecraft.nbt.NBTTagCompound();item.setByte("slot",(byte)slot);equipment.appendTag(item); }
            tag.setTag("equipment",equipment);tag.setString("textures","signed-texture");tag.setString("signature","signature");
            byte[] raw=PlayerAppearance.encode(tag);
            PlayerAppearance appearance=new PlayerAppearance(7,VIEWER,0,TARGET,2,1,raw);
            raw[0]=0;
            check("Fixture".equals(appearance.decode().getString("name")),"appearance bytes are immutable");
            PlayerAppearance.Decoded decodedAppearance=appearance.appearance();
            check(decodedAppearance.profile.getId().equals(TARGET) && decodedAppearance.profile.getName().equals("Fixture"),"appearance recreates canonical profile");
            check(decodedAppearance.profile.getProperties().get("textures").iterator().next().getSignature().equals("signature"),"signed skin metadata preserved");
            for(int slot=0;slot<5;slot++) check(decodedAppearance.equipment(slot)==null,"empty equipment slots remain empty");
            ByteBuf encoded=Unpooled.buffer();
            try {
                appearance.write(encoded);
                check(encoded.readableBytes()==appearance.bytes(),"appearance envelope byte accounting");
                PlayerAppearance decoded=PlayerAppearance.read(encoded.duplicate());
                check(decoded.player.equals(TARGET) && decoded.revision==1 && "Fixture".equals(decoded.decode().getString("name")),"appearance wire round trip");
                final ByteBuf packet=encoded;
                for(int length=0;length<encoded.readableBytes();length++) {
                    final int n=length;invalid(()->PlayerAppearance.read(packet.slice(0,n)),"truncated appearance accepted");
                }
                encoded.writeByte(0);invalid(()->PlayerAppearance.read(packet.duplicate()),"trailing appearance bytes accepted");
            } finally { encoded.release(); }
            net.minecraft.nbt.NBTTagCompound huge=new net.minecraft.nbt.NBTTagCompound();huge.setByteArray("huge",new byte[PlayerAppearance.MAX_BYTES]);
            try { PlayerAppearance.encode(huge);throw new AssertionError("oversized appearance encoded"); }
            catch(java.io.IOException expected) { checks++; }
            PlayerFrame.Pose visible=new PlayerFrame.Pose(TARGET,2,10,100,-200,0,0,0,0,0,0,10,0,1);
            PlayerFrame withAppearance=new PlayerFrame(7,3,VIEWER,0,512,Collections.singletonList(visible));
            cache.reset(connection,7,0,VIEWER);
            check(cache.accept(connection,appearance),"appearance may arrive before frame");
            check(cache.accept(connection,withAppearance,100) && cache.appearance(visible)!=null,"matching revision authorises model appearance");
            check(!cache.accept(new Object(),appearance),"appearance from old connection rejected");
            check(!cache.accept(connection,new PlayerAppearance(8,VIEWER,0,TARGET,2,1,PlayerAppearance.encode(tag))),"appearance from old subscription rejected");
            check(cache.accept(connection,new PlayerAppearance(7,VIEWER,0,TARGET,2,2,PlayerAppearance.encode(tag))) && cache.appearance(visible).revision==1,"interpolated old pose retains its own appearance revision");
            check(!cache.accept(connection,appearance),"old appearance cannot overwrite newer revision");
            cache.reset(connection,8,1,VIEWER);check(cache.appearance(visible)==null,"world reset clears appearances");
        } catch(java.io.IOException error) { throw new AssertionError(error); }
        // The real selector needs only a loader URL inventory and Forge side. Preserve
        // those globals so this fixture does not bootstrap a Minecraft game or transform it.
        net.minecraft.launchwrapper.LaunchClassLoader previousLoader=net.minecraft.launchwrapper.Launch.classLoader;
        try(net.minecraft.launchwrapper.LaunchClassLoader fixtureLoader=new net.minecraft.launchwrapper.LaunchClassLoader(new java.net.URL[0])) {
            java.lang.reflect.Field side=cpw.mods.fml.relauncher.FMLLaunchHandler.class.getDeclaredField("side");side.setAccessible(true);
            Object previousSide=side.get(null);net.minecraft.launchwrapper.Launch.classLoader=fixtureLoader;
            try {
                for(cpw.mods.fml.relauncher.Side value:cpw.mods.fml.relauncher.Side.values()) {
                    side.set(null,value);
                    java.util.List<String> early=new com.seibel.distanthorizons.DistantHorizonsTweaker().getMixins(Collections.emptySet());
                    check(Collections.frequency(early,"MixinPlayerUseState")==1,"actual early loader selects the common player accessor exactly once on "+value);
                }
            } finally { side.set(null,previousSide);net.minecraft.launchwrapper.Launch.classLoader=previousLoader; }
        } catch(java.io.IOException | ReflectiveOperationException failure) { throw new AssertionError("Early mixin selector fixture failed",failure); }
        List<PlayerFrame.Pose> population=new ArrayList<>();Map<UUID,Long> known=new HashMap<>(),sent=new HashMap<>();
        for(int i=0;i<64;i++) population.add(new PlayerFrame.Pose(new UUID(100,i),i,0,0,0,0,0,0,0,0,0,0,0,1));
        for(long tick=4;tick<=256;tick+=4) {
            int delivered=0;
            for(PlayerFrame.Pose candidate:AppearanceDeliveryOrder.ordered(population,known,sent)) {
                if(known.getOrDefault(candidate.uuid,0L)==candidate.appearanceRevision && tick-sent.get(candidate.uuid)<20) continue;
                known.put(candidate.uuid,candidate.appearanceRevision);sent.put(candidate.uuid,tick);
                if(++delivered==4) break;
            }
            if(tick==64) check(known.size()==64,"initial appearances cannot starve behind periodic repairs");
        }
        for(PlayerFrame.Pose candidate:population) check(sent.get(candidate.uuid)>=196,"repairs rotate across all recipients");
        invalid(()->new PlayerFrame(1,1,VIEWER,0,0,PlayerFrame.OWNED,2,Collections.emptyList()),"ownership without compatible policy accepted");
        invalid(()->new PlayerFrame(1,1,VIEWER,0,0,8,2,Collections.emptyList()),"unknown ownership capabilities accepted");
        invalid(()->new PlayerFrame(1,1,VIEWER,0,0,1,0,Collections.emptyList()),"zero update interval accepted");
        cache.reset(connection,1,0,VIEWER);
        check(cache.accept(connection,new PlayerFrame(1,1,VIEWER,0,0,PlayerFrame.SAFE,2,Collections.emptyList()),0)
            && cache.safe() && !cache.ownsLegacy(),"policy probe cannot claim legacy presentation");
        check(cache.accept(connection,new PlayerFrame(1,2,VIEWER,0,0,PlayerFrame.SAFE|PlayerFrame.OWNED,2,Collections.emptyList()),1)
            && cache.ownsLegacy() && cache.players().isEmpty(),"server ownership acknowledgement works with player toggle off");
        for(int server=0;server<2;server++) {
            PlayerFrame policy=new PlayerFrame(1,3+server,VIEWER,0,0,PlayerFrame.SAFE|PlayerFrame.OWNED|(server==1?PlayerFrame.FMUR:0),2,Collections.emptyList());
            ByteBuf bytes=Unpooled.buffer();
            try { policy.write(bytes);check(cache.accept(connection,PlayerFrame.read(bytes),2+server),"FMUR server policy round trips through the actual wire/cache"); }
            finally { bytes.release(); }
            for(int sampled=0;sampled<2;sampled++) for(int client=0;client<2;client++)
                check(cache.fmurEnabled(sampled==1?PlayerFrame.POSE_FMUR:0,client==1)==(server==1 && sampled==1 && client==1),
                    "FMUR server revocation and sampled/client policy combine without enabling an incompatible pose");
        }
        PlayerFrame.Pose fmurPose=new PlayerFrame.Pose(TARGET,2,0,65,0,0,0,0,0,0,255,100,0,1,0,0,new byte[]{1});
        ByteBuf policyPacket=Unpooled.buffer();
        try {
            new PlayerFrame(1,10,VIEWER,0,512,PlayerFrame.SAFE|PlayerFrame.OWNED|PlayerFrame.FMUR,2,Collections.singletonList(fmurPose)).write(policyPacket);
            PlayerFrame restored=PlayerFrame.read(policyPacket);
            check(restored.players.get(0).flags==255 && restored.players.get(0).extension()[0]==1,"FMUR pose bit preserves all original action bits on the wire");
        } finally { policyPacket.release(); }
        try(java.io.InputStream input=PlayerProtocolChecks.class.getResourceAsStream("/net/minecraft/entity/player/EntityPlayer.class")) {
            org.spongepowered.asm.lib.tree.ClassNode player=new org.spongepowered.asm.lib.tree.ClassNode();
            new org.spongepowered.asm.lib.ClassReader(input).accept(player,0);
            boolean fieldFound=false;
            for(org.spongepowered.asm.lib.tree.FieldNode field:player.fields) if(field.name.equals("itemInUseCount")) {
                fieldFound=true;
                if(field.visibleAnnotations!=null) for(org.spongepowered.asm.lib.tree.AnnotationNode annotation:field.visibleAnnotations)
                    check(!annotation.desc.endsWith("/SideOnly;"),"server accessor field survives side stripping");
            }
            check(fieldFound,"server item-use accessor has a real Minecraft field");
        } catch(java.io.IOException error) { throw new AssertionError(error); }
        // Inspect bytecode without initializing Forge networking or optional mods.
        for(String name:new String[]{"PlayerRelay","PlayerRelay$SettingsHandler","PlayerRelay$FrameHandler","PlayerVisibility","PlayerFrame","PlayerFrameCache","PlayerAppearance","VehicleFrame","VehicleFrameCache","VehicleRelay","VehicleRelay$SettingsHandler","VehicleRelay$FrameHandler","McheliSource"}) {
            String path="/com/seibel/distanthorizons/forge17/actors/"+name+".class";
            try(java.io.InputStream input=PlayerProtocolChecks.class.getResourceAsStream(path)) {
                if(input==null) throw new AssertionError("Missing server class "+name);
                java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();byte[] buffer=new byte[4096];int n;
                while((n=input.read(buffer))!=-1) bytes.write(buffer,0,n);
                String pool=new String(bytes.toByteArray(),java.nio.charset.StandardCharsets.ISO_8859_1);
                check(!pool.contains("net/minecraft/client/") && !pool.contains("org/lwjgl/"),"server actor boundary: "+name);
            } catch(java.io.IOException error) { throw new AssertionError(error); }
        }
        return checks;
    }
}
