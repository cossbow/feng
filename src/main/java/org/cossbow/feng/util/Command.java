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

public class Command {
    private final Path dir;
    private final List<String> cmd;

    public Command(Path dir, List<String> cmd) {
        this.dir = dir;
        this.cmd = cmd;
    }

    public Command(Path dir, String... cmd) {
        this(dir, Arrays.asList(cmd));
    }

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
            return new ProcessBuilder(cmd)
                    .directory(dir.toFile())
                    .inheritIO()
                    .start();
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
            return new Result(ec, err.get(), out.get());
        } catch (Exception e) {
            throw ErrorUtil.sneaky(e);
        }
    }

    public record Result(int code, String out, String err) {
    }
}
