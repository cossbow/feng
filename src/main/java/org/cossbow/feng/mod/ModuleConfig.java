package org.cossbow.feng.mod;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

/**
 * Per-module configuration loaded from a {@code feng.cfg} file
 * in the module's source directory.
 * <p>
 * File format is Java {@link Properties} (key=value, one per line).
 * If the file is absent or a key is missing, defaults to empty.
 */
public class ModuleConfig {

    private final List<String> linkLibs;

    private final boolean testing;

    private ModuleConfig(List<String> linkLibs, boolean testing) {
        this.linkLibs = linkLibs;
        this.testing = testing;
    }

    /**
     * Libraries to pass to the linker (bare names, without {@code -l}).
     */
    public List<String> linkLibs() {
        return linkLibs;
    }

    /**
     * This module is for test
     */
    public boolean testing() {
        return testing;
    }

    /**
     * Sentinel for a module without a {@code feng.cfg}.
     */
    public static final ModuleConfig EMPTY =
            new ModuleConfig(List.of(), false);

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

        var props = new Properties();
        try (var r = Files.newBufferedReader(cfgFile)) {
            props.load(r);
        }

        var raw = props.getProperty("link");
        var libs = (raw == null || raw.isBlank())
                ? List.<String>of()
                : Arrays.stream(raw.split("\\s*,\\s*"))
                .map(String::strip)
                .filter(s -> !s.isEmpty())
                .toList();

        var test = Boolean.parseBoolean(props.getProperty("testing"));

        return new ModuleConfig(libs, test);
    }
}
