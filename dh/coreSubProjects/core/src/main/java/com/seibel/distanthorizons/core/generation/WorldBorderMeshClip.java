package com.seibel.distanthorizons.core.generation;

import com.seibel.distanthorizons.core.dataObjects.render.bufferBuilding.BufferQuad;
import com.seibel.distanthorizons.core.enums.EDhDirection;

/** Clip block-aligned mesh faces without changing full-data/database records. */
public final class WorldBorderMeshClip {
    private WorldBorderMeshClip() { }
    public static boolean clip(BufferQuad q, WorldBorderBounds border, int originX, int originZ) {
        if(border==null)return true;
        int minX=(int)Math.ceil(border.minX-originX), maxX=(int)Math.floor(border.maxX-originX);
        int minZ=(int)Math.ceil(border.minZ-originZ), maxZ=(int)Math.floor(border.maxZ-originZ);
        int x=q.x,z=q.z,w=q.widthEastWest,h=q.widthNorthSouthOrHeight;
        EDhDirection d=q.direction;
        if(d==EDhDirection.UP || d==EDhDirection.DOWN) {
            int right=Math.min(x+w,maxX),bottom=Math.min(z+h,maxZ);
            x=Math.max(x,minX);z=Math.max(z,minZ);w=right-x;h=bottom-z;
        } else if(d==EDhDirection.NORTH || d==EDhDirection.SOUTH) {
            if(z<minZ || z>maxZ)return false;
            int right=Math.min(x+w,maxX);x=Math.max(x,minX);w=right-x;
        } else {
            if(x<minX || x>maxX)return false;
            int bottom=Math.min(z+w,maxZ);z=Math.max(z,minZ);w=bottom-z;
        }
        if(w<=0 || h<=0)return false;
        q.set((short)x,q.y,(short)z,(short)w,(short)h,q.color,q.textureTileId,
            q.irisBlockMaterialId,q.skyLight,q.blockLight,d);
        return true;
    }
}
