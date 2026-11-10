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
            out.setByte(0,99);invalid(()->PlayerFrame.read(packet.duplicate()),"unknown protocol accepted");out.setByte(0,1);
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
        for(int i=0;i<PlayerFrame.MAX_PLAYERS;i++) maximum.add(pose(new UUID(100,i),i,0));
        out=Unpooled.buffer();
        try {
            new PlayerFrame(1,1,VIEWER,0,1024,maximum).write(out);
            check(out.readableBytes()<=PlayerFrame.MAX_BYTES,"maximum player frame byte bound");
            check(PlayerFrame.read(out).players.size()==PlayerFrame.MAX_PLAYERS,"maximum frame round trip");
        } finally { out.release(); }
        maximum.add(pose(new UUID(200,1),200,0));
        invalid(()->new PlayerFrame(1,1,VIEWER,0,512,maximum),"oversized actor set accepted");
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
        for(int version:new int[]{0,1,2}) for(int range:new int[]{0,512,1024,1025,65535}) {
            out=Unpooled.buffer();
            try {
                out.writeByte(version).writeLong(1).writeShort(range);
                PlayerRelay.Settings settings=new PlayerRelay.Settings();settings.fromBytes(out);
                check(settings.valid==(version==1 && range<=1024),"subscription validates version and radius");
            } finally { out.release(); }
        }
        // Inspect bytecode without initializing Forge networking or optional mods.
        for(String name:new String[]{"PlayerRelay","PlayerRelay$SettingsHandler","PlayerRelay$FrameHandler","PlayerVisibility","PlayerFrame","PlayerFrameCache"}) {
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
