/*
 *    This file is part of the Distant Horizons mod
 *    licensed under the GNU LGPL v3 License.
 */

package com.seibel.distanthorizons.core.jar;

import com.seibel.distanthorizons.coreapi.ReleaseChannel;

/** Immutable validated build provenance loaded from build_info.json. */
public final class BuildInfo
{
	public static final int CURRENT_SCHEMA_VERSION = 1;
	public static final String WDG_REPOSITORY = "Wargames-Development/DistantHorizons-WDG";

	public final int schemaVersion;
	public final String modId;
	public final String modVersion;
	public final ReleaseChannel releaseChannel;
	public final String repository;
	public final String upstreamRepository;
	public final String branchOrChannel;
	public final String commit;
	public final String shortCommit;
	public final String treeState;
	public final String sourceTreeDigest;
	public final String buildSource;
	public final String minecraftVersion;
	public final String forgeVersion;
	public final int javaClassFileTarget;
	public final String packageContractVersion;
	public final UpdaterPolicy updaterPolicy;
	public final boolean reproducibleBuild;

	public BuildInfo(
		int schemaVersion,
		String modId,
		String modVersion,
		ReleaseChannel releaseChannel,
		String repository,
		String upstreamRepository,
		String branchOrChannel,
		String commit,
		String shortCommit,
		String treeState,
		String sourceTreeDigest,
		String buildSource,
		String minecraftVersion,
		String forgeVersion,
		int javaClassFileTarget,
		String packageContractVersion,
		UpdaterPolicy updaterPolicy,
		boolean reproducibleBuild)
	{
		this.schemaVersion = schemaVersion;
		this.modId = modId;
		this.modVersion = modVersion;
		this.releaseChannel = releaseChannel;
		this.repository = repository;
		this.upstreamRepository = upstreamRepository;
		this.branchOrChannel = branchOrChannel;
		this.commit = commit;
		this.shortCommit = shortCommit;
		this.treeState = treeState;
		this.sourceTreeDigest = sourceTreeDigest;
		this.buildSource = buildSource;
		this.minecraftVersion = minecraftVersion;
		this.forgeVersion = forgeVersion;
		this.javaClassFileTarget = javaClassFileTarget;
		this.packageContractVersion = packageContractVersion;
		this.updaterPolicy = updaterPolicy;
		this.reproducibleBuild = reproducibleBuild;
	}

	public boolean isWdgManaged()
	{
		return WDG_REPOSITORY.equals(this.repository) && this.updaterPolicy == UpdaterPolicy.MANAGED_DISABLED;
	}
}
