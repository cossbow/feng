package org.cossbow.feng.ast;

import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.BiConsumer;

public class MultiTable<T> {
    private final Map<Identifier, UniqueTable<T>> tables = new HashMap<>();

    public void add(Identifier group, Identifier id, T value) {
        tables.computeIfAbsent(group, g -> new UniqueTable<>())
                .add(id, value);
    }

    public UniqueTable<T> get(Identifier group) {
        var table = tables.get(group);
        if (table == null) {
            throw new NoSuchElementException("not exists '" + group + "'");
        }
        return table;
    }

    public T get(Identifier group, Identifier id) {
        return get(group).get(id);
    }

    public boolean exists(Identifier group, Identifier id) {
        var table = tables.get(group);
        return table != null && table.exists(id);
    }

    public void forEach(BiConsumer<Identifier, UniqueTable<T>> action) {
        tables.forEach(action);
    }
}
