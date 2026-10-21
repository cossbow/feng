package org.cossbow.feng.ast.type;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.Symbol;
import org.cossbow.feng.ast.TypeDomain;
import org.cossbow.feng.ast.dcl.FuncTypeDeclarer;
import org.cossbow.feng.ast.dcl.GenericTypeDeclarer;
import org.cossbow.feng.ast.dcl.TypeDeclarer;

import java.util.EnumSet;

/**
 * Constraint {@code ?}: the type argument must be an optional reference
 * (a reference that may be nil).
 */
public class OptionalTypeConstraint extends TypeConstraint {
    public OptionalTypeConstraint(Position pos) {
        super(pos);
    }

    @Override
    public boolean contains(TypeDeclarer t) {
        var r = t.maybeRefer();
        if (r.has() && !r.get().required()) return true;
        // 可空 func 原型（func 非引用，可空性由 required() 表达）
        if (t instanceof FuncTypeDeclarer f && !f.required()) return true;
        return t instanceof GenericTypeDeclarer gtd
                && containsByConstraint(gtd);
    }

    @Override
    public boolean include(TypeConstraint tc) {
        // 可空维度无上近似投影，剥壳 equals 除外保守 false
        return equals(normalize(tc));
    }

    @Override
    public Tri referenced() {
        // 可空引用：本身仍是引用
        return Tri.BOTH;
    }

    @Override
    public Tri optional() {
        return Tri.YES;
    }

    @Override
    public Tri unmodifiable() {
        return Tri.BOTH;
    }

    @Override
    public MemberSet members() {
        return MemberSet.empty();
    }

    @Override
    public Tri newable() {
        return Tri.BOTH;
    }

    @Override
    public EnumSet<TypeDomain> domains() {
        return allDomains();
    }

    @Override
    public Tri hasAttr(Symbol attr) {
        return Tri.BOTH;
    }

    @Override
    public Tri has(Concept c) {
        return Tri.BOTH;
    }

    @Override
    public OperatorSet operators() {
        // 可空标记不保证任何运算符 → 空
        return OperatorSet.empty();
    }

    //

    @Override
    public boolean equals(Object c) {
        return c instanceof OptionalTypeConstraint;
    }

    @Override
    public int hashCode() {
        return 2000;
    }

    //
    @Override
    public String toString() {
        return "?";
    }
}
