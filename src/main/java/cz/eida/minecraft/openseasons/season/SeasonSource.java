/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.season;

import java.util.Locale;
import java.util.Optional;

/**
 * Where the season of a world or region comes from: the game calendar (default) or the real date
 * ({@code real-calendar.yml}). The values are also used by the WorldGuard flag {@code season-source}.
 */
public enum SeasonSource {
    /** game calendar (date by the world clock) */
    GAME,
    /** real-world seasons by the real date */
    REAL;

    public static Optional<SeasonSource> parse(String value) {
        if (value == null) return Optional.empty();
        String v = value.trim().toUpperCase(Locale.ROOT);
        return switch (v) {
            case "GAME" -> Optional.of(GAME);
            case "REAL" -> Optional.of(REAL);
            default -> Optional.empty();
        };
    }
}
