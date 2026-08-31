package org.cossbow.feng.ast.type;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.Symbol;
import org.cossbow.feng.ast.attr.Attribute;
import org.cossbow.feng.ast.dcl.DerivedTypeDeclarer;
import org.cossbow.feng.ast.dcl.TypeDeclarer;

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
        return false;
    }

    @Override
    public TypeView view() {
        return TypeView.attribute(attribute);
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
