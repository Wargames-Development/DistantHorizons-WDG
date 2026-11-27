package com.seibel.distanthorizons.core.render;

import com.seibel.distanthorizons.core.dataObjects.render.TerrainCoverage;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

public class TerrainCacheDiagnosticsTest {
    @Test
    public void geometrySeparatesResolutionAndDistanceAndWeightsAreaByColumnWidth() {
        TerrainCacheDiagnostics.Geometry geometry = new TerrainCacheDiagnostics.Geometry(0, 0, 1000);
        long[] rows = new long[TerrainCoverage.WIDTH];
        rows[0] = 1;
        TerrainCoverage source = TerrainCoverage.copyOf(rows, new TerrainCoverage.CacheStats(12, 1, 23, 300, 900));
        TerrainCoverage mesh = source.withMeshStats(3, 40);
        assertTrue(mesh.isKnown(0, 0));
        assertFalse(mesh.isKnown(0, 1));
        geometry.add(DhSectionPos.encode((byte) 6, 0, 0), mesh.cacheStats, 20, 6);
        geometry.add(DhSectionPos.encode((byte) 7, 4, 0), mesh.cacheStats, 30, 4);
        geometry.add(DhSectionPos.encode((byte) 7, 4, 1), null, 2, 0);
        Map<String, Long> result = new HashMap<>();
        geometry.writeTo(result);
        assertEquals(12L, (long) result.get("lod_0.distance_0.present_blocks2"));
        assertEquals(48L, (long) result.get("lod_1.distance_512.present_blocks2"));
        assertEquals(4L, (long) result.get("lod_1.distance_512.complete_blocks2"));
        assertEquals(2L, (long) result.get("lod_1.distance_512.sections"));
        assertEquals(1L, (long) result.get("lod_1.distance_512.source_samples"));
        assertEquals(32L, (long) result.get("lod_1.distance_512.opaque_triangles"));
        assertEquals(4L, (long) result.get("lod_1.distance_512.transparent_triangles"));
        assertEquals(23L, (long) result.get("lod_1.distance_512.input_spans"));
        assertEquals(40L, (long) result.get("lod_1.distance_512.candidate_quads"));
        assertEquals(100L, (long) result.get("lod_1.distance_512.modified_age_ms_max"));
    }

    @Test
    public void handlesDistanceEdgesAndUnknownOrFutureTimestamps() {
        assertEquals(0, TerrainCacheDiagnostics.distanceBand(255.99, 0));
        assertEquals(1, TerrainCacheDiagnostics.distanceBand(256, 0));
        assertEquals(2, TerrainCacheDiagnostics.distanceBand(512, 0));
        TerrainCacheDiagnostics.Geometry geometry = new TerrainCacheDiagnostics.Geometry(0, 0, 1000);
        geometry.add(DhSectionPos.encode((byte) 6, 0, 0),
            new TerrainCoverage.CacheStats(0, 0, 0, 0, 1001), 0, 0);
        Map<String, Long> result = new HashMap<>();
        geometry.writeTo(result);
        assertEquals(0L, (long) result.get("lod_0.distance_0.created_age_count"));
        assertEquals(0L, (long) result.get("lod_0.distance_0.modified_age_count"));
        assertEquals(1L, (long) result.get("lod_0.distance_0.future_timestamps"));
        assertEquals(1L, (long) result.get("lod_0.distance_0.empty_mesh_sections"));
    }
}
