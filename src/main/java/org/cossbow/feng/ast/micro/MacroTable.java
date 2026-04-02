package org.cossbow.feng.ast.micro;

import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.IdentifierTable;
import org.cossbow.feng.util.Optional;

import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;

public class MacroTable {
    private Map<Identifier, IdentifierTable<Macro>> tables
            = new HashMap<>();

    public void add(Macro macro) {
        add(macro.type(), macro.name(), macro);
    }

    void add(Identifier type, Identifier id, Macro value) {
        tables.computeIfAbsent(type, g -> new IdentifierTable<>())
                .add(id, value);
    }

    public IdentifierTable<Macro> get(Identifier type) {
        var table = tables.get(type);
        if (table == null) {
            throw new NoSuchElementException("not exists '" + type + "'");
        }
        return table;
    }

    public Macro get(Identifier type, Identifier id) {
        return get(type).get(id);
    }

    public Optional<Macro> tryGet(Identifier type, Identifier id) {
        var tab = tables.get(type);
        if (tab == null) return Optional.empty();
        return tab.tryGet(id);
    }

    public boolean isEmpty() {
        return tables.isEmpty();
    }

    public Optional<Macro> resourceFree() {
        return tryGet(TYPE_RESOURCE, RESOURCE_FREE);
    }

    //

    public static final Identifier TYPE_RESOURCE = new Identifier("resource");
    public static final Identifier RESOURCE_FREE = new Identifier("free");

}
