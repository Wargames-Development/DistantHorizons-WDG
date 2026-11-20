package com.seibel.distanthorizons.forge17.terrain;

import com.seibel.distanthorizons.api.enums.config.EDhApiWorldCompressionMode;
import com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiWorldGenerationStep;
import com.seibel.distanthorizons.common.wrappers.block.BlockStateWrapper;
import com.seibel.distanthorizons.common.wrappers.world.ServerLevelWrapper;
import com.seibel.distanthorizons.core.api.internal.SharedApi;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.file.fullDatafile.V2.FullDataSourceProviderV2;
import com.seibel.distanthorizons.core.level.IDhLevel;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.util.FullDataPointUtil;
import com.seibel.distanthorizons.core.util.threading.ThreadPoolUtil;
import com.seibel.distanthorizons.core.world.IDhWorld;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.IntPredicate;

/** Server-thread evidence for opaque radar cells in DH's existing finest-detail storage. */
public final class DhRadarTerrainApi {
    public static final int API_VERSION = 1;
    private static final int MAX_SECTIONS = 128;
    private static final int MAX_READS_PER_TICK = 6;
    private static final int MAX_IN_FLIGHT = 12;
    private static final int MAX_SAMPLES_PER_TICK = 32 * 8192;
    private static final int MAX_SECTION_POINTS = 131072;
    private static final int MAX_COLUMN_POINTS = 256;
    private static final int SAVED_CHUNK_RETRY_TICKS = 20;
    private static final int REFRESH_TICKS = 200;
    private static final int EXPIRE_TICKS = 400;
    private static final int WIDTH = 64;
    private static final int COLUMN_COUNT = WIDTH * WIDTH;
    private static final LinkedHashMap<Key, Entry> sections = new LinkedHashMap<>(32, 0.75F, true);
    private static final ArrayList<CompletableFuture<Snapshot>> inFlight = new ArrayList<>(MAX_IN_FLIGHT);

    private static volatile Thread serverThread;
    private static World lastWorld;
    private static FullDataSourceProviderV2 lastProvider;
    private static int height;
    private static long lastPosition;
    private static Entry lastEntry;
    private static Set<Block> opaqueBlocks;
    private static long tick;
    private static int reads;
    private static int samples;

    private DhRadarTerrainApi() { }

    public static void serverTick() {
        serverThread = Thread.currentThread();
        tick++;
        reads = samples = 0;
        lastWorld = null;
        lastEntry = null;
        lastProvider = null;
        inFlight.removeIf(CompletableFuture::isDone);
    }

    public static void unload(World world) {
        sections.keySet().removeIf(key -> key.world == world);
        if (lastWorld == world) {
            lastWorld = null;
            lastProvider = null;
            lastEntry = null;
        }
    }

    public static void serverStopping() {
        sections.clear();
        lastWorld = null;
        lastProvider = null;
        lastEntry = null;
        opaqueBlocks = null;
        serverThread = null;
    }

    public static void chunkSaved(WorldServer world, int chunkX, int chunkZ) {
        if (Thread.currentThread() != serverThread || sections.isEmpty()) return;
        long pos = sectionPosition(chunkX >> 2, chunkZ >> 2);
        Entry entry = sections.get(new Key(world, pos));
        if (entry == null) return;
        entry.lastNativeSave = System.currentTimeMillis();
        entry.savedAfter[(chunkX & 3) * 4 + (chunkZ & 3)] = entry.lastNativeSave;
    }

