/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the design rules: no NMS/CraftBukkit/ProtocolLib, no hand-written block ids
 * and no unencrypted HTTP. Fails as soon as anything like that gets into the sources.
 */
class SourceGuardTest {

    private static final Path MAIN = Path.of("src/main/java");
    /** the only package where NMS might ever appear (empty so far) */
    private static final String NMS_ALLOWED = "/compat/";

    private static final Pattern RAW_BLOCK_ID =
            Pattern.compile("setBlockInNMSChunk|getStateById|getCombinedId\\(|getRawId\\(");
    private static final Pattern PLAIN_HTTP = Pattern.compile("\"http://(?!localhost|127\\.0\\.0\\.1)");

    @Test
    void noNmsCraftBukkitOrProtocolLibImports() {
        List<String> offenders = scan((file, line) -> {
            String t = line.trim();
            return t.startsWith("import ")
                    && !file.toString().contains(NMS_ALLOWED)
                    && (t.contains("net.minecraft.") || t.contains("org.bukkit.craftbukkit.")
                    || t.contains("io.netty.") || t.contains("com.comphenix."));
        });
        assertTrue(offenders.isEmpty(), "NMS/ProtocolLib imports: " + offenders);
    }

    @Test
    void noRawBlockIds() {
        List<String> offenders = scan((file, line) -> RAW_BLOCK_ID.matcher(line).find());
        assertTrue(offenders.isEmpty(), "hand-written block ids: " + offenders);
    }

    /**
     * PlaceholderAPI is optional: its classes (and {@code SeasonExpansion}, which extends it) may only be known by
     * wrappers called after checking that the plugin runs. A reference from elsewhere breaks the start without PlaceholderAPI.
     */
    @Test
    void placeholderApiOnlyBehindHooks() {
        List<String> offenders = scan((file, line) -> {
            String name = file.getFileName().toString();
            boolean hook = name.equals("SeasonExpansion.java") || name.equals("PlaceholderHook.java")
                    || name.equals("PapiHook.java");
            String t = line.trim();
            return !hook && !t.startsWith("*") && !t.startsWith("/*") && !t.startsWith("//")
                    && (t.contains("me.clip.") || t.matches(".*\\bSeasonExpansion\\b.*"));
        });
        assertTrue(offenders.isEmpty(), "PlaceholderAPI outside the wrappers: " + offenders);
    }

    @Test
    void noPlainHttp() {
        List<String> offenders = scan((file, line) -> PLAIN_HTTP.matcher(line).find());
        assertTrue(offenders.isEmpty(), "unencrypted HTTP: " + offenders);
    }

    private interface LineCheck {
        boolean offends(Path file, String line);
    }

    private static List<String> scan(LineCheck check) {
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(MAIN)) {
            files.filter(p -> p.toString().endsWith(".java")).forEach(p -> {
                try {
                    List<String> lines = Files.readAllLines(p);
                    for (int i = 0; i < lines.size(); i++) {
                        if (check.offends(p, lines.get(i))) {
                            offenders.add(p + ":" + (i + 1) + ": " + lines.get(i).trim());
                        }
                    }
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return offenders;
    }
}
