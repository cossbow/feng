package org.cossbow.feng.ast.oop;

import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.IdentifierMap;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.Symbol;
import org.cossbow.feng.ast.gen.TypeParameter;
import org.cossbow.feng.ast.gen.TypeParameters;
import org.cossbow.feng.util.Groups;

import java.util.ArrayList;
import java.util.List;

import static org.cossbow.feng.ast.Position.ZERO;

/**
 * Tuple definition: {@code (T0, T1, ...)} (value type).
 * <p>
 * C struct: {@code { T0 v0; T1 v1; ... }}
 * <p>
 * No meta struct, no vtable, no cleanup.
 * Element index maps directly to field index: elements[0] → v0, elements[1] → v1.
 */
public class TupleDefinition extends BuiltinTypeDefinition {

    private final List<TypeParameter> elementParams;

    public TupleDefinition(List<TypeParameter> elementParams) {
        super(ZERO, new Symbol(new Identifier("Tuple" + elementParams.size())),
                new TypeParameters(ZERO, new IdentifierMap<>(
                        elementParams.stream()
                                .map(tp -> Groups.g2(tp.name(), tp))
                                .toList())));
        this.elementParams = new ArrayList<>(elementParams);
    }

    public List<TypeParameter> elementParams() {
        return elementParams;
    }

    public int arity() {
        return elementParams.size();
    }
}
