package org.cossbow.feng.ast.oop;

import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.IdentifierMap;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.Symbol;
import org.cossbow.feng.ast.dcl.TypeDeclarer;
import org.cossbow.feng.ast.gen.TypeParameter;
import org.cossbow.feng.ast.gen.TypeParameters;
import org.cossbow.feng.util.Groups;

import java.util.List;

import static org.cossbow.feng.ast.Position.ZERO;

/**
 * Reference-counted array definition: {@code [&]T} (SRef) or {@code [&?]T} (PRef).
 * <p>
 * C struct: {@code { T* $value; Int64 $length; }}
 * <p>
 * No meta struct, no vtable.
 * SRef (phantom=false) needs cleanup, PRef (phantom=true) does not.
 */
public class ArrayRefDefinition extends BuiltinTypeDefinition {

    private static final Symbol SYMBOL_SREF = new Symbol(new Identifier("ArraySRef"));
    private static final Symbol SYMBOL_PREF = new Symbol(new Identifier("ArrayPRef"));

    private final TypeParameter elementParam;
    /**
     * Concrete element type when this is a monomorphized instance, else null.
     */
    private final TypeDeclarer elementType;
    private final boolean phantom;

    /**
     * Generic template form ({@code elementParam} + typeMap), used by the
     * legacy pass.
     */
    public ArrayRefDefinition(TypeParameter elementParam, boolean phantom) {
        this(phantom ? SYMBOL_PREF : SYMBOL_SREF, elementParam, null, phantom);
    }

    /**
     * Monomorphized form: unique symbol + concrete element type, no typeMap.
     */
    public ArrayRefDefinition(Symbol symbol, TypeDeclarer elementType, boolean phantom) {
        this(symbol, null, elementType, phantom);
    }

    private ArrayRefDefinition(Symbol symbol, TypeParameter elementParam,
                               TypeDeclarer elementType, boolean phantom) {
        super(ZERO, symbol, elementParam == null ? TypeParameters.empty()
                : new TypeParameters(ZERO,
                        new IdentifierMap<>(List.of(Groups.g2(elementParam.name(), elementParam)))));
        this.elementParam = elementParam;
        this.elementType = elementType;
        this.phantom = phantom;
    }

    public TypeParameter elementParam() {
        return elementParam;
    }

    public TypeDeclarer elementType() {
        return elementType;
    }

    public boolean phantom() {
        return phantom;
    }
}
