package org.cossbow.feng.ast.micro;

import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.IdentifierTable;

import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.BiConsumer;

public class MacroTable {
    private Map<Identifier, IdentifierTable<Macro>> tables
            = new HashMap<>();

    public void add(Identifier group, Identifier id, Macro value) {
        tables.computeIfAbsent(group, g -> new IdentifierTable<>())
                .add(id, value);
    }

    public IdentifierTable<Macro> get(Identifier group) {
        var table = tables.get(group);
        if (table == null) {
            throw new NoSuchElementException("not exists '" + group + "'");
        }
        return table;
    }

    public Macro get(Identifier group, Identifier id) {
        return get(group).get(id);
    }

    public boolean exists(Identifier group, Identifier id) {
        var table = tables.get(group);
        return table != null && table.exists(id);
    }

}
