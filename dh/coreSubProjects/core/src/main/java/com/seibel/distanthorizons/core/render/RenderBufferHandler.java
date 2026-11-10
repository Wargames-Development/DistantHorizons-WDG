/*
 *    This file is part of the Distant Horizons mod
 *    licensed under the GNU LGPL v3 License.
 *
 *    Copyright (C) 2020 James Seibel
 *
 *    This program is free software: you can redistribute it and/or modify
 *    it under the terms of the GNU Lesser General Public License as published by
 *    the Free Software Foundation, version 3.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU Lesser General Public License for more details.
 *
 *    You should have received a copy of the GNU Lesser General Public License
 *    along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.seibel.distanthorizons.core.render;

import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.api.interfaces.override.rendering.IDhApiCullingFrustum;
import com.seibel.distanthorizons.api.interfaces.override.rendering.IDhApiShadowCullingFrustum;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.dataObjects.render.bufferBuilding.LodBufferContainer;
import com.seibel.distanthorizons.core.dependencyInjection.ModAccessorInjector;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.logging.DhLogger;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.logging.f3.F3Screen;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.pos.blockPos.DhBlockPos2D;
import com.seibel.distanthorizons.core.render.QuadTree.LodQuadTree;
import com.seibel.distanthorizons.core.render.QuadTree.LodRenderSection;
import com.seibel.distanthorizons.core.render.renderer.LodRenderer;
import com.seibel.distanthorizons.core.render.renderer.cullingFrustum.DhFrustumBounds;
import com.seibel.distanthorizons.core.render.renderer.cullingFrustum.NeverCullFrustum;
import com.seibel.distanthorizons.core.util.LodUtil;
import com.seibel.distanthorizons.core.util.objects.SortedArraySet;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftRenderWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.modAccessor.IIrisAccessor;
import com.seibel.distanthorizons.coreapi.ModInfo;
import com.seibel.distanthorizons.coreapi.interfaces.dependencyInjection.IOverrideInjector;
import com.seibel.distanthorizons.core.util.math.DhMat4f;
import com.seibel.distanthorizons.core.util.math.DhVec3d;
import org.joml.Matrix4f;

import java.util.ArrayList;

/**
 * This object tells the {@link LodRenderer} what buffers to render
 */
public class RenderBufferHandler implements AutoCloseable
{
    // Render-thread-owned index for this exact terrain view, separate from visibility depth.
    private final it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<LodRenderSection> coverageSections = new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<>();
    private final it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<LodBufferContainer> coverageBuffers = new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<>();
    private RenderParams coverageView;
    private boolean coverageIndexReady;
    private long coverageGeneration;

	private static final DhLogger LOGGER = new DhLoggerBuilder().build();
	
	private static final IMinecraftRenderWrapper MC_RENDER = SingletonInjector.INSTANCE.get(IMinecraftRenderWrapper.class);

	private static final IIrisAccessor IRIS_ACCESSOR = ModAccessorInjector.INSTANCE.get(IIrisAccessor.class);
	
	// static values for re-use to reduce GC pressure
	private static final float[] JOML_TRANSPOSE_ARRAY = new float[16];
	private static final Matrix4f WORLD_VIEW_JOML_MATRIX = new Matrix4f();
	private static final Matrix4f WORLD_VIEW_PROJ_JOML_MATRIX = new Matrix4f();
	private static final DhMat4f FRUSTOM_DH_MATRIX = new DhMat4f();
	
	
	/** contains all relevant data */
	public final LodQuadTree lodQuadTree;
	
	private final SortedArraySet<LodBufferContainer> loadedNearToFarBuffers;
	/** temp array to prevent threading issues and prevent re-allocating the same array each frame */
	private final ArrayList<LodRenderSection> tempProcessNodeList = new ArrayList<>();
	
	private int visibleBufferCount;
	private int culledBufferCount;
	private int shadowVisibleBufferCount;
	private int shadowCulledBufferCount;
	
	
	
	//=============//
	// constructor //
	//=============//
	//region
	