    /**
     * False means no blocking evidence. Only the caller's live query may override an already
     * loaded cell. Calls on other threads or with unavailable terrain return false.
     */
    public static boolean isOpaque(World world, int x, int y, int z) {
        if (Thread.currentThread() != serverThread || !(world instanceof WorldServer)
                || world.isRemote || samples >= MAX_SAMPLES_PER_TICK || x < -30000000 || x >= 30000000
                || z < -30000000 || z >= 30000000 || y < 0) return false;
        samples++;
        boolean changedWorld = lastWorld != world;
        if (changedWorld) {
            lastWorld = world;
            lastEntry = null;
            lastPosition = Long.MIN_VALUE;
            height = world.getHeight();
            IDhWorld dhWorld = SharedApi.tryGetDhServerWorld();
            IDhLevel level = dhWorld == null ? null : dhWorld.getLevel(ServerLevelWrapper.getWrapper((WorldServer) world));
            lastProvider = level == null ? null : level.getFullDataProvider();
        }
        if (lastProvider == null || y >= height) return false;
        long pos = sectionPosition(x >> 6, z >> 6);
        if (lastPosition != pos) {
            lastPosition = pos;
            lastEntry = lookup(world, pos, lastProvider, height);
        }
        if (lastEntry == null || lastEntry.snapshot == null || tick - lastEntry.requestedAt >= EXPIRE_TICKS)
            return false;
        return lastEntry.blocks(x & 63, y, z & 63);
    }

    private static long sectionPosition(int sectionX, int sectionZ) {
        return DhSectionPos.encode(DhSectionPos.SECTION_BLOCK_DETAIL_LEVEL, sectionX, sectionZ);
    }

    private static Entry lookup(World world, long pos, FullDataSourceProviderV2 provider, int worldHeight) {
        Key key = new Key(world, pos);
        Entry entry = sections.get(key);
        if (entry != null && entry.provider != provider) {
            sections.remove(key);
            entry = null;
        }
        if (entry == null) {
            if (reads >= MAX_READS_PER_TICK || inFlight.size() >= MAX_IN_FLIGHT) return null;
            if (sections.size() >= MAX_SECTIONS) {
                sections.remove(sections.keySet().iterator().next());
            }
            entry = new Entry(provider);
            sections.put(key, entry);
        }
        if (entry.pending != null && entry.pending.isDone()) {
            try { entry.snapshot = entry.pending.getNow(null); }
            catch (CompletionException unavailable) { entry.snapshot = null; }
            entry.requestedAt = entry.startedAt;
            entry.pending = null;
        }
        int refresh = entry.snapshot != null && entry.snapshot.savedAt <= entry.lastNativeSave
                ? SAVED_CHUNK_RETRY_TICKS : REFRESH_TICKS;
        if (entry.pending == null && tick - entry.requestedAt >= refresh
                && reads < MAX_READS_PER_TICK && inFlight.size() < MAX_IN_FLIGHT) {
            ExecutorService executor = ThreadPoolUtil.getFileHandlerExecutor();
            if (executor != null && !executor.isShutdown()) {
                Set<Block> opaque = radarOpaqueBlocks();
                try {
                    entry.pending = CompletableFuture.supplyAsync(() -> read(provider, pos, worldHeight, opaque), executor);
                    entry.startedAt = tick;
                    inFlight.add(entry.pending);
                    reads++;
                } catch (RejectedExecutionException unavailable) {
                    entry.requestedAt = tick;
                    entry.snapshot = null;
                }
            }
        }
        return entry;
    }

    private static Set<Block> radarOpaqueBlocks() {
        if (opaqueBlocks == null) {
            Set<Block> blocks = Collections.newSetFromMap(new IdentityHashMap<Block, Boolean>());
            for (Object registered : Block.blockRegistry) {
                Block block = (Block) registered;
                try {
                    if (block.getMaterial() != Material.leaves && block.isOpaqueCube()) blocks.add(block);
                } catch (RuntimeException | LinkageError unsupported) {
                    // A mod block with an unavailable definition contributes no stored evidence.
                }
            }
            opaqueBlocks = Collections.unmodifiableSet(blocks);
        }
        return opaqueBlocks;
    }

