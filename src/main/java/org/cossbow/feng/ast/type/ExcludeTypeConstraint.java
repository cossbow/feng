package org.cossbow.feng.ast.type;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.TypeDomain;
import org.cossbow.feng.ast.dcl.GenericTypeDeclarer;
import org.cossbow.feng.ast.dcl.TypeDeclarer;

import java.util.EnumSet;
import java.util.Objects;

/**
 * Complement constraint {@code !C}: contains all types <em>not</em> in
 * the operand constraint.
 */
public class ExcludeTypeConstraint extends TypeConstraint {
    private final TypeConstraint operand;

    public ExcludeTypeConstraint(Position pos,
                                 TypeConstraint operand) {
        super(pos);
        this.operand = operand;
    }

    public TypeConstraint operand() {
        return operand;
    }

    @Override
    public boolean contains(TypeDeclarer t) {
        if (t instanceof GenericTypeDeclarer gtd) {
            return containsByConstraint(gtd);
        }
        return !operand.contains(t);
    }

    @Override
    public boolean include(TypeConstraint tc) {
        // 补集无上近似，剥壳 equals 除外保守 false
        return equals(normalize(tc));
    }

    @Override
    public Tri referenced() {
        // 补集投影取反。注意：仅当 operand 是纯引用性谓词时精确；
        // 对跨维度谓词（class 等），¬proj(operand) ≠ proj(!operand)，
        // 保守交由检查阶段。
        return Tri.not(operand.referenced());
    }

    @Override
    public MemberSet members() {
        // 补集在成员集维度上坍缩：无法保证任何公共成员。
        return MemberSet.empty();
    }

    @Override
    public Tri newable() {
        return Tri.not(operand.newable());
    }

    @Override
    public EnumSet<TypeDomain> domains() {
        // 域是有限 8 值集合，补集在此维度精确闭合。
        var r = allDomains();
        r.removeAll(operand.domains());
        return r;
    }

    //

    @Override
    public final boolean equals(Object o) {
        return o instanceof ExcludeTypeConstraint c
                && operand.equals(c.operand);

    }

    @Override
    public int hashCode() {
        return operand.hashCode();
    }

    //
    @Override
    public String toString() {
        return "!" + operand;
    }
}
