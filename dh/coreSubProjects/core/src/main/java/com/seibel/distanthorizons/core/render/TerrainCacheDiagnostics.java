package com.seibel.distanthorizons.core.render;

import com.seibel.distanthorizons.core.dataObjects.render.TerrainCoverage;
import com.seibel.distanthorizons.core.dataObjects.render.bufferBuilding.LodBufferContainer;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.util.threading.PriorityTaskPicker;
import com.seibel.distanthorizons.core.wrapperInterfaces.render.objects.IVertexBufferWrapper;
import java.util.Map;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.atomic.LongAdder;

/** Optional read-only counters for diagnosing coverage, cache provenance and mesh complexity. */
public final class TerrainCacheDiagnostics {
    public static final boolean ENABLED = Boolean.getBoolean("wgprofiler.dhCacheDetails");
    private static final int[] DISTANCE_BOUNDS = {0, 256, 512, 1024, 2048, 4096, 8192};
    private TerrainCacheDiagnostics() { }

    private static final class CpuClock {
        static final ThreadMXBean THREADS = ManagementFactory.getThreadMXBean();
    }

    public static long threadCpuTime() {
        if (!ENABLED) return -1;
        ThreadMXBean threads = CpuClock.THREADS;
        return threads.isCurrentThreadCpuTimeSupported() ? threads.getCurrentThreadCpuTime() : -1;
    }

    public static final class Work {
        private final LongAdder tasks = new LongAdder();
        private final LongAdder cpuSamples = new LongAdder();
        private final LongAdder cpuNanos = new LongAdder();
        private final LongAdder wallNanos = new LongAdder();

        public void record(long cpuStart, long wallStart) {
            tasks.increment();
            wallNanos.add(System.nanoTime() - wallStart);
            long end = threadCpuTime();
            if (cpuStart >= 0 && end >= cpuStart) {
                cpuSamples.increment();
                cpuNanos.add(end - cpuStart);
            }
        }

        public void writeTo(Map<String, Long> output, String prefix) {
            output.put(prefix + "_tasks_total", tasks.sum());
            output.put(prefix + "_wall_ns_total", wallNanos.sum());
            output.put(prefix + "_cpu_samples_total", cpuSamples.sum());
            output.put(prefix + "_cpu_ns_total", cpuNanos.sum());
        }
    }

    static final class Geometry {
        private final Bucket[] buckets = new Bucket[(Byte.MAX_VALUE + 1) * DISTANCE_BOUNDS.length];
        private final double cameraX, cameraZ;
        private final long nowMs;

        Geometry(double cameraX, double cameraZ, long nowMs) {
            this.cameraX = cameraX;
            this.cameraZ = cameraZ;
            this.nowMs = nowMs;
        }

        void add(LodBufferContainer buffer) {
            add(buffer.pos, buffer.terrainCoverage.cacheStats,
                countTriangles(buffer.vboOpaqueWrappers), countTriangles(buffer.vboTransparentWrappers));
        }

        void add(long pos, TerrainCoverage.CacheStats stats, long opaque, long transparent) {
            int detail = DhSectionPos.getDetailLevel(pos) - DhSectionPos.SECTION_MINIMUM_DETAIL_LEVEL;
            int width = DhSectionPos.getBlockWidth(pos);
            if (detail < 0 || width <= 0) return;
            int band = distanceBand(DhSectionPos.getCenterBlockPosX(pos) - cameraX,
                DhSectionPos.getCenterBlockPosZ(pos) - cameraZ);
            int index = detail * DISTANCE_BOUNDS.length + band;
            Bucket bin = buckets[index];
            if (bin == null) buckets[index] = bin = new Bucket();
            long columnWidth = width / TerrainCoverage.WIDTH;
            bin.add(columnWidth * columnWidth, stats, opaque, transparent, nowMs);
        }

        void writeTo(Map<String, Long> output) {
            for (int index = 0; index < buckets.length; index++) {
                Bucket bin = buckets[index];
                if (bin == null) continue;
                int detail = index / DISTANCE_BOUNDS.length;
                int band = index % DISTANCE_BOUNDS.length;
                String prefix = "lod_" + detail + ".distance_" + DISTANCE_BOUNDS[band] + ".";
                bin.writeTo(output, prefix);
            }
        }
    }

