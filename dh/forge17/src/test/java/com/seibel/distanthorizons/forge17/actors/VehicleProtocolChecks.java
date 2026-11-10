package com.seibel.distanthorizons.forge17.actors;

import io.netty.buffer.*;
import java.util.*;

public final class VehicleProtocolChecks {
    private static int checks;
    private static final UUID VIEWER=new UUID(1,2),VEHICLE=new UUID(3,4);
    private static void check(boolean value,String message) { if(!value) throw new AssertionError(message);checks++; }
    private static void invalid(Runnable action) { try { action.run();throw new AssertionError("invalid vehicle data accepted"); }catch(IllegalArgumentException | IndexOutOfBoundsException expected) { checks++; } }
    private static VehicleFrame.Entry entry(UUID uuid,int id,byte[] bytes) { return new VehicleFrame.Entry(uuid,id,10,200,30,bytes); }
    private static VehicleFrame frame(long token,long sequence,int dim,int range) {
        return new VehicleFrame(token,sequence,VIEWER,dim,range,2,true,range==0?Collections.emptyList():Collections.singletonList(entry(VEHICLE,7,new byte[]{1,2,3})));
    }
    public static int run() {
        checks=0;
        for(int requested:new int[]{0,1,100,512,750,751,4096}) for(int maximum:new int[]{0,100,750,4096,100000}) {
            check(VehicleFrame.effectiveRange(requested,maximum,true)<=Math.min(requested,Math.min(VehicleFrame.MAX_RANGE,maximum)),"client cannot exceed server vehicle range");
            check(VehicleFrame.effectiveRange(requested,maximum,false)==0,"server vehicle support off blocks delivery");
        }
        check(VehicleFrame.effectiveRange(4096,750,true)==750,"default vehicle ceiling is server authoritative");
        check(VehicleFrame.effectiveRange(-1,750,true)==0 && VehicleFrame.effectiveRange(4097,750,true)==0,"invalid vehicle request fails closed");
        byte[] bytes=new byte[VehicleFrame.MAX_PAYLOAD];bytes[0]=5;VehicleFrame.Entry immutable=entry(VEHICLE,7,bytes);bytes[0]=9;
        check(immutable.payload()[0]==5,"vehicle source payload is copied");immutable.payload()[0]=10;check(immutable.payload()[0]==5,"vehicle payload accessor is immutable");
        invalid(()->entry(VEHICLE,7,new byte[VehicleFrame.MAX_PAYLOAD+1]));invalid(()->entry(VEHICLE,7,new byte[0]));
        invalid(()->new VehicleFrame.Entry(VEHICLE,7,Double.NaN,0,0,new byte[]{1}));
        List<VehicleFrame.Entry> maximum=new ArrayList<>();for(int i=0;i<VehicleFrame.MAX_VEHICLES;i++) maximum.add(entry(new UUID(9,i),i,new byte[512]));
        ByteBuf out=Unpooled.buffer();
        try {
            new VehicleFrame(1,1,VIEWER,0,750,2,true,maximum).write(out);
            check(out.readableBytes()<=VehicleFrame.MAX_BYTES,"maximum vehicle payload set remains bounded");
            check(VehicleFrame.read(out.duplicate()).entries.size()==VehicleFrame.MAX_VEHICLES,"maximum vehicle frame round trip");
        } finally { out.release(); }
        List<VehicleFrame.Entry> heavy=new ArrayList<>();
        for(int i=0;i<6;i++) heavy.add(entry(new UUID(10,i),i,new byte[VehicleFrame.MAX_PAYLOAD]));
        invalid(()->new VehicleFrame(1,1,VIEWER,0,750,2,true,heavy));
        heavy.remove(heavy.size()-1);ByteBuf heavyPacket=Unpooled.buffer();
        try {
            new VehicleFrame(1,1,VIEWER,0,750,2,true,heavy).write(heavyPacket);
            check(heavyPacket.readableBytes()<=VehicleFrame.MAX_BYTES && VehicleFrame.read(heavyPacket).entries.size()==5,"large animated models fit a bounded whole-entry frame");
        } finally { heavyPacket.release(); }
        maximum.add(entry(new UUID(8,1),99,new byte[]{1}));invalid(()->new VehicleFrame(1,1,VIEWER,0,750,2,true,maximum));
        invalid(()->new VehicleFrame(1,1,VIEWER,0,0,2,true,Collections.singletonList(immutable)));
        invalid(()->new VehicleFrame(1,1,VIEWER,0,750,2,false,Collections.singletonList(immutable)));
        invalid(()->new VehicleFrame(1,1,VIEWER,0,750,2,true,Arrays.asList(immutable,immutable)));
        ByteBuf packet=Unpooled.buffer();
        try {
            frame(7,1,0,750).write(packet);int length=packet.readableBytes();
            for(int n=0;n<length;n++) { final ByteBuf truncated=packet.slice(0,n);invalid(()->VehicleFrame.read(truncated)); }
            packet.writeByte(0);invalid(()->VehicleFrame.read(packet.duplicate()));packet.writerIndex(length);
            packet.setShort(86,VehicleFrame.MAX_PAYLOAD+1);invalid(()->VehicleFrame.read(packet.duplicate()));packet.setShort(86,3);
            packet.setByte(0,255);invalid(()->VehicleFrame.read(packet.duplicate()));
        } finally { packet.release(); }
        for(int range:new int[]{0,750,4096,4097,65535}) for(int version:new int[]{0,VehicleFrame.VERSION,255}) {
            ByteBuf settings=Unpooled.buffer();
            try {
                settings.writeByte(version).writeLong(1).writeShort(range);
                VehicleRelay.Settings decoded=new VehicleRelay.Settings();decoded.fromBytes(settings);
                check(decoded.valid==(version==VehicleFrame.VERSION && range<=VehicleFrame.MAX_RANGE),"vehicle settings validate version/range");
            } finally { settings.release(); }
        }
        Object connection=new Object();VehicleFrameCache cache=new VehicleFrameCache();cache.reset(connection,7,VIEWER,0);
        check(cache.accept(connection,frame(7,1,0,750),100),"vehicle frame accepts current connection/session");
        check(!cache.accept(new Object(),frame(7,2,0,750),100),"vehicle rejects old connection");
        check(!cache.accept(connection,frame(8,2,0,750),100),"vehicle rejects old subscription");
        check(!cache.accept(connection,frame(7,2,1,750),100),"vehicle rejects wrong dimension");
        check(!cache.accept(connection,frame(7,1,0,750),100),"vehicle rejects duplicate sequence");
        check(!cache.accept(connection,new VehicleFrame(7,2,new UUID(5,6),0,750,2,true,Collections.emptyList()),100),"vehicle rejects wrong viewer");
        check(cache.accept(connection,frame(7,2,0,0),200) && cache.frame().entries.isEmpty(),"full empty frame removes distant vehicles");
        check(cache.accept(connection,frame(7,3,0,750),100),"vehicle replacement frame accepted");cache.expire(1000000101L);
        check(cache.frame()==null,"queued/stalled vehicle frame expires from original network receipt");
        cache.reset(connection,8,VIEWER,1);check(cache.frame()==null && !cache.accept(connection,frame(7,4,0,750),200),"dimension/session reset rejects all old vehicle data");
        return checks;
    }
}
