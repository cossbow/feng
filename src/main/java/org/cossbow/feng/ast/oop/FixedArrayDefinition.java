package org.cossbow.feng.ast.oop;

import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.IdentifierMap;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.Symbol;
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
    private final int length;

    public FixedArrayDefinition(TypeParameter elementParam, int length) {
        super(ZERO, SYMBOL, new TypeParameters(ZERO,
                new IdentifierMap<>(List.of(Groups.g2(elementParam.name(), elementParam)))));
        this.elementParam = elementParam;
        this.length = length;
    }

    public TypeParameter elementParam() {
        return elementParam;
    }

    public int length() {
        return length;
    }
}
