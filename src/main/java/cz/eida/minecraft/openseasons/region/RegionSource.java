/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.region;

import org.bukkit.World;

/**
 * Where region settings come from (WorldGuard; later maybe Lands or GriefPrevention).
 * Queries come from the main thread.
 */
public interface RegionSource {

    /** without a region plugin: {@link RegionSettings#NONE} everywhere */
    RegionSource NONE = new RegionSource() {
        @Override
        public RegionSettings query(World world, int chunkX, int chunkZ) {
            return RegionSettings.NONE;
        }

        @Override
        public long fingerprint(World world) {
            return 0;
        }

        @Override
        public String describe() {
            return "without WorldGuard, only global settings apply";
        }
    };

    /** settings for the whole chunk column (a region anywhere in the column applies to the whole chunk) */
    RegionSettings query(World world, int chunkX, int chunkZ);

    /**
     * Fingerprint of the regions of the world that may affect {@link #query}; another fingerprint = the regions changed
     * (e.g. {@code /rg flag}, {@code /rg redefine}, {@code /rg load}) and the cache is recomputed.
     */
    long fingerprint(World world);

    /** description for administrators */
    String describe();
}
