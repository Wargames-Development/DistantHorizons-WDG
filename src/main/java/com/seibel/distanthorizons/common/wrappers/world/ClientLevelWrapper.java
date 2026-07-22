package com.seibel.distanthorizons.common.wrappers.world;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.util.Vec3;
import net.minecraft.world.WorldServer;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.seibel.distanthorizons.api.enums.config.EDhApiLodShading;
import com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiLevelType;
import com.seibel.distanthorizons.api.interfaces.block.IDhApiBiomeWrapper;
import com.seibel.distanthorizons.api.interfaces.block.IDhApiBlockStateWrapper;
import com.seibel.distanthorizons.api.interfaces.render.IDhApiCustomRenderRegister;
import com.seibel.distanthorizons.api.objects.DhApiResult;
import com.seibel.distanthorizons.api.objects.data.IDhApiFullDataSource;
import com.seibel.distanthorizons.common.wrappers.block.BiomeWrapper;
import com.seibel.distanthorizons.common.wrappers.block.BlockStateWrapper;
import com.seibel.distanthorizons.common.wrappers.block.ClientBlockStateColorCache;
import com.seibel.distanthorizons.common.wrappers.block.FakeBlockState;
import com.seibel.distanthorizons.core.api.internal.SharedApi;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.enums.EDhDirection;
import com.seibel.distanthorizons.core.level.IDhLevel;
import com.seibel.distanthorizons.core.level.IKeyedClientLevelManager;
import com.seibel.distanthorizons.core.level.IServerKeyedClientLevel;
import com.seibel.distanthorizons.core.logging.DhLogger;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.pos.blockPos.DhBlockPos;
import com.seibel.distanthorizons.core.pos.blockPos.DhBlockPosMutable;
import com.seibel.distanthorizons.core.util.TimerUtil;
import com.seibel.distanthorizons.core.world.AbstractDhWorld;
import com.seibel.distanthorizons.core.wrapperInterfaces.block.IBlockStateWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IBiomeWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IClientLevelWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IServerLevelWrapper;
import com.seibel.distanthorizons.coreapi.util.ColorUtil;

public class ClientLevelWrapper implements IClientLevelWrapper {

    private static final DhLogger LOGGER = new DhLoggerBuilder().build();
    private static final Map<WorldClient, WeakReference<ClientLevelWrapper>> LEVEL_WRAPPER_BY_CLIENT_LEVEL = Collections
        .synchronizedMap(new WeakHashMap<>());

    private static final IKeyedClientLevelManager KEYED_CLIENT_LEVEL_MANAGER = SingletonInjector.INSTANCE
        .get(IKeyedClientLevelManager.class);

    private static final Minecraft MINECRAFT = Minecraft.getMinecraft();

    private final WorldClient level;
    private final ConcurrentHashMap<FakeBlockState, ClientBlockStateColorCache> blockCache = new ConcurrentHashMap<>();

    private BlockStateWrapper dirtBlockWrapper;
    private IDhLevel dhLevel;
    private volatile long lastAccessTime = System.currentTimeMillis();

    private static final ThreadLocal<DhBlockPosMutable> MUTABLE_BLOCK_POS_THREAD_LOCAL = ThreadLocal
        .withInitial(DhBlockPosMutable::new);
    private static final Timer CLIENT_CLEANUP_TIMER = TimerUtil.CreateTimer("ClientLevelTickCleanup");
    private static final TimerTask CLIENT_CLEANUP_TASK = TimerUtil.createTimerTask(ClientLevelWrapper::tickCleanup);
    private static final long INACTIVE_TIME_BEFORE_UNLOADED_IN_MS = 30 * 1000;

    static {
        CLIENT_CLEANUP_TIMER.scheduleAtFixedRate(CLIENT_CLEANUP_TASK, 0, 1000 / 20);
    }

    // =============//
    // constructor //
    // =============//

    protected ClientLevelWrapper(WorldClient level) {
        this.level = level;
    }

    // ===============//
    // wrapper logic //
    // ===============//

