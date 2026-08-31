package org.cossbow.feng.ast.type;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.Symbol;
import org.cossbow.feng.ast.TypeDomain;
import org.cossbow.feng.ast.attr.Attribute;
import org.cossbow.feng.ast.dcl.DerivedTypeDeclarer;
import org.cossbow.feng.ast.dcl.GenericTypeDeclarer;
import org.cossbow.feng.ast.dcl.TypeDeclarer;

import java.util.EnumSet;

/**
 * Constraint that matches types carrying a specific attribute (e.g. {@code @Pack}).
 * <p>
 * During semantic analysis the presence of the attribute is checked on the
 * resolved {@link TypeDeclarer}.
 */
public class AttributeTypeConstraint extends TypeConstraint {
    private final Attribute attribute;

    public AttributeTypeConstraint(Position pos,
                                   Attribute attribute) {
        super(pos);
        this.attribute = attribute;
    }

    public Attribute attribute() {
        return attribute;
    }


    @Override
    public boolean contains(TypeDeclarer t) {
        if (t instanceof DerivedTypeDeclarer td)
            return td.def().modifier().attributes()
                    .exists(attribute.type());
        return t instanceof GenericTypeDeclarer gtd
                && containsByConstraint(gtd);
    }

    @Override
    public boolean include(TypeConstraint tc) {
        // 属性维度无上近似投影，剥壳 equals 除外保守 false
        return equals(normalize(tc));
    }

    @Override
    public Tri referenced() {
        // 属性不约束引用性
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

    //


    @Override
    public boolean equals(Object o) {
        return o instanceof AttributeTypeConstraint c
                && attribute.equals(c.attribute);
    }

    @Override
    public int hashCode() {
        return attribute.hashCode();
    }

    //
    @Override
    public String toString() {
        return attribute.toString();
    }
}
