package org.cossbow.feng.ast.type;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.TypeDomain;
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

import java.util.EnumSet;

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
        if (t instanceof GenericTypeDeclarer gtd) {
            // 类型变量分支（§3.4）：同一 param 恒真（U 对 U 自身，即使无约束）；
            // 否则以其约束集合做包含判定
            if (definedType instanceof GenericType gt
                    && gtd.type().equals(gt)) {
                return true;
            }
            return containsByConstraint(gtd);
        }

        if (definedType instanceof PrimitiveType pt) {
            return t instanceof PrimitiveTypeDeclarer ptd
                    && ptd.primitive() == pt.primitive();
        }

        if (definedType instanceof GenericType gt) {
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

        var tas = TypeTool.checkInherited(
                dt, dtd.derivedType());

        return tas.has();
    }

    @Override
    public boolean include(TypeConstraint tc) {
        var x = normalize(tc);
        if (equals(x)) return true;

        if (definedType instanceof GenericType gt) {
            // C_U 无约束 = 全集 → true；否则 C_U.include(x)
            var oc = gt.param().constraint();
            return oc.none() || oc.get().include(x);
        }

        if (!(x instanceof DefinedTypeConstraint dc)) {
            return false;
        }
        if (definedType instanceof PrimitiveType) {
            // 单例 {p} 的子集只有 {p}（已由 equals 命中）或空
            return false;
        }
        if (definedType instanceof DerivedType dt
                && dc.definedType() instanceof DerivedType d2) {
            // 单类型约束降维复用 contains(TypeDeclarer)（含 supers() 继承链）
            return contains(d2.declarer(Optional.empty()));
        }
        return false;
    }

    @Override
    public Tri referenced() {
        if (definedType instanceof PrimitiveType) {
            return Tri.NO;      // 原始类型是值
        }
        if (definedType instanceof GenericType gt) {
            // 同胞参数引用：委托其自身约束
            var oc = gt.param().constraint();
            return oc.none() ? Tri.BOTH : oc.get().referenced();
        }
        if (definedType instanceof DerivedType dt) {
            // 具体类型约束表示「裸类型符号」：class/struct/... 的裸形态即值
            return switch (dt.def().domain()) {
                case PRIMITIVE, STRUCT, UNION, ENUM, CLASS -> Tri.NO;
                case INTERFACE, FUNC -> Tri.YES;
                case ATTRIBUTE -> Tri.BOTH;
            };
        }
        return Tri.BOTH;
    }

    @Override
    public MemberSet members() {
        if (definedType instanceof PrimitiveType) {
            return MemberSet.empty();
        }
        if (definedType instanceof GenericType gt) {
            var oc = gt.param().constraint();
            return oc.none() ? MemberSet.empty() : oc.get().members();
        }
        if (definedType instanceof DerivedType dt) {
            return MemberSet.of(dt.declarer(Optional.empty()));
        }
        return MemberSet.empty();
    }

    @Override
    public Tri newable() {
        if (definedType instanceof PrimitiveType) {
            return Tri.YES;     // 原始类型可 new
        }
        if (definedType instanceof GenericType gt) {
            var oc = gt.param().constraint();
            return oc.none() ? Tri.BOTH : oc.get().newable();
        }
        if (definedType instanceof DerivedType dt) {
            return dt.def().newable() ? Tri.YES : Tri.NO;
        }
        return Tri.BOTH;
    }

    @Override
    public EnumSet<TypeDomain> domains() {
        if (definedType instanceof PrimitiveType) {
            return EnumSet.of(TypeDomain.PRIMITIVE);
        }
        if (definedType instanceof GenericType gt) {
            var oc = gt.param().constraint();
            return oc.none() ? allDomains() : oc.get().domains();
        }
        if (definedType instanceof DerivedType dt) {
            return EnumSet.of(dt.def().domain());
        }
        return allDomains();
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