	public RenderBufferHandler(LodQuadTree lodQuadTree) 
	{ 
		this.lodQuadTree = lodQuadTree;
		
		IDhApiCullingFrustum coreCameraFrustum = DhApi.overrides.get(IDhApiCullingFrustum.class, IOverrideInjector.CORE_PRIORITY);
		if (coreCameraFrustum == null)
		{
			DhApi.overrides.bind(IDhApiCullingFrustum.class, new DhFrustumBounds());
		}
		
		// by default the shadow pass shouldn't have any frustum culling
		IDhApiShadowCullingFrustum coreShadowFrustum = DhApi.overrides.get(IDhApiShadowCullingFrustum.class, IOverrideInjector.CORE_PRIORITY);
		if (coreShadowFrustum == null)
		{
			DhApi.overrides.bind(IDhApiShadowCullingFrustum.class, new NeverCullFrustum());
		}
		
		this.loadedNearToFarBuffers = new SortedArraySet<>(this::sortBufferContainersNearToFar);
	}
	private int sortBufferContainersNearToFar(LodBufferContainer loadedBufferA, LodBufferContainer loadedBufferB)
	{
		DhBlockPos2D aPos = DhSectionPos.getCenterBlockPos(loadedBufferA.pos);
		DhBlockPos2D bPos = DhSectionPos.getCenterBlockPos(loadedBufferB.pos);
		
		DhBlockPos2D centerPos = this.lodQuadTree.getCenterBlockPos();
		
		int aManhattanDistance = aPos.manhattanDist(centerPos);
		int bManhattanDistance = bPos.manhattanDist(centerPos);
		return aManhattanDistance - bManhattanDistance;
	}
	
	//endregion
	
	
	
	//=================//
	// render building //
	//=================//
	//region
	
