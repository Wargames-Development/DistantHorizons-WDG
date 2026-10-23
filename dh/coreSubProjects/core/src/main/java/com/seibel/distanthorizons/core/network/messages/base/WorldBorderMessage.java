package com.seibel.distanthorizons.core.network.messages.base;
import com.seibel.distanthorizons.core.network.messages.AbstractNetworkMessage;
import com.seibel.distanthorizons.core.generation.WorldBorderBounds;
import io.netty.buffer.ByteBuf;
public final class WorldBorderMessage extends AbstractNetworkMessage {
    public String dimension; public WorldBorderBounds bounds;
    public WorldBorderMessage() { }
    public WorldBorderMessage(String dimension,WorldBorderBounds bounds) {this.dimension=dimension;this.bounds=bounds;}
    @Override public void encode(ByteBuf out) {writeString(dimension,out);out.writeBoolean(bounds!=null);
        if(bounds!=null){out.writeDouble(bounds.minX);out.writeDouble(bounds.minZ);out.writeDouble(bounds.maxX);out.writeDouble(bounds.maxZ);}}
    @Override public void decode(ByteBuf in) {dimension=readString(in);if(dimension.length()>150)throw new IllegalArgumentException("Invalid dimension");
        bounds=in.readBoolean()?new WorldBorderBounds(in.readDouble(),in.readDouble(),in.readDouble(),in.readDouble()):null;}
}