    public static IClientLevelWrapper getWrapper(@NotNull WorldClient level) {
        return getWrapper(level, false);
    }

    @Nullable
    public static IClientLevelWrapper getWrapper(@Nullable WorldClient level, boolean bypassLevelKeyManager) {
        if (!bypassLevelKeyManager) {
            if (level == null) {
                return null;
            }

            // used if the client is connected to a server that defines the currently loaded level
            IServerKeyedClientLevel overrideLevel = KEYED_CLIENT_LEVEL_MANAGER
                .getServerKeyedLevel(getWrapper(level, true));
            if (overrideLevel != null) {
                WeakReference<ClientLevelWrapper> wrapperRef = LEVEL_WRAPPER_BY_CLIENT_LEVEL.get(level);
                if (wrapperRef != null && wrapperRef.get() != overrideLevel) {
                    ClientLevelWrapper wrapper = wrapperRef.get();
                    if (wrapper != null) {
                        wrapper.tryUnloadFromWorld();
                    }
                    wrapperRef = null;
                }

                if (wrapperRef == null && overrideLevel instanceof ClientLevelWrapper) {
                    LEVEL_WRAPPER_BY_CLIENT_LEVEL.put(level, new WeakReference<>((ClientLevelWrapper) overrideLevel));
                }

                return overrideLevel;
            }
        }

        WeakReference<ClientLevelWrapper> wrapperRef = LEVEL_WRAPPER_BY_CLIENT_LEVEL.get(level);
        if (wrapperRef != null) {
            ClientLevelWrapper wrapper = wrapperRef.get();
            if (wrapper != null) {
                return wrapper;
            }
        }
        ClientLevelWrapper wrapper = new ClientLevelWrapper(level);
        LEVEL_WRAPPER_BY_CLIENT_LEVEL.put(level, new WeakReference<>(wrapper));
        return wrapper;
    }

    @Override
    public synchronized void markAccessed() {
        this.lastAccessTime = System.currentTimeMillis();
    }

    public synchronized long getLastAccessTime() {
        return this.lastAccessTime;
    }

    public static void tickCleanup() {
        WorldClient clientLevel = MINECRAFT.theWorld;
        if (clientLevel == null) {
            return;
        }

        long currentTime = System.currentTimeMillis();
        ArrayList<ClientLevelWrapper> levelsToUnload = new ArrayList<>();

        synchronized (LEVEL_WRAPPER_BY_CLIENT_LEVEL) {
            for (WeakReference<ClientLevelWrapper> ref : LEVEL_WRAPPER_BY_CLIENT_LEVEL.values()) {
                ClientLevelWrapper wrapper = ref.get();
                if (wrapper != null && wrapper.level != clientLevel) {
                    long inactiveTimeMs = currentTime - wrapper.getLastAccessTime();
                    if (inactiveTimeMs > INACTIVE_TIME_BEFORE_UNLOADED_IN_MS) {
                        levelsToUnload.add(wrapper);
                    }
                }
            }
        }

        for (ClientLevelWrapper wrapper : levelsToUnload) {
            synchronized (wrapper) {
                long inactiveTimeMs = currentTime - wrapper.getLastAccessTime();
                if (wrapper.level != clientLevel && inactiveTimeMs > INACTIVE_TIME_BEFORE_UNLOADED_IN_MS) {
                    LOGGER.debug("Unloading level [" + wrapper.getDhIdentifier() + "] due to inactivity");
                    wrapper.tryUnloadFromWorld();
                }
            }
        }
    }

    @Nullable
    @Override
    public IServerLevelWrapper tryGetServerSideWrapper() {
        try {
            WorldServer[] serverLevels = MINECRAFT.getIntegratedServer().worldServers;

            // attempt to find the server level with the same dimension type
            // TODO this assumes only one level per dimension type, the SubDimensionLevelMatcher will need to be added
            // for supporting multiple levels per dimension
            ServerLevelWrapper foundLevelWrapper = null;

            // TODO: Surely there is a more efficient way to write this code
            for (WorldServer serverLevel : serverLevels) {
                if (serverLevel.provider.dimensionId == this.level.provider.dimensionId) {
                    foundLevelWrapper = ServerLevelWrapper.getWrapper(serverLevel);
                    break;
                }
            }

            return foundLevelWrapper;
        } catch (Exception e) {
            LOGGER.error("Failed to get server side wrapper for client level: " + this.level);
            return null;
        }
    }

