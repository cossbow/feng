package org.cossbow.feng.parser;

import java.nio.file.Path;
import java.util.Objects;

public class ModulePath {
    private final Path path;

    public ModulePath(Path path) {
        this.path = path;
    }

    public Path path() {
        return path;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ModulePath t)) return false;
        return Objects.equals(path, t.path);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(path);
    }
}
