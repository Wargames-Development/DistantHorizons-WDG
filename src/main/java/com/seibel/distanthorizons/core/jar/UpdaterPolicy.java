/*
 *    This file is part of the Distant Horizons mod
 *    licensed under the GNU LGPL v3 License.
 */

package com.seibel.distanthorizons.core.jar;

/** Controls whether the packaged mod may use the upstream self-updater. */
public enum UpdaterPolicy
{
	MANAGED_DISABLED,
	UPSTREAM;

	public boolean allowsUpstreamUpdater() { return this == UPSTREAM; }
}
