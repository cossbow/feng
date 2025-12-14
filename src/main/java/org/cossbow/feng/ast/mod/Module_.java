package org.cossbow.feng.ast.mod;

import org.cossbow.feng.ast.Entity;
import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.Position;

import java.util.List;
import java.util.stream.Collectors;

public class Module_ extends Entity {
    private final List<Identifier> path;

    public Module_(Position pos, List<Identifier> path) {
        super(pos);
        this.path = path;
    }

    public List<Identifier> path() {
        return path;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Module_ m)) return false;
        return path.equals(m.path);
    }

    @Override
    public int hashCode() {
        return path.hashCode();
    }

    @Override
    public String toString() {
        return path.stream().map(Identifier::value)
                .collect(Collectors.joining("."));
    }
}
