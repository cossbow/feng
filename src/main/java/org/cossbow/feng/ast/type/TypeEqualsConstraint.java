package org.cossbow.feng.ast.type;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.Symbol;
import org.cossbow.feng.ast.TypeDomain;
import org.cossbow.feng.ast.dcl.DerivedTypeDeclarer;
import org.cossbow.feng.ast.dcl.GenericTypeDeclarer;
import org.cossbow.feng.ast.dcl.TypeDeclarer;

import java.util.EnumSet;

/**
 * 精确相等约束 {@code == T}：匹配与 {@code T} 结构化相等的类型。
 * <p>
 * 由 {@code static if (T == X)} 分支收窄生成：真分支约束 = 原约束 ∩ {@code == X}，
 * 假分支 = 原约束 ∩ {@code !(== X)}。
 * <p>
 * 与 {@link DefinedTypeConstraint} 的关键区别：后者是「子类型包含」语义
 * （{@code contains} 走 def 相等 + {@code supers()} 继承链，同定义的所有实例
 * 全部命中）；本约束是「精确相等」语义（{@code contains} = 结构化
 * {@link Object#equals}，含类型实参递归），保证
 * {@code == List`int`} 只收 {@code List`int`}，不收 {@code List`Bus`} 或子类。
 * <p>
 * 右侧可含模板参数（如 {@code T == List`S`}）：{@code contains} 对
 * {@code List`int`} 返回 false（{@code int != S}），S 是模板参数、由收窄
 * gm 的兄弟映射在 mono 期具体化，本约束仅作集合谓词。
 */
public class TypeEqualsConstraint extends TypeConstraint {
    private final TypeDeclarer type;

    public TypeEqualsConstraint(Position pos, TypeDeclarer type) {
        super(pos);
        this.type = type;
    }

    public TypeDeclarer type() {
        return type;
    }

    @Override
    public boolean contains(TypeDeclarer t) {
        if (t instanceof GenericTypeDeclarer gtd) {
            // 类型变量分支：委托 include 做集合包含（U==X 对 U 的约束集）
            return containsByConstraint(gtd);
        }
        return type.equals(t);
    }

    @Override
    public boolean include(TypeConstraint tc) {
        // 精确单例集合的子集只有自身（或空），剥壳 equals 除外保守 false
        return equals(normalize(tc));
    }

    @Override
    public Tri referenced() {
        return Tri.BOTH;
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
        // 分支内可用的成员 = 右侧类型的具体成员（经其泛型映射实例化）
        return MemberSet.of(type);
    }

    @Override
    public Tri newable() {
        return newableOf(type);
    }

    private static Tri newableOf(TypeDeclarer t) {
        if (t instanceof DerivedTypeDeclarer dtd)
            return dtd.def().newable() ? Tri.YES : Tri.NO;
        if (t instanceof GenericTypeDeclarer gtd) {
            var c = gtd.param().constraint();
            return c.none() ? Tri.BOTH : c.get().newable();
        }
        return Tri.BOTH;
    }

    @Override
    public EnumSet<TypeDomain> domains() {
        if (type instanceof DerivedTypeDeclarer dtd) {
            var dt = dtd.derivedType();
            return dt.analyzed()
                    ? EnumSet.of(dt.def().domain())
                    : allDomains();
        }
        return allDomains();
    }

    @Override
    public Tri hasAttr(Symbol attr) {
        if (type instanceof DerivedTypeDeclarer dtd)
            return dtd.def().modifier().attributes().exists(attr)
                    ? Tri.YES : Tri.NO;
        return Tri.BOTH;
    }

    @Override
    public Tri has(Concept c) {
        if (type instanceof GenericTypeDeclarer gtd) {
            var oc = gtd.param().constraint();
            return oc.none() ? Tri.BOTH : oc.get().has(c);
        }
        return Tri.NO;
    }

    @Override
    public OperatorSet operators() {
        return OperatorSet.of(type);
    }

    //

    @Override
    public boolean equals(Object o) {
        return o instanceof TypeEqualsConstraint c
                && type.equals(c.type);
    }

    @Override
    public int hashCode() {
        return type.hashCode();
    }

    //
    @Override
    public String toString() {
        return "==" + type;
    }
}
