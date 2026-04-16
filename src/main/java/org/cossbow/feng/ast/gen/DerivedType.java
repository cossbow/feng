package org.cossbow.feng.ast.gen;

import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.Symbol;
import org.cossbow.feng.ast.TypeDefinition;
import org.cossbow.feng.util.CommonUtil;

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

    private TypeDefinition def;
    private GenericMap gm = GenericMap.EMPTY;

    public TypeDefinition def() {
        return CommonUtil.required(def);
    }

    public void def(TypeDefinition def) {
        this.def = CommonUtil.required(def);
    }

    public GenericMap gm() {
        return CommonUtil.required(gm);
    }

    public void gm(GenericMap gm) {
        this.gm = CommonUtil.required(gm);
    }

    public boolean hasTemplate() {
        return generic.hasTemplate();
    }

    public DerivedType clone() {
        return (DerivedType) super.clone();
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
