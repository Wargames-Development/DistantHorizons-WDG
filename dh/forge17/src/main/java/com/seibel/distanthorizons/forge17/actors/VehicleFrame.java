package com.seibel.distanthorizons.forge17.actors;

import io.netty.buffer.ByteBuf;
import java.util.*;

/** Recipient-scoped vehicle visual payloads, independent of radar/gameplay packets. */
public final class VehicleFrame {
    public static final int VERSION=1,MAX_VEHICLES=32,MAX_RANGE=4096,MAX_PAYLOAD=512,MAX_BYTES=18432;
    public final long subscription,sequence;
    public final UUID viewer;
    public final int dimension,range,interval;
    public final boolean sourceReady;
    public final List<Entry> entries;
    public VehicleFrame(long subscription,long sequence,UUID viewer,int dimension,int range,int interval,boolean sourceReady,List<Entry> entries) {
        if(subscription==0 || sequence<0 || viewer==null || range<0 || range>MAX_RANGE || interval<1 || interval>20
                || entries.size()>MAX_VEHICLES || ((!sourceReady || range==0) && !entries.isEmpty())) throw new IllegalArgumentException("Invalid vehicle frame");
        Set<UUID> uuids=new HashSet<>();Set<Integer> ids=new HashSet<>();
        for(Entry entry:entries) if(entry==null || !uuids.add(entry.uuid) || !ids.add(entry.entityId)) throw new IllegalArgumentException("Duplicate vehicle identity");
        this.subscription=subscription;this.sequence=sequence;this.viewer=viewer;this.dimension=dimension;this.range=range;this.interval=interval;this.sourceReady=sourceReady;
        this.entries=Collections.unmodifiableList(new ArrayList<>(entries));
    }
    public static int effectiveRange(int requested,int maximum,boolean allowed) {
        return !allowed || requested<0 || requested>MAX_RANGE?0:Math.min(requested,Math.max(0,Math.min(MAX_RANGE,maximum)));
    }
    public void write(ByteBuf out) {
        out.writeByte(VERSION).writeLong(subscription).writeLong(sequence);uuid(out,viewer);
        out.writeInt(dimension).writeShort(range).writeByte(interval).writeByte(sourceReady?1:0).writeByte(entries.size());
        for(Entry entry:entries) {
            uuid(out,entry.uuid);out.writeInt(entry.entityId).writeDouble(entry.x).writeDouble(entry.y).writeDouble(entry.z);
            out.writeShort(entry.payload.length).writeBytes(entry.payload);
        }
    }
    public static VehicleFrame read(ByteBuf in) {
        if(in.readableBytes()<42 || in.readableBytes()>MAX_BYTES || in.readUnsignedByte()!=VERSION) throw new IllegalArgumentException("Invalid vehicle frame envelope");
        long token=in.readLong(),sequence=in.readLong();UUID viewer=uuid(in);
        int dimension=in.readInt(),range=in.readUnsignedShort(),interval=in.readUnsignedByte(),flags=in.readUnsignedByte(),count=in.readUnsignedByte();
        if(flags>1 || count>MAX_VEHICLES) throw new IllegalArgumentException("Invalid vehicle frame flags/count");
        List<Entry> entries=new ArrayList<>(count);
        for(int i=0;i<count;i++) {
            UUID id=uuid(in);int entity=in.readInt();double x=in.readDouble(),y=in.readDouble(),z=in.readDouble();int length=in.readUnsignedShort();
            if(length<1 || length>MAX_PAYLOAD || length>in.readableBytes()) throw new IllegalArgumentException("Invalid vehicle source payload");
            byte[] bytes=new byte[length];in.readBytes(bytes);entries.add(new Entry(id,entity,x,y,z,bytes));
        }
        if(in.isReadable()) throw new IllegalArgumentException("Trailing vehicle data");
        return new VehicleFrame(token,sequence,viewer,dimension,range,interval,flags!=0,entries);
    }
    private static void uuid(ByteBuf out,UUID id) { out.writeLong(id.getMostSignificantBits()).writeLong(id.getLeastSignificantBits()); }
    private static UUID uuid(ByteBuf in) { return new UUID(in.readLong(),in.readLong()); }
    public static final class Entry {
        public final UUID uuid;public final int entityId;public final double x,y,z;
        private final byte[] payload;
        public Entry(UUID uuid,int entityId,double x,double y,double z,byte[] payload) {
            if(uuid==null || entityId<0 || !Double.isFinite(x+y+z) || Math.abs(x)>30000000 || Math.abs(z)>30000000 || Math.abs(y)>65536
                    || payload==null || payload.length<1 || payload.length>MAX_PAYLOAD) throw new IllegalArgumentException("Invalid vehicle entry");
            this.uuid=uuid;this.entityId=entityId;this.x=x;this.y=y;this.z=z;this.payload=payload.clone();
        }
        public byte[] payload() { return payload.clone(); }
    }
}
