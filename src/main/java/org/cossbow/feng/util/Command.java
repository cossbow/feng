package org.cossbow.feng.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/**
 * Thin wrapper around {@link ProcessBuilder} that captures stdout/stderr
 * and provides a clean {@link Result}.
 */
public class Command {
    private final Path dir;
    private final List<String> cmd;
    private boolean quiet;

    public Command(Path dir, List<String> cmd) {
        this(dir, cmd, false);
    }

    public Command(Path dir, String... cmd) {
        this(dir, Arrays.asList(cmd), false);
    }

    private Command(Path dir, List<String> cmd, boolean quiet) {
        this.dir = dir;
        this.cmd = cmd;
        this.quiet = quiet;
    }

    public Command quiet(boolean quiet) {
        this.quiet = quiet;
        return this;
    }

    // ---- factories ----

    /**
     * Create a silent command suitable for tool detection
     * (no working directory, stderr merged into stdout, no inheritIO).
     */
    public static Command detect(String... cmd) {
        return new Command(null, Arrays.asList(cmd), true);
    }

    // ---- exec ----

    private CompletableFuture<String> read(Supplier<InputStream> src) {
        return CompletableFuture.supplyAsync(() -> {
            try (var is = src.get();
                 var r = new InputStreamReader(is);
                 var w = new StringWriter()) {
                r.transferTo(w);
                return w.toString();
            } catch (IOException e) {
                throw ErrorUtil.sneaky(e);
            }
        }, Executors.newVirtualThreadPerTaskExecutor());
    }

    private Process start() {
        try {
            var pb = new ProcessBuilder(cmd);
            if (dir != null) {
                pb.directory(dir.toFile());
            }
            if (quiet) {
                pb.redirectErrorStream(true);
            } else {
                pb.inheritIO();
            }
            return pb.start();
        } catch (IOException e) {
            throw ErrorUtil.sneaky(e);
        }
    }

    public Result exec() {
        var p = start();
        var err = read(p::getErrorStream);
        var out = read(p::getInputStream);
        try {
            int ec = p.waitFor();
            return new Result(ec, out.get(), err.get());
        } catch (Exception e) {
            throw ErrorUtil.sneaky(e);
        }
    }

    public record Result(int code, String out, String err) {
        public int code() { return code; }
        public String out() { return out; }
        public String err() { return err; }
    }
}
