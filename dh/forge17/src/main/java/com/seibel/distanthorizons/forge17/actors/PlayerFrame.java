package com.seibel.distanthorizons.forge17.actors;

import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/** Immutable, bounded authoritative poses. Actors are presentation data, never hitboxes. */
public final class PlayerFrame {
    public static final int VERSION=4, MAX_PLAYERS=64, MAX_RANGE=1024, MAX_BYTES=12288, MAX_EXTENSION_BYTES=48;
    public final long subscription, sequence;
    public final UUID viewer;
    public final int dimension, range,capabilities,interval;
    public static final int SAFE=1,OWNED=2;
    public final List<Pose> players;

    public PlayerFrame(long subscription,long sequence,UUID viewer,int dimension,int range,List<Pose> players) {
        this(subscription,sequence,viewer,dimension,range,SAFE|OWNED,2,players);
    }
    public PlayerFrame(long subscription,long sequence,UUID viewer,int dimension,int range,int capabilities,int interval,List<Pose> players) {
        if (subscription==0 || sequence<0 || viewer==null || range<0 || range>MAX_RANGE || players.size()>MAX_PLAYERS
                || (range==0 && !players.isEmpty()) || (capabilities & ~3)!=0
                || ((capabilities & OWNED)!=0 && (capabilities & SAFE)==0) || interval<1 || interval>20) throw new IllegalArgumentException("Invalid player frame");
        HashSet<UUID> ids=new HashSet<UUID>();
        HashSet<Integer> entityIds=new HashSet<Integer>();
        for(Pose pose:players) if(pose==null || pose.uuid.equals(viewer) || !ids.add(pose.uuid) || !entityIds.add(pose.entityId))
            throw new IllegalArgumentException("Duplicate/self player");
        this.subscription=subscription;this.sequence=sequence;this.viewer=viewer;this.dimension=dimension;this.range=range;
        this.capabilities=capabilities;this.interval=interval;
        this.players=Collections.unmodifiableList(new ArrayList<Pose>(players));
    }

    public static int effectiveRange(int requested,int serverMaximum,boolean allowed) {
        if(!allowed || requested<0 || requested>MAX_RANGE) return 0;
        return Math.min(requested,Math.max(0,Math.min(MAX_RANGE,serverMaximum)));
    }

    public void write(ByteBuf out) {
        out.writeByte(VERSION).writeLong(subscription).writeLong(sequence);
        uuid(out,viewer);out.writeInt(dimension).writeShort(range).writeByte(capabilities).writeByte(interval).writeByte(players.size());
        for(Pose pose:players) pose.write(out);
    }
    public static PlayerFrame read(ByteBuf in) {
        if(in.readableBytes()<42 || in.readableBytes()>MAX_BYTES || in.readUnsignedByte()!=VERSION)
            throw new IllegalArgumentException("Invalid player frame envelope");
        long subscription=in.readLong(), sequence=in.readLong();UUID viewer=uuid(in);
        int dimension=in.readInt(),range=in.readUnsignedShort(),capabilities=in.readUnsignedByte(),interval=in.readUnsignedByte(),count=in.readUnsignedByte();
        if(count>MAX_PLAYERS) throw new IllegalArgumentException("Too many players");
        List<Pose> players=new ArrayList<Pose>(count);
        for(int i=0;i<count;i++) players.add(Pose.read(in));
        if(in.isReadable()) throw new IllegalArgumentException("Trailing frame data");
        return new PlayerFrame(subscription,sequence,viewer,dimension,range,capabilities,interval,players);
    }
    private static void uuid(ByteBuf out,UUID id) { out.writeLong(id.getMostSignificantBits()).writeLong(id.getLeastSignificantBits()); }
    private static UUID uuid(ByteBuf in) { return new UUID(in.readLong(),in.readLong()); }

