package org.cossbow.feng.ast.dcl;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.gen.DerivedType;
import org.cossbow.feng.ast.proc.Prototype;
import org.cossbow.feng.ast.proc.PrototypeDefinition;
import org.cossbow.feng.util.Lazy;

import java.util.Objects;


public class NamedFuncTypeDeclarer extends FuncTypeDeclarer {
    private DerivedType derivedType;
    private Lazy<PrototypeDefinition> def;

    public NamedFuncTypeDeclarer(Position pos,
                                 boolean required,
                                 DerivedType derivedType,
                                 Lazy<PrototypeDefinition> def) {
        super(pos, required);
        this.derivedType = derivedType;
        this.def = def;
    }

    public NamedFuncTypeDeclarer(Position pos,
                                 boolean required,
                                 DerivedType derivedType) {
        this(pos, required, derivedType, Lazy.nil());
    }

    public DerivedType derivedType() {
        return derivedType;
    }

    public Lazy<PrototypeDefinition> def() {
        return def;
    }


    private Prototype prototype;
    ;

    public Prototype prototype() {
        return Objects.requireNonNull(prototype);
    }

    public void prototype(Prototype prototype) {
        this.prototype = prototype;
    }

    //
    @Override
    public boolean equals(Object o) {
        if (!(o instanceof NamedFuncTypeDeclarer t))
            return false;
        return required() == t.required() &&
                derivedType.equals(t.derivedType);
    }

    @Override
    public int hashCode() {
        return Boolean.hashCode(required()) * 31
                + derivedType.hashCode();
    }

    //
    @Override
    public String toString() {
        if (required()) return "!" + derivedType;
        return derivedType.toString();
    }
}
