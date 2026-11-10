package com.seibel.distanthorizons.forge17.actors;

import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/** Immutable, bounded authoritative poses. Actors are presentation data, never hitboxes. */
public final class PlayerFrame {
    public static final int VERSION=1, MAX_PLAYERS=64, MAX_RANGE=1024, MAX_BYTES=8192;
    public final long subscription, sequence;
    public final UUID viewer;
    public final int dimension, range;
    public final List<Pose> players;

    public PlayerFrame(long subscription,long sequence,UUID viewer,int dimension,int range,List<Pose> players) {
        if (subscription==0 || sequence<0 || viewer==null || range<0 || range>MAX_RANGE || players.size()>MAX_PLAYERS
                || (range==0 && !players.isEmpty())) throw new IllegalArgumentException("Invalid player frame");
        HashSet<UUID> ids=new HashSet<UUID>();
        HashSet<Integer> entityIds=new HashSet<Integer>();
        for(Pose pose:players) if(pose==null || pose.uuid.equals(viewer) || !ids.add(pose.uuid) || !entityIds.add(pose.entityId))
            throw new IllegalArgumentException("Duplicate/self player");
        this.subscription=subscription;this.sequence=sequence;this.viewer=viewer;this.dimension=dimension;this.range=range;
        this.players=Collections.unmodifiableList(new ArrayList<Pose>(players));
    }

    public static int effectiveRange(int requested,int serverMaximum,boolean allowed) {
        if(!allowed || requested<0 || requested>MAX_RANGE) return 0;
        return Math.min(requested,Math.max(0,Math.min(MAX_RANGE,serverMaximum)));
    }

    public void write(ByteBuf out) {
        out.writeByte(VERSION).writeLong(subscription).writeLong(sequence);
        uuid(out,viewer);out.writeInt(dimension).writeShort(range).writeByte(players.size());
        for(Pose pose:players) pose.write(out);
    }
    public static PlayerFrame read(ByteBuf in) {
        if(in.readableBytes()<40 || in.readableBytes()>MAX_BYTES || in.readUnsignedByte()!=VERSION)
            throw new IllegalArgumentException("Invalid player frame envelope");
        long subscription=in.readLong(), sequence=in.readLong();UUID viewer=uuid(in);
        int dimension=in.readInt(),range=in.readUnsignedShort(),count=in.readUnsignedByte();
        if(count>MAX_PLAYERS) throw new IllegalArgumentException("Too many players");
        List<Pose> players=new ArrayList<Pose>(count);
        for(int i=0;i<count;i++) players.add(Pose.read(in));
        if(in.isReadable()) throw new IllegalArgumentException("Trailing frame data");
        return new PlayerFrame(subscription,sequence,viewer,dimension,range,players);
    }
    private static void uuid(ByteBuf out,UUID id) { out.writeLong(id.getMostSignificantBits()).writeLong(id.getLeastSignificantBits()); }
    private static UUID uuid(ByteBuf in) { return new UUID(in.readLong(),in.readLong()); }

    public static final class Pose {
        public final UUID uuid;
        public final int entityId,flags,age,brightness;
        public final double x,y,z;
        public final float yaw,pitch,headYaw,bodyYaw,swing;
        public Pose(UUID uuid,int entityId,double x,double y,double z,float yaw,float pitch,float headYaw,float bodyYaw,
                    float swing,int flags,int age,int brightness) {
            if(uuid==null || entityId<0 || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                    || Math.abs(x)>30000000 || Math.abs(z)>30000000 || Math.abs(y)>65536
                    || !Float.isFinite(yaw) || !Float.isFinite(pitch) || !Float.isFinite(headYaw) || !Float.isFinite(bodyYaw)
                    || !Float.isFinite(swing) || swing<0 || swing>1 || (flags & ~127)!=0 || age<0 || (brightness & ~0x00F000F0)!=0)
                throw new IllegalArgumentException("Invalid player pose");
            this.uuid=uuid;this.entityId=entityId;this.x=x;this.y=y;this.z=z;this.yaw=yaw;this.pitch=pitch;
            this.headYaw=headYaw;this.bodyYaw=bodyYaw;this.swing=swing;this.flags=flags;this.age=age;this.brightness=brightness;
        }
        private void write(ByteBuf out) {
            uuid(out,uuid);out.writeInt(entityId).writeDouble(x).writeDouble(y).writeDouble(z);
            out.writeFloat(yaw).writeFloat(pitch).writeFloat(headYaw).writeFloat(bodyYaw).writeFloat(swing);
            out.writeByte(flags).writeInt(age).writeInt(brightness);
        }
        private static Pose read(ByteBuf in) {
            return new Pose(uuid(in),in.readInt(),in.readDouble(),in.readDouble(),in.readDouble(),
                in.readFloat(),in.readFloat(),in.readFloat(),in.readFloat(),in.readFloat(),in.readUnsignedByte(),in.readInt(),in.readInt());
        }
    }
}