    private ClientBlockStateColorCache createBlockColorCache(FakeBlockState block) {
        return new ClientBlockStateColorCache(block, this);
    }

    private final Function<FakeBlockState, ClientBlockStateColorCache> cachedBlockColorCacheFunction = this::createBlockColorCache;

    /** Clears cached biome tint colors on all cached block states for this level. */
    public void clearBiomeColorCaches() {
        for (ClientBlockStateColorCache cache : this.blockCache.values()) {
            cache.clearBiomeColorCache();
        }
    }

    /** Clears biome color caches across all active ClientLevelWrappers. */
    public static void clearAllBiomeColorCaches() {
        for (WeakReference<ClientLevelWrapper> wrapperRef : LEVEL_WRAPPER_BY_CLIENT_LEVEL.values()) {
            ClientLevelWrapper wrapper = wrapperRef.get();
            if (wrapper != null) {
                wrapper.clearBiomeColorCaches();
            }
        }
    }

    // ====================//
    // base level methods //
    // ====================//

    @Override
    public int getBlockColor(DhBlockPos pos, IBiomeWrapper biome, FullDataSourceV2 fullDataSource,
        IBlockStateWrapper blockWrapper, boolean allowApiOverride) {
        final ClientBlockStateColorCache blockColorCache = this.blockCache
            .computeIfAbsent(((BlockStateWrapper) blockWrapper).blockState, cachedBlockColorCacheFunction);

        return blockColorCache.getColor((BiomeWrapper) biome, pos, this);
    }

    @Override
    public int getDirtBlockColor() {
        if (this.dirtBlockWrapper == null) {
            try {
                this.dirtBlockWrapper = (BlockStateWrapper) BlockStateWrapper
                    .deserialize(BlockStateWrapper.DIRT_RESOURCE_LOCATION_STRING, this);
            } catch (IOException e) {
                // shouldn't happen, but just in case
                LOGGER.warn(
                    "Unable to get dirt color with resource location ["
                        + BlockStateWrapper.DIRT_RESOURCE_LOCATION_STRING
                        + "] with level ["
                        + this
                        + "].",
                    e);
                return -1;
            }
        }

        return this.getBlockColor(DhBlockPos.ZERO, BiomeWrapper.EMPTY_WRAPPER, null, this.dirtBlockWrapper);
    }

    @Override
    public void clearBlockColorCache() {
        this.blockCache.clear();
        this.clearBiomeColorCaches();
    }

    @Override
    public DimensionTypeWrapper getDimensionType() {
        return DimensionTypeWrapper.getDimensionTypeWrapper(this.level.provider.dimensionId);
    }

    @Override
    public String getDimensionName() {
        return DimensionTypeWrapper.getDimensionTypeWrapper(this.level.provider.dimensionId)
            .getName() + ":"
            + this.level.provider.dimensionId;
    }

    @Override
    public long getHashedSeed() {
        return this.level.getSeed();
    } // TODO?

    @Override
    public String getDhIdentifier() {
        return this.getHashedSeedEncoded() + "@" + this.getDimensionName();
    }

    @Override
    public EDhApiLevelType getLevelType() {
        return EDhApiLevelType.CLIENT_LEVEL;
    }

    public WorldClient getLevel() {
        return this.level;
    }

    @Override
    public boolean hasCeiling() {
        return DimensionTypeWrapper.getDimensionTypeWrapper(this.level.provider.dimensionId)
            .hasCeiling();
    }

    @Override
    public boolean hasSkyLight() {
        return DimensionTypeWrapper.getDimensionTypeWrapper(this.level.provider.dimensionId)
            .hasSkyLight();
    }

    @Override
    public int getMaxHeight() {
        return this.level.getHeight();
    }

