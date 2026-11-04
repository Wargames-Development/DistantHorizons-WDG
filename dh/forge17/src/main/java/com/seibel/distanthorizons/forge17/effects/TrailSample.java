package com.seibel.distanthorizons.forge17.effects;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.regex.Pattern;
import io.netty.buffer.ByteBuf;

/** One observed emission segment, never a projectile simulation or predicted future path. */
public final class TrailSample
{
    public static final int MCH = 1, HBM = 2, FLANS = 3;
    private static final Pattern STYLE=Pattern.compile("[a-zA-Z0-9_.:-]{1,64}");
    public int family, entityId, slot, count;
    public UUID uuid;
    public String style;
    public long tick;
    public double x0, y0, z0, x, y, z;
    public float scale = 1, mx, my, mz;
    public double ox, oy, oz; // Server-only nozzle offsets; the wire carries resolved world positions.

    public boolean valid()
    {
        return family >= MCH && family <= FLANS && uuid != null && style != null
            && STYLE.matcher(style).matches() && slot >= 0 && slot < 8 && count >= 1 && count <= 32
            && finitePosition(x0, y0, z0) && finitePosition(x, y, z)
            && (x-x0)*(x-x0)+(y-y0)*(y-y0)+(z-z0)*(z-z0)<=512D*512D
            && Float.isFinite(scale) && scale > 0 && scale <= 256
            && Float.isFinite(mx) && Float.isFinite(my) && Float.isFinite(mz)
            && Math.abs(mx) <= 64 && Math.abs(my) <= 64 && Math.abs(mz) <= 64;
    }

    private static boolean finitePosition(double x, double y, double z)
    {
        return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)
            && Math.abs(x) <= 30000000 && Math.abs(y) <= 65536 && Math.abs(z) <= 30000000;
    }

    public TrailSample copy()
    {
        TrailSample n = new TrailSample();
        n.family=family; n.entityId=entityId; n.slot=slot; n.count=count; n.uuid=uuid; n.style=style; n.tick=tick;
        n.x0=x0; n.y0=y0; n.z0=z0; n.x=x; n.y=y; n.z=z; n.scale=scale; n.mx=mx; n.my=my; n.mz=mz;
        return n;
    }

    public void write(ByteBuf b)
    {
        b.writeByte(family).writeInt(entityId).writeByte(slot).writeByte(count);
        b.writeLong(uuid.getMostSignificantBits()).writeLong(uuid.getLeastSignificantBits()).writeLong(tick);
        byte[] name=style.getBytes(StandardCharsets.US_ASCII);
        b.writeByte(name.length).writeBytes(name);
        b.writeDouble(x0).writeDouble(y0).writeDouble(z0).writeDouble(x).writeDouble(y).writeDouble(z);
        b.writeFloat(scale).writeFloat(mx).writeFloat(my).writeFloat(mz);
    }

    public static TrailSample read(ByteBuf b)
    {
        TrailSample n=new TrailSample();
        n.family=b.readUnsignedByte(); n.entityId=b.readInt(); n.slot=b.readUnsignedByte(); n.count=b.readUnsignedByte();
        n.uuid=new UUID(b.readLong(), b.readLong()); n.tick=b.readLong();
        int length=b.readUnsignedByte();
        if (length < 1 || length > 64) throw new IllegalArgumentException("Invalid trail style length");
        byte[] name=new byte[length]; b.readBytes(name); n.style=new String(name, StandardCharsets.US_ASCII);
        n.x0=b.readDouble(); n.y0=b.readDouble(); n.z0=b.readDouble(); n.x=b.readDouble(); n.y=b.readDouble(); n.z=b.readDouble();
        n.scale=b.readFloat(); n.mx=b.readFloat(); n.my=b.readFloat(); n.mz=b.readFloat();
        if (!n.valid()) throw new IllegalArgumentException("Invalid visual trail sample");
        return n;
    }
}
