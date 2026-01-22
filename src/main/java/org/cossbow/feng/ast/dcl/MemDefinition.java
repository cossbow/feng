package org.cossbow.feng.ast.dcl;

import org.cossbow.feng.ast.*;
import org.cossbow.feng.ast.attr.Modifier;
import org.cossbow.feng.ast.gen.TypeParameters;
import org.cossbow.feng.ast.lit.IntegerLiteral;
import org.cossbow.feng.util.ErrorUtil;
import org.cossbow.feng.util.Optional;

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


    //

    public Optional<MemField> getField(Identifier name) {
        if (!"length".equals(name.value()))
            return Optional.empty();
        return Optional.of(new MemField(pos(), name,
                Primitive.INT.declarer(pos())));
    }

    public static class MemField extends Field {
        public MemField(Position pos, Identifier name, TypeDeclarer type) {
            super(pos, name, type);
        }
    }


    //

    public static final MemDefinition RAM = new MemDefinition("ram", false);
    public static final MemDefinition ROM = new MemDefinition("rom", true);

    public static final Map<Symbol, MemDefinition> CACHE = Map.of(
            RAM.symbol(), RAM,
            ROM.symbol(), ROM
    );

    public static MemDefinition get(boolean readonly) {
        return readonly ? ROM : RAM;
    }
}
