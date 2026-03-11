package org.cossbow.feng.ast.gen;

import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.Symbol;
import org.cossbow.feng.ast.TypeDefinition;
import org.cossbow.feng.util.Lazy;

import java.util.Objects;

public class DerivedType extends DefinedType {
    private final Symbol symbol;
    private TypeArguments generic;

    public DerivedType(Position pos,
                       Symbol symbol,
                       TypeArguments generic) {
        super(pos);
        this.symbol = symbol;
        this.generic = generic;
    }

    public Symbol symbol() {
        return symbol;
    }

    public TypeArguments generic() {
        return generic;
    }

    public void generic(TypeArguments generic) {
        this.generic = generic;
    }

    public Identifier name() {
        return symbol.name();
    }

    //

    public final Lazy<TypeDefinition> def = Lazy.nil();
    private GenericMap gm = GenericMap.EMPTY;

    public GenericMap gm() {
        return gm;
    }

    public void gm(GenericMap gm) {
        this.gm = Objects.requireNonNull(gm);
    }

    public boolean hasTemplate() {
        return generic.hasTemplate();
    }

    //

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof DerivedType t))
            return false;
        return symbol.equals(t.symbol) &&
                generic.equals(t.generic);
    }

    @Override
    public int hashCode() {
        return 31 * symbol.hashCode() + generic.hashCode();
    }

    //


    @Override
    public String toString() {
        return generic.isEmpty() ? symbol.toString() :
                symbol.toString() + generic;
    }
}
