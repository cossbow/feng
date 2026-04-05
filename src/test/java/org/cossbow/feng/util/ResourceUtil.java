package org.cossbow.feng.util;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import static java.nio.charset.StandardCharsets.UTF_8;

public class ResourceUtil {

    public static Path getDir(String name) {
        var cl = Thread.currentThread().getContextClassLoader();
        var dir = cl.getResource(name);
        assert dir != null;
        return new File(dir.getFile()).toPath();
    }

    public static List<Path> list(Path dir) {
        try (var l = Files.list(dir)) {
            return l.filter(Constants::isSource).toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static void write(Path file, Output o) {
        try (var w = Files.newBufferedWriter(file, UTF_8)) {
            o.write(w);
            w.flush();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public interface Output {
        void write(Writer writer) throws IOException;
    }

    public static <T> CompletableFuture<Void> go(
            List<T> list, Consumer<T> run) {
        try (var executor = Executors.newFixedThreadPool(
                Runtime.getRuntime().availableProcessors())) {
            return CompletableFuture.allOf(list.stream()
                    .map(t -> CompletableFuture.runAsync(
                            () -> run.accept(t), executor))
                    .toArray(CompletableFuture[]::new));
        }
    }
}
