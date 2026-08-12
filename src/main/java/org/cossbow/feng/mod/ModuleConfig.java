package org.cossbow.feng.mod;

import org.cossbow.feng.util.TargetOS;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Per-module configuration loaded from a {@code feng.cfg} file
 * in the module's source directory.
 *
 * <h3>File format</h3>
 * <pre>{@code
 * # Common options — applied on all oss
 * link = m
 * testing = false
 *
 * # os-specific section
 * [windows]
 * link = ws2_32
 *
 * [linux]
 * link = pthread,dl
 * }</pre>
 * <p>
 * Common options (outside any {@code [section]}) are always active.
 * os-specific options are merged on top when the current OS matches.
 * Keys are comma-separated where applicable (e.g. {@code link}).
 */
public class ModuleConfig {

    // --- parsed data ---

    /** Libraries from the common section (always applied). */
    private final List<String> commonLinkLibs;
    /** Libraries keyed by os name (e.g. "windows", "linux"). */
    private final Map<String, List<String>> osLinkLibs;
    private final boolean testing;

    private ModuleConfig(List<String> commonLinkLibs,
                         Map<String, List<String>> osLinkLibs,
                         boolean testing) {
        this.commonLinkLibs = List.copyOf(commonLinkLibs);
        this.osLinkLibs = Map.copyOf(osLinkLibs);
        this.testing = testing;
    }

    // --- public API ---

    /**
     * Libraries to pass to the linker (bare names, without {@code -l}),
     * resolved for the given <em>target</em> os: common + os-specific.
     */
    public List<String> linkLibs(TargetOS target) {
        var merged = new LinkedHashSet<>(commonLinkLibs);
        merged.addAll(osLinkLibs.getOrDefault(target.configKey(), List.of()));
        return List.copyOf(merged);
    }

    /**
     * Libraries for the common section only (not os-resolved).
     * Useful when the caller wants to handle os filtering itself.
     */
    public List<String> commonLinkLibs() {
        return commonLinkLibs;
    }

    /**
     * os-specific libs (may be empty for unknown oss).
     */
    public List<String> osLinkLibs(String os) {
        return osLinkLibs.getOrDefault(os, List.of());
    }

    /** Whether this module is marked as test-only. */
    public boolean testing() {
        return testing;
    }

    // --- sentinel & factory ---

    public static final ModuleConfig EMPTY =
            new ModuleConfig(List.of(), Map.of(), false);

    /**
     * Load configuration from a module directory.
     *
     * @param moduleDir directory containing {@code .feng} / {@code .h} files
     * @return parsed config, or {@link #EMPTY} if no {@code feng.cfg} exists
     * @throws IOException on read error
     */
    public static ModuleConfig load(Path moduleDir) throws IOException {
        var cfgFile = moduleDir.resolve("feng.cfg");
        if (!Files.isRegularFile(cfgFile)) {
            return EMPTY;
        }

        var commonLink = new ArrayList<String>();
        var platLink = new LinkedHashMap<String, List<String>>();
        var testing = false;

        String section = ""; // "" = common, else os name

        try (var r = Files.newBufferedReader(cfgFile)) {
            String line;
            while ((line = r.readLine()) != null) {
                line = line.strip();

                // skip blank lines and comments
                if (line.isEmpty() || line.startsWith("#")) continue;

                // os section header: [windows], [linux], etc.
                if (line.startsWith("[") && line.endsWith("]")) {
                    section = line.substring(1, line.length() - 1).strip().toLowerCase();
                    continue;
                }

                int eq = line.indexOf('=');
                if (eq < 0) continue; // malformed line, skip

                var key = line.substring(0, eq).strip().toLowerCase();
                var value = line.substring(eq + 1).strip();

                if ("link".equals(key)) {
                    var libs = parseList(value);
                    if (section.isEmpty()) {
                        commonLink.addAll(libs);
                    } else {
                        platLink.computeIfAbsent(section, k -> new ArrayList<>())
                                .addAll(libs);
                    }
                } else if ("testing".equals(key)) {
                    // testing is only meaningful in the common section
                    if (section.isEmpty()) {
                        testing = Boolean.parseBoolean(value);
                    }
                }
            }
        }

        // Deduplicate common link while preserving order
        var dedupCommon = new ArrayList<>(new LinkedHashSet<>(commonLink));
        var dedupPlat = new LinkedHashMap<String, List<String>>();
        for (var e : platLink.entrySet()) {
            dedupPlat.put(e.getKey(),
                    List.copyOf(new LinkedHashSet<>(e.getValue())));
        }

        return new ModuleConfig(dedupCommon, dedupPlat, testing);
    }

    // ---- internal helpers ----

    private static List<String> parseList(String raw) {
        if (raw.isBlank()) return List.of();
        return Arrays.stream(raw.split("\\s*,\\s*"))
                .map(String::strip)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
