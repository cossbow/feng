package org.cossbow.feng.ast.mod;

import org.cossbow.feng.ast.Identifier;

import java.util.List;
import java.util.stream.Collectors;

public class ModulePath {
    private final List<Identifier> values;

    public ModulePath(List<Identifier> values) {
        this.values = List.copyOf(values);
    }

    public ModulePath(Identifier... values) {
        this.values = List.of(values);
    }

    public List<Identifier> values() {
        return values;
    }

    public int size() {
        return values.size();
    }

    public Identifier get(int i) {
        return values.get(i);
    }

    //

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ModulePath t)) return false;
        return values.equals(t.values);
    }

    @Override
    public int hashCode() {
        return values.hashCode();
    }

    //
    @Override
    public String toString() {
        return values.stream().map(Identifier::value)
                .collect(Collectors.joining("."));
    }
}
