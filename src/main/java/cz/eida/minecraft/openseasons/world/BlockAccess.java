/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.world;

import org.bukkit.Bukkit;
import org.bukkit.World;

import java.util.Optional;

/** Reading and writing blocks of one world for restoring from the journal; never loads chunks. */
public interface BlockAccess {

    /** block material ({@code minecraft:poppy}); empty when its chunk is not loaded */
    Optional<String> material(int x, int y, int z);

    /**
     * Sets the block from data ({@code minecraft:air}) without physics and neighbour updates.
     *
     * @return false when the data cannot be read (e.g. a block from another game version)
     */
    boolean set(int x, int y, int z, String blockData);

    /** blocks of a world on the server */
    static BlockAccess of(World world) {
        return new BlockAccess() {
            @Override
            public Optional<String> material(int x, int y, int z) {
                if (!world.isChunkLoaded(x >> 4, z >> 4)) return Optional.empty();
                return Optional.of(world.getBlockAt(x, y, z).getType().getKey().toString());
            }

            @Override
            public boolean set(int x, int y, int z, String blockData) {
                try {
                    world.getBlockAt(x, y, z).setBlockData(Bukkit.createBlockData(blockData), false);
                    return true;
                } catch (IllegalArgumentException e) {
                    return false;
                }
            }
        };
    }
}
