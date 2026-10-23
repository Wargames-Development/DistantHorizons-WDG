package com.seibel.distanthorizons.core.dataObjects.transformers;

import com.seibel.distanthorizons.api.enums.rendering.EDhApiBlockMaterial;
import com.seibel.distanthorizons.core.dataObjects.render.ColumnRenderSource;
import com.seibel.distanthorizons.core.enums.EDhDirection;
import com.seibel.distanthorizons.core.util.RenderDataPointUtil;

/** Render-only legacy water tint smoothing. Never reads the world or changes stored LOD data. */
public final class LegacyWaterColorBlender
{
    private static final int WIDTH = ColumnRenderSource.WIDTH;
    private static final long RGB_MASK = 0xFFFFFFL << 32;
    private LegacyWaterColorBlender() { }

    public static void blend(ColumnRenderSource center, ColumnRenderSource[] neighbors, int radius)
    {
        radius = Math.max(0, Math.min(3, radius));
        if (radius == 0) return;
        // Snapshot surface water only: submerged vegetation must not tint the surface.
        long[] surfaces = new long[WIDTH * WIDTH];
        int[] indices = new int[surfaces.length];
        for (int x = 0; x < WIDTH; x++)
            for (int z = 0; z < WIDTH; z++)
            {
                int column = x * WIDTH + z;
                indices[column] = -1;
                for (int y = 0; y < center.maxVerticalSliceCount; y++)
                {
                    long point = center.getDataPoint(x, z, y);
                    if (point == 0) break;
                    if (isWater(point))
                    {
                        surfaces[column] = point;
                        indices[column] = column * center.maxVerticalSliceCount + y;
                        break;
                    }
                }
            }
        for (int x = 0; x < WIDTH; x++)
            for (int z = 0; z < WIDTH; z++)
            {
                int column = x * WIDTH + z;
                long original = surfaces[column];
                if (indices[column] < 0) continue;
                int color = RenderDataPointUtil.getColor(original);
                int weight = radius + 1;
                int red = ((color >>> 16) & 255) * weight;
                int green = ((color >>> 8) & 255) * weight;
                int blue = (color & 255) * weight;
                for (EDhDirection direction : EDhDirection.CARDINAL_COMPASS)
                    for (int distance = 1; distance <= radius; distance++)
                    {
                        int sx = x, sz = z;
                        switch (direction)
                        {
                            case NORTH: sz -= distance; break;
                            case SOUTH: sz += distance; break;
                            case EAST: sx += distance; break;
                            case WEST: sx -= distance; break;
                            default: break;
                        }
                        long donor;
                        if (sx >= 0 && sx < WIDTH && sz >= 0 && sz < WIDTH)
                            donor = surfaces[sx * WIDTH + sz];
                        else
                        {
                            ColumnRenderSource neighbor = neighbors[direction.compassIndex];
                            if (neighbor == null || neighbor.getDataDetailLevel() != center.getDataDetailLevel()) break;
                            // Adjacency records contain one edge row. Extend that row for the
                            // bounded kernel, rather than loading entire neighboring sections.
                            sx = sx < 0 ? WIDTH - 1 : sx >= WIDTH ? 0 : sx;
                            sz = sz < 0 ? WIDTH - 1 : sz >= WIDTH ? 0 : sz;
                            donor = surface(neighbor, sx, sz);
                        }
                        // Do not bleed across land, waterfalls or water at another elevation.
                        if (!isWater(donor) || RenderDataPointUtil.getYMax(donor) != RenderDataPointUtil.getYMax(original)) break;
                        int tint = RenderDataPointUtil.getColor(donor);
                        int w = radius + 1 - distance;
                        red += ((tint >>> 16) & 255) * w;
                        green += ((tint >>> 8) & 255) * w;
                        blue += (tint & 255) * w;
                        weight += w;
                    }
                int rgb = ((red / weight) << 16) | ((green / weight) << 8) | (blue / weight);
                center.renderDataContainer.set(indices[column], (original & ~RGB_MASK) | ((long) rgb << 32));
            }
    }

    private static boolean isWater(long point)
    { return point != 0 && RenderDataPointUtil.getBlockMaterialId(point) == EDhApiBlockMaterial.WATER.index; }

    private static long surface(ColumnRenderSource source, int x, int z)
    {
        for (int y = 0; y < source.maxVerticalSliceCount; y++)
        {
            long point = source.getDataPoint(x, z, y);
            if (point == 0 || isWater(point)) return point;
        }
        return 0;
    }
}
