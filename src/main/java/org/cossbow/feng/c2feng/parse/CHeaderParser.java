package org.cossbow.feng.c2feng.parse;

import org.cossbow.feng.c2feng.convert.C2FengConverter;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Invokes clang to dump the JSON AST of a C header file, parses it
 * via {@link JsonAstParser}, and writes the resulting Fēng metadata.
 */
public class CHeaderParser {

    private final Path headerPath;
    private final String moduleName;
    private final Path outputDir;
    private final List<String> extraArgs;

    private String clangPath = "clang";

    /**
     * @param headerPath path to the C header file (.h)
     * @param moduleName Fēng module name for the generated metadata
     * @param outputDir  directory where the .feng file is written
     */
    public CHeaderParser(Path headerPath,
                         String moduleName,
                         Path outputDir) {
        this.headerPath = headerPath;
        this.moduleName = moduleName;
        this.outputDir = outputDir;
        this.extraArgs = new ArrayList<>();
    }

    /**
     * Add extra arguments forwarded to clang (e.g. {@code -I/path}, {@code -DNAME}).
     */
    public CHeaderParser addClangArg(String arg) {
        extraArgs.add(arg);
        return this;
    }

    /**
     * Override the clang executable path (default: {@code "clang"}).
     */
    public CHeaderParser clangPath(String path) {
        this.clangPath = path;
        return this;
    }

    // ========== main entry point ==========

    /**
     * Run the full pipeline: clang → parse → convert → write.
     *
     * @return the path of the generated .feng metadata file
     */
    public Path run() throws IOException, InterruptedException {
        if (!Files.isRegularFile(headerPath)) {
            throw new IOException("header file not found: " + headerPath);
        }
        Files.createDirectories(outputDir);

        var json = invokeClang();
        var converter = new C2FengConverter(moduleName);
        new JsonAstParser(converter).parse(json);

        var outputFile = outputDir.resolve(moduleName + ".feng");
        try (var w = Files.newBufferedWriter(outputFile)) {
            converter.write(w);
        }
        return outputFile;
    }

    // ========== clang invocation ==========

    private String invokeClang() throws IOException, InterruptedException {
        var cmd = buildClangCommand();
        var process = new ProcessBuilder(cmd)
                .redirectErrorStream(false)
                .start();

        // Read stdout (JSON AST)
        String json;
        try (var reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            json = readAll(reader);
        }

        // Read stderr (diagnostics)
        String stderr;
        try (var reader = new BufferedReader(
                new InputStreamReader(process.getErrorStream()))) {
            stderr = readAll(reader);
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException(
                    "clang failed (exit " + exitCode + "):\n" + stderr);
        }

        if (json == null || json.isBlank()) {
            throw new IOException(
                    "clang produced no AST output.\nstderr:\n" + stderr);
        }

        return json;
    }

    private List<String> buildClangCommand() {
        var cmd = new ArrayList<String>();
        cmd.add(clangPath);
        cmd.add("-Xclang");
        cmd.add("-ast-dump=json");
        cmd.add("-fsyntax-only");
        cmd.addAll(extraArgs);
        cmd.add(headerPath.toAbsolutePath().toString());
        return cmd;
    }

    // ========== helpers ==========

    private static String readAll(BufferedReader reader) throws IOException {
        var sb = new StringBuilder(4096);
        char[] buf = new char[4096];
        int n;
        while ((n = reader.read(buf)) != -1) {
            sb.append(buf, 0, n);
        }
        return sb.toString();
    }
}