	/**
	 * The following buildRenderList sorting method is based on the following reddit post: <br>
	 * <a href="https://www.reddit.com/r/VoxelGameDev/comments/a0l8zc/correct_depthordering_for_translucent_discrete/">correct_depth_ordering_for_translucent_discrete</a>
	 */
	public void buildRenderList(RenderParams renderParams)
	{
		if (ModInfo.IS_DEV_BUILD)
		{
			if (!RenderThreadTaskHandler.INSTANCE.isCurrentThread())
			{
				LodUtil.assertNotReach("Should only be run on the render thread");
			}
		}
		
		// clear the old list so we can start fresh
		this.loadedNearToFarBuffers.clear();
        this.coverageSections.clear();
        this.coverageBuffers.clear();
        this.coverageView = renderParams;
        this.coverageIndexReady = false;
        this.coverageGeneration++;
		
		
		
		//====================================//
		// get and update the culling frustum //
		//====================================//
		
		// get the culling frustum
		boolean enableFrustumCulling;
		IDhApiCullingFrustum frustum;
		boolean isShadowPass = (IRIS_ACCESSOR != null && IRIS_ACCESSOR.isRenderingShadowPass());
		if (isShadowPass)
		{
			enableFrustumCulling = !Config.Client.Advanced.Graphics.Culling.disableShadowPassFrustumCulling.get();
			frustum = DhApi.overrides.get(IDhApiShadowCullingFrustum.class);
		}
		else
		{
			enableFrustumCulling = !Config.Client.Advanced.Graphics.Culling.disableFrustumCulling.get();
			frustum = DhApi.overrides.get(IDhApiCullingFrustum.class);
		}
		
		
		// update the frustum if necessary
		if (enableFrustumCulling)
		{
			int worldMinY = renderParams.clientLevelWrapper.getMinHeight();
			int worldHeight = renderParams.clientLevelWrapper.getMaxHeight();
			
			renderParams.mcModelViewMatrix.putValuesInArray(JOML_TRANSPOSE_ARRAY);
			WORLD_VIEW_JOML_MATRIX
				.setTransposed(JOML_TRANSPOSE_ARRAY)
				.translate(
					-(float) renderParams.exactCameraPosition.x,
					-(float) renderParams.exactCameraPosition.y,
					-(float) renderParams.exactCameraPosition.z);
			
			renderParams.dhProjectionMatrix.putValuesInArray(JOML_TRANSPOSE_ARRAY);
			WORLD_VIEW_PROJ_JOML_MATRIX
				.setTransposed(JOML_TRANSPOSE_ARRAY)
				.mul(WORLD_VIEW_JOML_MATRIX);

			FRUSTOM_DH_MATRIX.set(WORLD_VIEW_PROJ_JOML_MATRIX);
			frustum.update(worldMinY, worldMinY + worldHeight, FRUSTOM_DH_MATRIX);
		}
		
		
		
		//=========================//
		// Update the section list //
		//=========================//
		//region
		
		if (isShadowPass)
		{
			this.shadowCulledBufferCount = 0;
		}
		else
		{
			this.culledBufferCount = 0;
		}
		
		// setup iterator with culling frustum
		this.lodQuadTree.populateListWithEnabledRenderSections(this.tempProcessNodeList);
		for (LodRenderSection renderSection : this.tempProcessNodeList)
		{
			if (renderSection == null)
			{
				continue;
			}
			
			
			try
			{
				if (enableFrustumCulling)
				{
					int blockMinX = DhSectionPos.getMinCornerBlockX(renderSection.pos);
					int blockMinZ = DhSectionPos.getMinCornerBlockZ(renderSection.pos);
					int blockWidth = DhSectionPos.getBlockWidth(renderSection.pos);
					byte detailLevel = DhSectionPos.getDetailLevel(renderSection.pos);
					if (!frustum.intersects(blockMinX, blockMinZ, blockWidth, detailLevel))
					{
						if (isShadowPass)
						{
							this.shadowCulledBufferCount++;
						}
						else
						{
							this.culledBufferCount++;
						}
						
						continue;
					}
				}
			}
			catch (Exception e)
			{
				// don't cull if there was an unexpected issue
				LOGGER.error("Unexpected issue during culling for node pos: ["+DhSectionPos.toString(renderSection.pos)+"], error: ["+e.getMessage()+"].", e);
			}
			
			
			try
			{
				LodBufferContainer bufferContainer = renderSection.renderBufferContainer;
				if (bufferContainer == null
					|| !renderSection.getRenderingEnabled())
				{
					// shouldn't happen, but just in case
					continue;
				}
				
				this.loadedNearToFarBuffers.add(bufferContainer);
			}
			catch (Exception e)
			{
				LOGGER.error("Error updating QuadTree render source at [" + DhSectionPos.toString(renderSection.pos) + "], error: ["+e.getMessage()+"].", e);
			}
		}
		
		if (isShadowPass)
		{
			this.shadowVisibleBufferCount = this.loadedNearToFarBuffers.size();
		}
		else
		{
			this.visibleBufferCount = this.loadedNearToFarBuffers.size();
		}
		
		//endregion
		
	}
	
	//endregion
	
	
	
	//================//
	// render methods //
	//================//
	//region
	
    public long getCoverageGeneration() { return coverageGeneration; }

    private void ensureCoverageIndex() {
        if (!coverageIndexReady) {
            // No hash-index construction cost in frames without actor queries.
            for (int i=0;i<loadedNearToFarBuffers.size();i++) {
                LodBufferContainer buffer=loadedNearToFarBuffers.get(i);
                coverageBuffers.put(buffer.pos,buffer);
            }
            for (LodRenderSection section:tempProcessNodeList)
                if (section!=null && coverageBuffers.get(section.pos)==section.renderBufferContainer)
                    coverageSections.put(section.pos,section);
            coverageIndexReady=true;
        }
    }