    private static Snapshot read(FullDataSourceProviderV2 provider, long pos, int worldHeight, Set<Block> opaque) {
        try (FullDataSourceV2 source = provider.readExisting(pos)) {
            if (source == null) return null;
            return decode(source, worldHeight, id -> {
                Object state = source.mapping.getBlockStateWrapper(id);
                BlockStateWrapper block = state instanceof BlockStateWrapper ? (BlockStateWrapper) state : null;
                return block != null && block.blockState != null && opaque.contains(block.blockState.getBlock());
            });
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception | LinkageError unavailable) {
            return null;
        }
    }

    static Snapshot decode(FullDataSourceV2 source, int worldHeight, IntPredicate isOpaqueId) {
        if (source.getDataDetailLevel() != 0 || source.mapping.size() > MAX_SECTION_POINTS) return null;
        int[] offsets = new int[COLUMN_COUNT + 1];
        IntArrayList runs = new IntArrayList();
        byte[] opaqueById = new byte[source.mapping.size()];
        int points = 0;
        for (int column = 0; column < COLUMN_COUNT; column++) {
            offsets[column] = runs.size();
            byte compression = source.columnWorldCompressionMode.getByte(column);
            boolean exact = compression == EDhApiWorldCompressionMode.MERGE_SAME_BLOCKS.value;
            if ((!exact && compression != EDhApiWorldCompressionMode.VISUALLY_EQUAL.value)
                    || source.columnGenerationSteps.getByte(column) < EDhApiWorldGenerationStep.FEATURES.value) continue;
            LongArrayList data = source.dataPoints[column];
            if (data == null || data.size() > MAX_COLUMN_POINTS) continue;
            if ((points += data.size()) > MAX_SECTION_POINTS) return null;
            for (int i = 0; i < data.size(); i++) {
                long point = data.getLong(i);
                int bottom = FullDataPointUtil.getBottomY(point);
                int top = bottom + FullDataPointUtil.getHeight(point);
                int id = FullDataPointUtil.getId(point);
                if (top <= bottom || top > worldHeight || top > 65535 || id >= opaqueById.length) continue;
                if (opaqueById[id] == 0) opaqueById[id] = (byte) (isOpaqueId.test(id) ? 1 : -1);
                // Visual compression can extend the upper block's run downward over other blocks.
                if (opaqueById[id] == 1) runs.add(((exact ? bottom : top - 1) << 16) | top);
            }
        }
        offsets[COLUMN_COUNT] = runs.size();
        return new Snapshot(offsets, runs.toIntArray(), source.lastModifiedUnixDateTime);
    }

    static final class Snapshot {
        private final int[] offsets, runs;
        private final long savedAt;

        Snapshot(int[] offsets, int[] runs, long savedAt) {
            this.offsets = offsets;
            this.runs = runs;
            this.savedAt = savedAt;
        }

        boolean blocks(int x, int y, int z) {
            int column = FullDataSourceV2.relativePosToIndex(x, z);
            for (int i = offsets[column]; i < offsets[column + 1]; i++) {
                if (y >= (runs[i] >>> 16) && y < (runs[i] & 65535)) return true;
            }
            return false;
        }
    }

    private static final class Entry {
        final FullDataSourceProviderV2 provider;
        final long[] savedAfter = new long[16];
        CompletableFuture<Snapshot> pending;
        Snapshot snapshot;
        long requestedAt = -REFRESH_TICKS;
        long startedAt;
        long lastNativeSave;

        Entry(FullDataSourceProviderV2 provider) { this.provider = provider; }

        boolean blocks(int x, int y, int z) {
            long saved = savedAfter[(x >> 4) * 4 + (z >> 4)];
            return (saved == 0 || snapshot.savedAt > saved) && snapshot.blocks(x, y, z);
        }
    }

    private static final class Key {
        final World world;
        final long position;

        Key(World world, long position) { this.world = world; this.position = position; }
        @Override public int hashCode() { return 31 * System.identityHashCode(world) + Long.hashCode(position); }
        @Override public boolean equals(Object other) {
            if (!(other instanceof Key)) return false;
            Key key = (Key) other;
            return world == key.world && position == key.position;
        }
    }
}
