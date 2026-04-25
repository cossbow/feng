package org.cossbow.feng.ast.micro;

import org.cossbow.feng.ast.BinaryOperator;
import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.IdentifierMap;
import org.cossbow.feng.ast.UnaryOperator;
import org.cossbow.feng.util.Optional;

import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.Function;
import java.util.stream.Collectors;

public class MacroTable {
    private Map<Identifier, IdentifierMap<Macro>> tables
            = new HashMap<>();

    public void add(Macro macro) {
        add(macro.type(), macro.name(), macro);
    }

    void add(Identifier type, Identifier id, Macro value) {
        tables.computeIfAbsent(type, g -> new IdentifierMap<>())
                .add(id, value);
    }

    public void addAll(MacroTable ot) {
        for (var e : ot.tables.entrySet()) {
            tables.merge(e.getKey(), e.getValue(), (has, more) -> {
                has.addAll(more);
                return has;
            });
        }
    }

    public IdentifierMap<Macro> get(Identifier type) {
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

    public IdentifierMap<Macro> operators() {
        return tables.getOrDefault(TYPE_OPERATOR, new IdentifierMap<>());
    }

    public Optional<Macro> operator(Identifier name) {
        return tryGet(TYPE_OPERATOR, name);
    }

    //

    public static final Identifier TYPE_RESOURCE = new Identifier("resource");
    public static final Identifier RESOURCE_FREE = new Identifier("free");

    public static final Identifier TYPE_OPERATOR = new Identifier("operator");
    public static final Map<Identifier, BinaryOperator> BINARY_OPERATOR = BinaryOperator
            .Overridable.stream().collect(Collectors.toMap(o ->
                    new Identifier(o.name().toLowerCase()), Function.identity()));
    public static final Map<Identifier, UnaryOperator> UNARY_OPERATOR = UnaryOperator
            .Overridable.stream().collect(Collectors.toMap(o ->
                    new Identifier(o.name().toLowerCase()), Function.identity()));

}
