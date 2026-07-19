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
    private final boolean phantom;

    public ArrayRefDefinition(TypeParameter elementParam, boolean phantom) {
        super(ZERO, phantom ? SYMBOL_PREF : SYMBOL_SREF,
                new TypeParameters(ZERO,
                        new IdentifierMap<>(List.of(Groups.g2(elementParam.name(), elementParam)))));
        this.elementParam = elementParam;
        this.phantom = phantom;
    }

    public TypeParameter elementParam() {
        return elementParam;
    }

    public boolean phantom() {
        return phantom;
    }
}