    public static final class Pose {
        public final UUID uuid;
        public final long appearanceRevision;
        public final int entityId,flags,age,brightness,useTicks;
        public final float bedYaw;
        private final byte[] extension;
        public byte[] extension() { return extension.clone(); }
        public final double x,y,z;
        public final float yaw,pitch,headYaw,bodyYaw,swing;
        public Pose(UUID uuid,int entityId,double x,double y,double z,float yaw,float pitch,float headYaw,float bodyYaw,
                    float swing,int flags,int age,int brightness) {
            this(uuid,entityId,x,y,z,yaw,pitch,headYaw,bodyYaw,swing,flags,age,brightness,0);
        }
        public Pose(UUID uuid,int entityId,double x,double y,double z,float yaw,float pitch,float headYaw,float bodyYaw,
                    float swing,int flags,int age,int brightness,long appearanceRevision) {
            this(uuid,entityId,x,y,z,yaw,pitch,headYaw,bodyYaw,swing,flags,age,brightness,appearanceRevision,0,0);
        }
        public Pose(UUID uuid,int entityId,double x,double y,double z,float yaw,float pitch,float headYaw,float bodyYaw,
                    float swing,int flags,int age,int brightness,long appearanceRevision,int useTicks,float bedYaw) {
            this(uuid,entityId,x,y,z,yaw,pitch,headYaw,bodyYaw,swing,flags,age,brightness,appearanceRevision,useTicks,bedYaw,new byte[0]);
        }
        public Pose(UUID uuid,int entityId,double x,double y,double z,float yaw,float pitch,float headYaw,float bodyYaw,
                    float swing,int flags,int age,int brightness,long appearanceRevision,int useTicks,float bedYaw,byte[] extension) {
            if(extension==null || extension.length>MAX_EXTENSION_BYTES) throw new IllegalArgumentException("Invalid presentation extension");
            this.extension=extension.clone();
            if(uuid==null || entityId<0 || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                    || Math.abs(x)>30000000 || Math.abs(z)>30000000 || Math.abs(y)>65536
                    || !Float.isFinite(yaw) || !Float.isFinite(pitch) || !Float.isFinite(headYaw) || !Float.isFinite(bodyYaw)
                    || useTicks<0 || useTicks>1000000 || !Float.isFinite(bedYaw) || appearanceRevision<0 || !Float.isFinite(swing) || swing<0 || swing>1 || (flags & ~127)!=0 || age<0 || (brightness & ~0x00F000F0)!=0)
                throw new IllegalArgumentException("Invalid player pose");
            this.useTicks=useTicks;this.bedYaw=bedYaw;this.appearanceRevision=appearanceRevision;this.uuid=uuid;this.entityId=entityId;this.x=x;this.y=y;this.z=z;this.yaw=yaw;this.pitch=pitch;
            this.headYaw=headYaw;this.bodyYaw=bodyYaw;this.swing=swing;this.flags=flags;this.age=age;this.brightness=brightness;
        }
        private void write(ByteBuf out) {
            uuid(out,uuid);out.writeInt(entityId).writeDouble(x).writeDouble(y).writeDouble(z);
            out.writeFloat(yaw).writeFloat(pitch).writeFloat(headYaw).writeFloat(bodyYaw).writeFloat(swing);
            out.writeByte(flags).writeInt(age).writeInt(brightness).writeLong(appearanceRevision).writeInt(useTicks).writeFloat(bedYaw).writeByte(extension.length).writeBytes(extension);
        }
        private static byte[] readExtension(ByteBuf in) {
            int length=in.readUnsignedByte();
            if(length>MAX_EXTENSION_BYTES || length>in.readableBytes()) throw new IllegalArgumentException("Invalid presentation extension envelope");
            byte[] data=new byte[length];in.readBytes(data);return data;
        }
        private static Pose read(ByteBuf in) {
            return new Pose(uuid(in),in.readInt(),in.readDouble(),in.readDouble(),in.readDouble(),
                in.readFloat(),in.readFloat(),in.readFloat(),in.readFloat(),in.readFloat(),in.readUnsignedByte(),in.readInt(),in.readInt(),in.readLong(),in.readInt(),in.readFloat(),readExtension(in));
        }
    }
}