    /** API event identity must match the exact view whose terrain buffers were selected. */
    public boolean isTerrainChunkKnown(com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiRenderParam view,
                                       long generation,int chunkX,int chunkZ) {
        if(generation!=coverageGeneration || generation==0 || coverageView==null || coverageView.apiCopy!=view || chunkX<-1875000 || chunkX>=1875000
                || chunkZ<-1875000 || chunkZ>=1875000) return false;
        ensureCoverageIndex();
        int blockX=chunkX<<4,blockZ=chunkZ<<4;
        for(byte detail=DhSectionPos.SECTION_BLOCK_DETAIL_LEVEL;detail<=30;detail++) {
            long pos=DhSectionPos.encode(detail,blockX>>detail,blockZ>>detail);
            LodRenderSection section=coverageSections.get(pos);
            if(section==null) continue;
            LodBufferContainer buffer=section.renderBufferContainer;
            if(buffer==null || !buffer.buffersUploaded || section.isRenderDataDirty() || !section.getRenderingEnabled()
                    || coverageBuffers.get(pos)!=buffer) return false;
            int dataDetail=detail-DhSectionPos.SECTION_MINIMUM_DETAIL_LEVEL;
            int x=blockX-DhSectionPos.getMinCornerBlockX(pos),z=blockZ-DhSectionPos.getMinCornerBlockZ(pos);
            return buffer.terrainCoverage.isKnown(x>>dataDetail,z>>dataDetail,(x+15)>>dataDetail,(z+15)>>dataDetail);
        }
        return false;
    }

    /** Checks source completeness in the active terrain view. Render thread only.
     * Missing, dirty, replaced or disposed buffers fail closed; known terrain is
     * still subject to per-fragment depth testing by the caller.
     */
    public boolean isTerrainKnown(RenderParams view,long generation, int blockX, int blockZ) {
        if (generation!=coverageGeneration || generation==0 || view == null || coverageView != view) return false;
        ensureCoverageIndex();
        for (byte detail=DhSectionPos.SECTION_BLOCK_DETAIL_LEVEL; detail<=30; detail++) {
            long pos=DhSectionPos.encode(detail,blockX >> detail,blockZ >> detail);
            LodRenderSection section=coverageSections.get(pos);
            if (section==null) continue;
            LodBufferContainer buffer=section.renderBufferContainer;
            if (buffer==null || !buffer.buffersUploaded || section.isRenderDataDirty() || !section.getRenderingEnabled()
                || coverageBuffers.get(pos)!=buffer) return false;
            int dataDetail=detail-DhSectionPos.SECTION_MINIMUM_DETAIL_LEVEL;
            int x=(blockX-DhSectionPos.getMinCornerBlockX(pos)) >> dataDetail;
            int z=(blockZ-DhSectionPos.getMinCornerBlockZ(pos)) >> dataDetail;
            return buffer.terrainCoverage.isKnown(x,z);
        }
        return false;
    }

	public SortedArraySet<LodBufferContainer> getColumnRenderBuffers() { return this.loadedNearToFarBuffers; }
	
	//endregion
	
	
	
	//=========//
	// F3 menu //
	//=========//
	//region
	
	public String getVboRenderDebugMenuString()
	{
		String countText = F3Screen.NUMBER_FORMAT.format(this.visibleBufferCount);
		if (!Config.Client.Advanced.Graphics.Culling.disableFrustumCulling.get())
		{
			countText += "/" + F3Screen.NUMBER_FORMAT.format(this.visibleBufferCount + this.culledBufferCount);
		}
		return "VBO Render Count: [" + countText + "]";
	}
	public String getShadowPassRenderDebugMenuString()
	{
		boolean hasIrisShaders = (IRIS_ACCESSOR != null && IRIS_ACCESSOR.isShaderPackInUse());
		if (!hasIrisShaders)
		{
			return null;
		}
		
		String countText = F3Screen.NUMBER_FORMAT.format(this.shadowVisibleBufferCount);
		if (!Config.Client.Advanced.Graphics.Culling.disableFrustumCulling.get())
		{
			countText += "/" + F3Screen.NUMBER_FORMAT.format(this.shadowVisibleBufferCount + this.shadowCulledBufferCount);
		}
		return "Shadow VBO Render Count: [" + countText + "]";
	}
	
	//endregion
	
	
	
	//=========//
	// cleanup //
	//=========//
	//region
	
	@Override
	public void close() { this.coverageView=null; this.coverageSections.clear();
        this.coverageBuffers.clear(); this.loadedNearToFarBuffers.clear(); this.lodQuadTree.close(); }
	
	//endregion
	
	
	
}
