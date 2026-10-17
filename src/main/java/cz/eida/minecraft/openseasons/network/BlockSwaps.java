/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.network;

import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.world.chunk.BaseChunk;
import com.github.retrooper.packetevents.protocol.world.chunk.Column;
import com.github.retrooper.packetevents.protocol.world.chunk.impl.v_1_18.Chunk_v1_18;
import com.github.retrooper.packetevents.protocol.world.chunk.palette.DataPalette;
import com.github.retrooper.packetevents.protocol.world.chunk.palette.GlobalPalette;
import com.github.retrooper.packetevents.protocol.world.chunk.palette.Palette;
import com.github.retrooper.packetevents.protocol.world.chunk.palette.SingletonPalette;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.github.retrooper.packetevents.protocol.world.states.type.StateType;
import com.github.retrooper.packetevents.protocol.world.states.type.StateTypes;
import cz.eida.minecraft.openseasons.biome.SeasonTints;
import cz.eida.minecraft.openseasons.config.MainConfig;
import cz.eida.minecraft.openseasons.season.Season;
import cz.eida.minecraft.openseasons.season.SubSeason;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Replacing blocks on the client by season ({@code block-replacements}):
 * the client sees another block, the server does not change. The default is birch leaves shown as acacia leaves in fall and winter,
 * because birch leaves have a fixed colour on the client while acacia leaves are tinted by the biome. Block states
 * (distance, persistence, water) are carried over. Phases ({@code sub-seasons}) are taken by the middle of the colour step.
 */
final class BlockSwaps {

    static final BlockSwaps NONE = new BlockSwaps(new Map[BiomeTable.LOOKS]);

    /** look → block state id → replacement; {@code null} = none */
    private final Map<Integer, Integer>[] byLook;

    private BlockSwaps(Map<Integer, Integer>[] byLook) {
        this.byLook = byLook;
    }

    /** replacements in the look, or {@code null} */
    Map<Integer, Integer> forLook(int look) {
        return look < 0 || look >= byLook.length ? null : byLook[look];
    }

    /**
     * @param disabled names of replacements that are not used ({@code client-colors.disabled-block-replacements})
     * @param version  block state ids as the server sends them
     */
    @SuppressWarnings("unchecked")
    static BlockSwaps build(MainConfig.BlockReplacements config, Set<String> disabled, ClientVersion version,
                            Consumer<String> warn) {
        if (!config.enabled()) return NONE;
        Map<Integer, Integer>[] byLook = new Map[BiomeTable.LOOKS];
        Map<StateType, List<WrappedBlockState>> states = null;
        for (MainConfig.BlockReplacement r : config.replacements()) {
            if (!r.enabled() || disabled.contains(r.id())) continue;
            StateType from = StateTypes.getByName(r.toReplace().toLowerCase(Locale.ROOT));
            StateType to = StateTypes.getByName(r.replacement().toLowerCase(Locale.ROOT));
            if (from == null || to == null) {
                warn.accept("block-replacements." + r.id() + ": unknown block " + (from == null ? r.toReplace() : r.replacement()));
                continue;
            }
            if (states == null) states = statesByType(version);
            Map<Integer, Integer> map = new HashMap<>();
            for (WrappedBlockState s : states.getOrDefault(from, List.of())) {
                map.put(s.getGlobalId(), carryOver(s, WrappedBlockState.getDefaultState(version, to)).getGlobalId());
            }
            for (Season season : r.seasons()) {
                for (int step = 0; step < SeasonTints.STEPS; step++) {
                    SubSeason phase = SubSeason.fromProgress((step + 0.5) * (100.0 / SeasonTints.STEPS));
                    if (!r.subSeasons().contains(phase)) continue;
                    int look = BiomeTable.look(season, step);
                    if (byLook[look] == null) byLook[look] = new HashMap<>();
                    map.forEach(byLook[look]::putIfAbsent);
                }
            }
        }
        for (int i = 0; i < byLook.length; i++) {
            if (byLook[i] != null) byLook[i] = Map.copyOf(byLook[i]);
        }
        return new BlockSwaps(byLook);
    }

    /** states of all blocks by type (once on load) */
    private static Map<StateType, List<WrappedBlockState>> statesByType(ClientVersion version) {
        Map<StateType, List<WrappedBlockState>> out = new HashMap<>();
        for (int id = 1; id < 1 << 16; id++) {
            WrappedBlockState s;
            try {
                s = WrappedBlockState.getByGlobalId(version, id, false);
            } catch (RuntimeException e) {
                break;
            }
            if (s == null || s.getGlobalId() != id) continue;
            out.computeIfAbsent(s.getType(), k -> new ArrayList<>()).add(s);
        }
        return out;
    }

    /** leaf (and water) properties from the original state, if the new block has them */
    private static WrappedBlockState carryOver(WrappedBlockState from, WrappedBlockState target) {
        WrappedBlockState t = target.clone();
        try {
            t.setDistance(from.getDistance());
        } catch (RuntimeException ignored) {
            // the new block has no distance
        }
        try {
            t.setPersistent(from.isPersistent());
        } catch (RuntimeException ignored) {
            // it has not
        }
        try {
            t.setWaterlogged(from.isWaterlogged());
        } catch (RuntimeException ignored) {
            // it has not
        }
        return t;
    }

    /** blocks in a chunk packet; @return true when something changed */
    static boolean apply(Column column, Map<Integer, Integer> swaps) {
        boolean changed = false;
        for (BaseChunk chunk : column.getChunks()) {
            if (!(chunk instanceof Chunk_v1_18 section)) continue;
            DataPalette blocks = section.getChunkData();
            if (blocks == null) continue;
            Palette p = blocks.palette;
            if (p instanceof SingletonPalette) {
                Integer r = swaps.get(p.idToState(0));
                if (r != null) {
                    blocks.palette = new SingletonPalette(r);
                    changed = true;
                }
                continue;
            }
            if (!(p instanceof GlobalPalette) && !contains(p, swaps)) continue;
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        Integer r = swaps.get(blocks.get(x, y, z));
                        if (r != null) {
                            blocks.set(x, y, z, r);
                            changed = true;
                        }
                    }
                }
            }
        }
        return changed;
    }

    private static boolean contains(Palette p, Map<Integer, Integer> swaps) {
        for (int i = 0; i < p.size(); i++) {
            if (swaps.containsKey(p.idToState(i))) return true;
        }
        return false;
    }
}
