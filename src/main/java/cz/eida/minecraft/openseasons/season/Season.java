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
 * Season. Its only identity: the position in the cycle is the order of the constants, no extra numbering.
 */
public enum Season {
    WINTER, SPRING, SUMMER, FALL;

    private static final Season[] VALUES = values();

    /** the next season in the fixed cycle winter → spring → summer → fall */
    public Season next() {
        return VALUES[(ordinal() + 1) % VALUES.length];
    }

    /** key in YAML: winter, spring, summer, fall */
    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * Name from the configuration or data, case-insensitive; {@code AUTUMN} is an alias of fall.
     * Values without a season ({@code NONE}, {@code DISABLED}, {@code RESTORE}) return empty.
     */
    public static Optional<Season> parse(String value) {
        if (value == null) return Optional.empty();
        String v = value.trim().toUpperCase(Locale.ROOT);
        if (v.equals("AUTUMN")) return Optional.of(FALL);
        for (Season s : VALUES) {
            if (s.name().equals(v)) return Optional.of(s);
        }
        return Optional.empty();
    }
}
