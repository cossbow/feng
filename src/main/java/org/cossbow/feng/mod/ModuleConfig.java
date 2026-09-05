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
 * feng = io_common.feng
 *
 * # os-specific section
 * [windows]
 * link = ws2_32
 *
 * [linux]
 * link = pthread,dl
 * feng = ioring_linux.feng
 * }</pre>
 * <p>
 * Common options (outside any {@code [section]}) are always active.
 * os-specific options are merged on top when the current OS matches.
 * Keys are comma-separated where applicable (e.g. {@code link}, {@code feng}).
 */
public class ModuleConfig {

    // --- parsed data ---

    /** Libraries from the common section (always applied). */
    private final List<String> commonLinkLibs;
    /** Libraries keyed by os name (e.g. "windows", "linux"). */
    private final Map<String, List<String>> osLinkLibs;
    /** .feng whitelist from the common section (applied on all oss). */
    private final List<String> commonFengFiles;
    /**
     * os-specific .feng whitelist keyed by os name. Merged with
     * {@link #commonFengFiles}: when the merged list is non-empty for the
     * target os, only the listed .feng files of the module directory are
     * compiled; all other .feng files are ignored.
     */
    private final Map<String, List<String>> osFengFiles;
    private final boolean testing;

    private ModuleConfig(List<String> commonLinkLibs,
                         Map<String, List<String>> osLinkLibs,
                         List<String> commonFengFiles,
                         Map<String, List<String>> osFengFiles,
                         boolean testing) {
        this.commonLinkLibs = List.copyOf(commonLinkLibs);
        this.osLinkLibs = Map.copyOf(osLinkLibs);
        this.commonFengFiles = List.copyOf(commonFengFiles);
        this.osFengFiles = Map.copyOf(osFengFiles);
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

    /**
     * .feng source whitelist resolved for the given <em>target</em> os:
     * common section merged with os-specific section.
     * Empty means no filtering (all .feng files in the module are compiled);
     * otherwise only the listed file names are compiled for this os.
     */
    public List<String> fengFiles(TargetOS target) {
        var merged = new LinkedHashSet<>(commonFengFiles);
        merged.addAll(osFengFiles.getOrDefault(target.configKey(), List.of()));
        return List.copyOf(merged);
    }

    /** Whether this module is marked as test-only. */
    public boolean testing() {
        return testing;
    }

    // --- sentinel & factory ---

    public static final ModuleConfig EMPTY =
            new ModuleConfig(List.of(), Map.of(), List.of(), Map.of(), false);

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
        var commonFeng = new ArrayList<String>();
        var platFeng = new LinkedHashMap<String, List<String>>();
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
                } else if ("feng".equals(key)) {
                    // .feng whitelist — common + os-specific
                    if (section.isEmpty()) {
                        commonFeng.addAll(parseList(value));
                    } else {
                        platFeng.computeIfAbsent(section, k -> new ArrayList<>())
                                .addAll(parseList(value));
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
        var dedupCommonFeng = new ArrayList<>(new LinkedHashSet<>(commonFeng));
        var dedupFeng = new LinkedHashMap<String, List<String>>();
        for (var e : platFeng.entrySet()) {
            dedupFeng.put(e.getKey(),
                    List.copyOf(new LinkedHashSet<>(e.getValue())));
        }

        return new ModuleConfig(dedupCommon, dedupPlat,
                dedupCommonFeng, dedupFeng, testing);
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