    @Override
    public int getMinHeight() {
        return 0;
    }

    @Override
    public WorldClient getWrappedMcObject() {
        return this.level;
    }

    private void tryUnloadFromWorld() {
        AbstractDhWorld world = SharedApi.getAbstractDhWorld();
        if (world == null || !world.unloadLevel(this)) {
            this.onUnload();
        }
    }

    @Override
    public void onUnload() {
        LEVEL_WRAPPER_BY_CLIENT_LEVEL.remove(this.level);
        this.dhLevel = null;
    }

    @Override
    public void setDhLevel(IDhLevel level) {
        dhLevel = level;
    }

    @Override
    public @Nullable IDhLevel getDhLevel() {
        return dhLevel;
    }

    @Override
    public File getDhSaveFolder() {
        if (this.dhLevel == null) {
            return null;
        }

        return this.dhLevel.getSaveStructure()
            .getSaveFolder(this);
    }

    @Override
    public DhApiResult<Color> getBlockColorPreApi(IDhApiBlockStateWrapper blockStateWrapper,
        IDhApiBiomeWrapper biomeWrapper, int blockWorldPosX, int blockWorldPosY, int blockWorldPosZ,
        IDhApiFullDataSource dataSource) {
        // cast to core objects //
        // region

        if (!(blockStateWrapper instanceof IBlockStateWrapper coreBlockStateWrapper)) {
            return DhApiResult.createFail(
                "Unable to cast [" + blockStateWrapper.getClass() + "] to [" + IBlockStateWrapper.class + "]");
        }

        if (!(biomeWrapper instanceof IBiomeWrapper coreBiomeWrapper)) {
            return DhApiResult
                .createFail("Unable to cast [" + biomeWrapper.getClass() + "] to [" + IBiomeWrapper.class + "]");
        }

        if (!(dataSource instanceof FullDataSourceV2 coreDataSource)) {
            return DhApiResult
                .createFail("Unable to cast [" + dataSource.getClass() + "] to [" + FullDataSourceV2.class + "]");
        }

        // endregion

        // use a mutable thread local to reduce allocations slightly
        DhBlockPosMutable blockWorldPos = MUTABLE_BLOCK_POS_THREAD_LOCAL.get();
        blockWorldPos.setX(blockWorldPosX);
        blockWorldPos.setY(blockWorldPosY);
        blockWorldPos.setZ(blockWorldPosZ);

        int color = this.getBlockColor(blockWorldPos, coreBiomeWrapper, coreDataSource, coreBlockStateWrapper, false);
        return DhApiResult.createSuccess(ColorUtil.toColorObjARGB(color));
    }

    // ===================//
    // generic rendering //
    // ===================//

    @Override
    public IDhApiCustomRenderRegister getRenderRegister() {
        if (this.dhLevel == null) {
            return null;
        }

        return this.dhLevel.getGenericRenderer();
    }

    @Override
    public Color getCloudColor(float tickDelta) {
        Vec3 colorVec3 = this.level.getCloudColour(tickDelta);
        return new Color((float) colorVec3.xCoord, (float) colorVec3.yCoord, (float) colorVec3.zCoord);
    }

    // ================//
    // base overrides //
    // ================//

    @Override
    public String toString() {
        if (this.level == null) {
            return "Wrapped{null}";
        }

        return "Wrapped{" + this.level.toString() + "@" + this.getDhIdentifier() + "}";
    }

    @Override
    public float getShade(EDhDirection lodDirection) {
        EDhApiLodShading lodShading = Config.Client.Advanced.Graphics.Quality.lodShading.get();
        switch (lodShading) {
            default:
            case AUTO:
            case ENABLED:
                switch (lodDirection) {
                    case DOWN:
                        return 0.5F;
                    default:
                    case UP:
                        return 1.0F;
                    case NORTH:
                    case SOUTH:
                        return 0.8F;
                    case WEST:
                    case EAST:
                        return 0.6F;
                }

            case DISABLED:
                return 1.0F;
        }
    }
}
