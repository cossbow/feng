package org.cossbow.feng.ast.dcl;

import org.cossbow.feng.ast.*;
import org.cossbow.feng.ast.attr.Modifier;
import org.cossbow.feng.ast.gen.TypeParameters;

import java.util.Map;

public class MemDefinition extends TypeDefinition {
    private final boolean readonly;

    private MemDefinition(String name, boolean readonly) {
        super(Position.ZERO,
                Modifier.empty(),
                new Symbol(Position.ZERO,
                        new Identifier(Position.ZERO, name)),
                TypeParameters.empty(), TypeDomain.MEM);
        this.readonly = readonly;
    }

    public boolean readonly() {
        return readonly;
    }

    public static final MemDefinition RAM = new MemDefinition("ram", false);
    public static final MemDefinition ROM = new MemDefinition("rom", true);

    public static final Map<String, MemDefinition> CACHE = Map.of(
            RAM.symbol().name().value(), RAM,
            ROM.symbol().name().value(), ROM
    );

    public static MemDefinition get(boolean readonly) {
        return readonly ? ROM : RAM;
    }
}
