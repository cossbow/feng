package org.cossbow.feng.ast.type;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.dcl.DerivedTypeDeclarer;
import org.cossbow.feng.ast.dcl.GenericTypeDeclarer;
import org.cossbow.feng.ast.dcl.PrimitiveTypeDeclarer;
import org.cossbow.feng.ast.dcl.TypeDeclarer;
import org.cossbow.feng.ast.gen.DefinedType;
import org.cossbow.feng.ast.gen.DerivedType;
import org.cossbow.feng.ast.gen.GenericType;
import org.cossbow.feng.ast.gen.PrimitiveType;
import org.cossbow.feng.ast.oop.ObjectDefinition;
import org.cossbow.feng.util.Optional;

import java.util.Objects;

/**
 * Constraint that matches a specific {@link DefinedType} (e.g. {@code int}, {@code Bus}).
 * <p>
 * During semantic analysis the {@link DefinedType} is resolved to a concrete
 * {@link org.cossbow.feng.ast.dcl.TypeDeclarer} via the symbol table.
 * Before resolution, {@link #contains} and {@link #view} return
 * conservative defaults (no match).
 */
public class DefinedTypeConstraint extends TypeConstraint {
    private DefinedType definedType;

    public DefinedTypeConstraint(Position pos,
                                 DefinedType definedType) {
        super(pos);
        this.definedType = definedType;
    }

    public DefinedType definedType() {
        return definedType;
    }

    public void definedType(DefinedType definedType) {
        this.definedType = definedType;
    }

    /**
     * Check if {@code t} satisfies this constraint.
     * <p>
     * The check follows subtyping:
     * - Direct type equals: {@code resolved.equals(t)}.
     * - Subtype (class extends / interface implements): iff both {@code resolved} and
     * {@code t} are {@link DerivedTypeDeclarer}s pointing to {@link ObjectDefinition}s,
     * walk the {@code supers()} chain of {@code t} and check whether any
     * super-type resolves to the same type definition as {@code resolved}.
     */
    @Override
    public boolean contains(TypeDeclarer t) {
        if (definedType instanceof PrimitiveType pt) {
            return t instanceof PrimitiveTypeDeclarer ptd
                    && ptd.primitive() == pt.primitive();
        }

        if (definedType instanceof GenericType gt) {
            if (t instanceof GenericTypeDeclarer gtd) {
                return gtd.type().param().equals(gt.param());
            }
            // Continue matching using the constraints of
            // the referenced type variable
            var oc = gt.param().constraint();
            return oc.none() || oc.get().contains(t);
        }

        if (!(definedType instanceof DerivedType dt)) {
            return false;
        }
        if (!(t instanceof DerivedTypeDeclarer dtd)) {
            return false;
        }

        var rDef = dt.def();
        var tDef = dtd.def();
        if (rDef.equals(tDef)) return true;

        if (!(rDef instanceof ObjectDefinition)) return false;
        if (!(tDef instanceof ObjectDefinition)) return false;

        var tas = ObjectTool.checkInherited(
                dt, dtd.derivedType());

        return tas.has();
    }

    @Override
    public TypeView view() {
        if (definedType instanceof PrimitiveType pt) {
            return TypeView.of(pt.declarer(Optional.empty()));
        }
        if (definedType instanceof DerivedType dt) {
            return TypeView.of(dt.declarer(Optional.empty()));
        }
        var gt = (GenericType) definedType;
        if (gt.param().constraint().none())
            return TypeView.unknown();
        return gt.param().constraint().get().view();
    }

    //


    @Override
    public final boolean equals(Object o) {
        return o instanceof DefinedTypeConstraint c
                && definedType.equals(c.definedType);

    }

    @Override
    public int hashCode() {
        return definedType.hashCode();
    }

    //
    @Override
    public String toString() {
        return definedType.toString();
    }
}
