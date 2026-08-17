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
 * Fixed-length array definition: {@code [N]T} (value type).
 * <p>
 * C struct: {@code { T $value[N]; Int64 $length; }}
 * <p>
 * No meta struct, no vtable, no cleanup needed.
 */
public class FixedArrayDefinition extends BuiltinTypeDefinition {

    private static final Symbol SYMBOL = new Symbol(new Identifier("Array"));

    private final TypeParameter elementParam;
    /**
     * Concrete element type when this is a monomorphized instance, else null.
     */
    private final TypeDeclarer elementType;
    private final int length;

    /**
     * Generic template form ({@code elementParam} + typeMap), used by the
     * legacy pass.
     */
    public FixedArrayDefinition(TypeParameter elementParam, int length) {
        this(SYMBOL, elementParam, null, length);
    }

    /**
     * Monomorphized form: unique symbol + concrete element type, no typeMap.
     */
    public FixedArrayDefinition(Symbol symbol, TypeDeclarer elementType, int length) {
        this(symbol, null, elementType, length);
    }

    private FixedArrayDefinition(Symbol symbol, TypeParameter elementParam,
                                 TypeDeclarer elementType, int length) {
        super(ZERO, symbol, elementParam == null ? TypeParameters.empty()
                : new TypeParameters(ZERO,
                        new IdentifierMap<>(List.of(Groups.g2(elementParam.name(), elementParam)))));
        this.elementParam = elementParam;
        this.elementType = elementType;
        this.length = length;
    }

    public TypeParameter elementParam() {
        return elementParam;
    }

    public TypeDeclarer elementType() {
        return elementType;
    }

    public int length() {
        return length;
    }
}
