package org.cossbow.feng.c2feng.parse;

import org.cossbow.feng.c2feng.convert.C2FengConverter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Invokes clang to dump the JSON AST of a C header file, parses it
 * via {@link JsonAstParser}, and writes the resulting Fēng metadata.
 */
public class CHeaderParser {
    private final Path header;


    /**
     * @param header path to the C header file (.h)
     */
    public CHeaderParser(Path header) {
        this.header = header;
    }

    // ========== main entry point ==========

    /**
     * Run clang and parse into an existing converter (for merging into
     * an already-populated ParseSymbolTable). Does not write a .feng file.
     */
    public void runInto(C2FengConverter converter)
            throws IOException, InterruptedException {
        if (!Files.isRegularFile(header)) {
            throw new IOException("header file not found: " + header);
        }
        var json = invokeClang();
        new JsonAstParser(converter).parse(json);
    }

    // ========== clang invocation ==========

    private String invokeClang()
            throws IOException, InterruptedException {
        var cmd = buildClangCommand();
        var process = new ProcessBuilder(cmd)
                .redirectErrorStream(false)
                .start();

        // Read stdout (JSON AST)
        var json = readAll(process::getInputStream);

        // Read stderr (diagnostics)
        var stderr = readAll(process::getErrorStream);

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException(
                    "clang failed (exit " + exitCode + "):\n" + stderr);
        }
        if (json.isBlank()) {
            throw new IOException(
                    "clang produced no AST output.\nstderr:\n" + stderr);
        }

        return json;
    }

    private List<String> buildClangCommand() {
        var cmd = new ArrayList<String>();
        cmd.add("clang");
        cmd.add("-Xclang");
        cmd.add("-ast-dump=json");
        cmd.add("-fsyntax-only");
        cmd.add("-std=c11");
        cmd.add(header.toAbsolutePath().toString());
        return cmd;
    }

    // ========== helpers ==========

    private static String readAll(Supplier<InputStream> res)
            throws IOException {
        try (var is = res.get();
             var ir = new InputStreamReader(is);
             var r = new BufferedReader(ir)) {
            var sb = new StringBuilder(4096);
            char[] buf = new char[4096];
            int n;
            while ((n = r.read(buf)) != -1) {
                sb.append(buf, 0, n);
            }
            return sb.toString();
        }
    }
}
