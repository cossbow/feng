package org.cossbow.feng.parser;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class ModulePath {
    private final String[] path;

    public ModulePath(String[] path) {
        this.path = path;
    }

    public String[] path() {
        return path;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ModulePath t)) return false;
        return Arrays.equals(path, t.path);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(path);
    }
}