    static int distanceBand(double dx, double dz) {
        double squared = dx * dx + dz * dz;
        int band = DISTANCE_BOUNDS.length - 1;
        while (band > 0 && squared < (double) DISTANCE_BOUNDS[band] * DISTANCE_BOUNDS[band]) band--;
        return band;
    }

    private static final class Bucket {
        long sections, footprint, sources, presentColumns, completeColumns, presentArea, completeArea;
        long inputSpans, neighborsWithData, candidateQuads, opaqueTriangles, transparentTriangles, emptySections;
        long createdCount, createdAgeSum, modifiedCount, modifiedAgeSum, modifiedAgeMax, futureTimestamps;

        void add(long columnArea, TerrainCoverage.CacheStats stats, long opaque, long transparent, long nowMs) {
            sections++;
            footprint += columnArea * TerrainCoverage.WIDTH * TerrainCoverage.WIDTH;
            opaqueTriangles += opaque;
            transparentTriangles += transparent;
            if (opaque == 0 && transparent == 0) emptySections++;
            if (stats == null) return;

            sources++;
            presentColumns += stats.presentColumns;
            completeColumns += stats.knownColumns;
            presentArea += stats.presentColumns * columnArea;
            completeArea += stats.knownColumns * columnArea;
            inputSpans += stats.inputSpans;
            neighborsWithData += stats.neighborsWithData;
            candidateQuads += stats.candidateQuads;
            if (stats.createdMs > 0 && stats.createdMs <= nowMs) {
                createdCount++;
                createdAgeSum += nowMs - stats.createdMs;
            }
            if (stats.modifiedMs > 0 && stats.modifiedMs <= nowMs) {
                long age = nowMs - stats.modifiedMs;
                modifiedCount++;
                modifiedAgeSum += age;
                modifiedAgeMax = Math.max(modifiedAgeMax, age);
            }
            if (stats.createdMs > nowMs || stats.modifiedMs > nowMs) futureTimestamps++;
        }

        void writeTo(Map<String, Long> output, String prefix) {
            output.put(prefix + "sections", sections);
            output.put(prefix + "footprint_blocks2", footprint);
            output.put(prefix + "source_samples", sources);
            output.put(prefix + "present_columns", presentColumns);
            output.put(prefix + "complete_columns", completeColumns);
            output.put(prefix + "present_blocks2", presentArea);
            output.put(prefix + "complete_blocks2", completeArea);
            output.put(prefix + "input_spans", inputSpans);
            output.put(prefix + "neighbors_with_data", neighborsWithData);
            output.put(prefix + "candidate_quads", candidateQuads);
            output.put(prefix + "opaque_triangles", opaqueTriangles);
            output.put(prefix + "transparent_triangles", transparentTriangles);
            output.put(prefix + "empty_mesh_sections", emptySections);
            output.put(prefix + "created_age_count", createdCount);
            output.put(prefix + "created_age_ms_sum", createdAgeSum);
            output.put(prefix + "modified_age_count", modifiedCount);
            output.put(prefix + "modified_age_ms_sum", modifiedAgeSum);
            output.put(prefix + "modified_age_ms_max", modifiedAgeMax);
            output.put(prefix + "future_timestamps", futureTimestamps);
        }
    }

    private static long countTriangles(IVertexBufferWrapper[] buffers) {
        long triangles = 0;
        for (IVertexBufferWrapper buffer : buffers) {
            if (buffer != null) triangles += 2L * (buffer.getVertexCount() / 4);
        }
        return triangles;
    }

    static void queue(Map<String, Long> output, String prefix, PriorityTaskPicker.Executor executor) {
        if (executor == null) return;
        output.put(prefix + "_waiting", (long) executor.getQueueSize());
        output.put(prefix + "_running", (long) executor.getRunningTaskCount());
        output.put(prefix + "_completed_total", (long) executor.getCompletedTaskCount());
        executor.writeWorkMetrics(output, prefix);
    }
}
