package org.cossbow.feng.ast.oop;

import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.IdentifierMap;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.Symbol;
import org.cossbow.feng.ast.dcl.TypeDeclarer;
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
    /**
     * Concrete element types when this is a monomorphized instance, else null.
     */
    private final List<TypeDeclarer> elementTypes;

    /**
     * Generic template form ({@code elementParams} + typeMap), used by the
     * legacy pass.
     */
    public TupleDefinition(List<TypeParameter> elementParams) {
        this(new Symbol(new Identifier("Tuple" + elementParams.size())),
                elementParams, null);
    }

    /**
     * Monomorphized form: unique symbol + concrete element types, no typeMap.
     */
    public TupleDefinition(Symbol symbol, List<TypeDeclarer> elementTypes) {
        this(symbol, null, elementTypes);
    }

    private TupleDefinition(Symbol symbol, List<TypeParameter> elementParams,
                            List<TypeDeclarer> elementTypes) {
        super(ZERO, symbol, elementParams == null ? TypeParameters.empty()
                : new TypeParameters(ZERO, new IdentifierMap<>(
                        elementParams.stream()
                                .map(tp -> Groups.g2(tp.name(), tp))
                                .toList())));
        this.elementParams = elementParams == null ? List.of() : new ArrayList<>(elementParams);
        this.elementTypes = elementTypes == null ? null : new ArrayList<>(elementTypes);
    }

    public List<TypeParameter> elementParams() {
        return elementParams;
    }

    public List<TypeDeclarer> elementTypes() {
        return elementTypes;
    }

    public int arity() {
        return elementTypes != null ? elementTypes.size() : elementParams.size();
    }
}
