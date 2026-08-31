package org.cossbow.feng.ast.type;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.TypeDomain;
import org.cossbow.feng.ast.dcl.*;

import java.util.EnumSet;
import java.util.Objects;

public class DomainTypeConstraint extends TypeConstraint {
    private final TypeDomain domain;

    public DomainTypeConstraint(Position pos, TypeDomain domain) {
        super(pos);
        this.domain = domain;
    }

    public TypeDomain domain() {
        return domain;
    }

    @Override
    public boolean contains(TypeDeclarer t) {
        var d = domainOf(t);
        if (d != null) return d == domain;
        return t instanceof GenericTypeDeclarer gtd
                && containsByConstraint(gtd);
    }

    @Override
    public boolean include(TypeConstraint tc) {
        var x = normalize(tc);
        if (equals(x)) return true;
        // 域投影是可能域的上近似：tc ⊆ 单域 ⟺ tc.domains() ⊆ {domain}
        return domains().containsAll(x.domains());
    }

    private static TypeDomain domainOf(TypeDeclarer t) {
        if (t instanceof PrimitiveTypeDeclarer p) {
            return p.primitive().type().domain();
        }
        if (t instanceof DerivedTypeDeclarer d) {
            return d.def().domain();
        }
        if (t instanceof FuncTypeDeclarer) {
            return TypeDomain.FUNC;
        }
        return null;
    }

    @Override
    public Tri referenced() {
        return switch (domain) {
            case PRIMITIVE, STRUCT, UNION, ENUM -> Tri.NO;   // 值类型
            case INTERFACE, FUNC -> Tri.YES;                 // 引用类型
            case CLASS, ATTRIBUTE -> Tri.BOTH;               // class 可值可引用 / attribute 不参与二分
        };
    }

    @Override
    public MemberSet members() {
        return MemberSet.empty();
    }

    @Override
    public Tri newable() {
        // 域约束不指定具体类型，newable 信息未知
        //（对应原 TypeView.domain() 工厂里 newable 恒为 null）。
        return Tri.BOTH;
    }

    @Override
    public EnumSet<TypeDomain> domains() {
        return EnumSet.of(domain);
    }

    //

    @Override
    public final boolean equals(Object o) {
        return o instanceof DomainTypeConstraint c
                && domain == c.domain;

    }

    @Override
    public int hashCode() {
        return domain.hashCode();
    }

    //
    @Override
    public String toString() {
        return String.valueOf(domain);
    }
}
