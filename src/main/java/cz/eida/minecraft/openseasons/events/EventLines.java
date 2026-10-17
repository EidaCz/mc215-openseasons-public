/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.events;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Lines {@code commands.start} / {@code commands.stop}: a line starting with {@code /}
 * is a console command, another one a message to the players of the world, {@code NONE} and an empty line are skipped.
 * Placeholders {@code %day%}, {@code %month%}, {@code %month_asname%}, {@code %year%},
 * {@code %weekday%}, {@code %season%}, {@code %world%}; PlaceholderAPI fills in the rest.
 */
public final class EventLines {

    /** event placeholders in the documented order */
    public static final List<String> PLACEHOLDERS =
            List.of("day", "month", "month_asname", "year", "weekday", "season", "world");

    public enum Kind {
        /** console command without the leading slash */
        COMMAND,
        /** message to the players of the world (colours {@code &}) */
        MESSAGE
    }

    public record Line(Kind kind, String text) {
    }

    private EventLines() {
    }

    public static List<Line> parse(List<String> lines) {
        List<Line> out = new ArrayList<>();
        for (String raw : lines) {
            if (raw == null) continue;
            String t = raw.strip();
            if (t.isEmpty() || t.equalsIgnoreCase("NONE")) continue;
            if (t.startsWith("/")) {
                String command = t.substring(1).strip();
                if (!command.isEmpty()) out.add(new Line(Kind.COMMAND, command));
            } else {
                out.add(new Line(Kind.MESSAGE, raw));
            }
        }
        return out;
    }

    /** fills in {@code %key%} from the map; leaves unknown placeholders (for PlaceholderAPI) */
    public static String fill(String text, Map<String, String> values) {
        String out = text;
        for (Map.Entry<String, String> e : values.entrySet()) {
            out = out.replace("%" + e.getKey() + "%", e.getValue());
        }
        return out;
    }
}
