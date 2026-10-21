package org.cossbow.feng.ast.type;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.Symbol;
import org.cossbow.feng.ast.TypeDomain;
import org.cossbow.feng.ast.dcl.GenericTypeDeclarer;
import org.cossbow.feng.ast.dcl.TypeDeclarer;

import java.util.EnumSet;

/**
 * Constraint that matches reference types ({@code *any}).
 * <p>
 * A type satisfies this constraint if it is a reference type
 * ({@link TypeDeclarer#maybeRefer()} returns present).
 */
public class ReferTypeConstraint extends TypeConstraint {
    public ReferTypeConstraint(Position pos) {
        super(pos);
    }

    @Override
    public boolean contains(TypeDeclarer t) {
        if (t.maybeRefer().has()) return true;
        return t instanceof GenericTypeDeclarer gtd
                && containsByConstraint(gtd);
    }

    @Override
    public boolean include(TypeConstraint tc) {
        var x = normalize(tc);
        if (equals(x)) return true;
        // 全 BOTH 化后 referenced() 失去精度，改用域判据：
        // interface 必引用，其域 ⊆ {INTERFACE} → 集合中每个元素都是引用 → 全部命中 *any
        var ds = x.domains();
        return !ds.isEmpty()
                && ds.stream().allMatch(d -> d == TypeDomain.INTERFACE);
    }

    @Override
    public Tri referenced() {
        return Tri.YES;
    }

    @Override
    public Tri optional() {
        return Tri.BOTH;
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
        // 引用标记不保证任何运算符（class 是否实现运算符未知）→ 空
        return OperatorSet.empty();
    }

    //

    @Override
    public boolean equals(Object c) {
        return c instanceof ReferTypeConstraint;
    }

    @Override
    public int hashCode() {
        return 1000;
    }

    //
    @Override
    public String toString() {
        return "*";
    }
}
