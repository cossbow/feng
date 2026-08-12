package org.cossbow.feng.util;

/**
 * Target operating system for compilation.
 * <p>
 * {@link #AUTO} detects the host OS at runtime; the explicit
 * {@link #WINDOWS} / {@link #LINUX} variants enable cross‑compilation.
 *
 * <h3>Where it is used</h3>
 * <ul>
 *   <li>Select {@code [windows]} / {@code [linux]} sections in {@code feng.cfg}</li>
 *   <li>Choose CMake generator and compiler defaults in {@code Compiler.runBuild()}</li>
 *   <li>Detect available host toolchain (always host, never cross)</li>
 * </ul>
 *
 * @see TargetArch
 */
public enum TargetOS {

    /** Detect the host OS at runtime. */
    AUTO,
    WINDOWS,
    LINUX;

    // ---- host identity (always current machine) ----

    private static final String HOST_OS = System.getProperty("os.name", "");

    private static boolean hostIsWindows() { return HOST_OS.startsWith("Windows"); }
    private static boolean hostIsLinux()   { return HOST_OS.startsWith("Linux"); }

    /** True when the host (builder) machine runs Windows — controls toolchain selection. */
    public static boolean isHostWindows() { return hostIsWindows(); }
    public static boolean isHostLinux()   { return hostIsLinux(); }

    // ---- target-OS queries ----

    public boolean isWindows() {
        return this == WINDOWS || (this == AUTO && hostIsWindows());
    }

    public boolean isLinux() {
        return this == LINUX || (this == AUTO && hostIsLinux());
    }

    /** Canonical lower‑case key matching {@code [windows]}/{@code [linux]} in feng.cfg. */
    public String configKey() {
        return switch (this) {
            case WINDOWS -> "windows";
            case LINUX   -> "linux";
            case AUTO    -> hostIsWindows() ? "windows"
                          : hostIsLinux()   ? "linux"
                          : "unknown";
        };
    }

    // ---- target triple & cross-compilation ----

    /** True when the target OS differs from the host — cross-compilation. */
    public boolean isCross() {
        return (this == WINDOWS && !hostIsWindows())
            || (this == LINUX   && !hostIsLinux());
    }

    /**
     * Clang target triple for cross-compilation.
     * Returns empty when the target matches the host (no {@code --target} needed).
     */
    public String targetTriple() {
        if (this == WINDOWS && hostIsWindows()) return "";
        if (this == LINUX   && hostIsLinux())   return "";
        return switch (this) {
            case WINDOWS -> "x86_64-windows-gnu";
            case LINUX   -> "x86_64-linux-gnu";
            case AUTO    -> "";
        };
    }

    /**
     * Preprocessor define to signal the target OS to C code
     * (e.g. {@code FENG_OS_LINUX}), so {@code #ifdef} can replace host macros.
     */
    public String osDefine() {
        return "FENG_OS_" + (switch (this) {
            case WINDOWS -> "WINDOWS";
            case LINUX   -> "LINUX";
            case AUTO    -> hostIsWindows() ? "WINDOWS"
                          : hostIsLinux()   ? "LINUX"
                          : "UNKNOWN";
        });
    }

    // ---- host toolchain discovery ----

    /** Check whether a command is available on the host {@code PATH}. */
    public static boolean commandExists(String name) {
        try {
            return Command.detect(
                    hostIsWindows() ? "where" : "which", name)
                    .exec().code() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Find an available make command on the host.
     * Falls back to {@code "make"} if nothing is detected.
     */
    public static String detectMake() {
        if (hostIsWindows()) {
            for (var name : new String[]{"mingw32-make", "make"}) {
                if (commandExists(name)) return name;
            }
        }
        return "make";
    }

    /** Find an available C compiler on the host, preferring clang. */
    public static String detectCC() {
        for (var name : new String[]{"clang", "cc"}) {
            if (commandExists(name)) return name;
        }
        return "cc";
    }

    /** Find an available C++ compiler on the host, preferring clang++. */
    public static String detectCXX() {
        for (var name : new String[]{"clang++", "c++"}) {
            if (commandExists(name)) return name;
        }
        return "c++";
    }
}
