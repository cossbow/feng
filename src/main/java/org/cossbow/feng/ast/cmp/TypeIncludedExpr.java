package org.cossbow.feng.ast.cmp;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.dcl.TypeDeclarer;
import org.cossbow.feng.ast.gen.GenericMap;
import org.cossbow.feng.ast.type.TypeConstraint;

/**
 * 类型判断 {@code type <: constraint}：
 * 左侧类型是否满足右侧约束（{@link TypeConstraint#contains}）。
 * <p>
 * 约束侧可含类型变量（如 {@code R <: T} 的 {@code T}）：
 * {@code contains} 对约束位置的类型变量按「模板级声明约束」求值
 * （{@link org.cossbow.feng.ast.type.DefinedTypeConstraint} 的 GenericType
 * 分支委托 {@code param().constraint()}），与实例化无关，故 mono 只映射左侧。
 */
public class TypeIncludedExpr extends StaticExpr {
    private TypeDeclarer type;
    private TypeConstraint constraint;

    public TypeIncludedExpr(Position pos,
                            TypeDeclarer type,
                            TypeConstraint constraint) {
        super(pos);
        this.type = type;
        this.constraint = constraint;
    }

    public TypeDeclarer type() {
        return type;
    }

    public TypeConstraint constraint() {
        return constraint;
    }

    /**
     * 分析期把已解析的操作数写回（类型解析、概念约束解析），
     * 保证 mono 期裁决与分析期看到的是同一约束。
     */
    public void type(TypeDeclarer type) {
        this.type = type;
    }

    public void constraint(TypeConstraint constraint) {
        this.constraint = constraint;
    }

    @Override
    public StaticExpr mono(GenericMap gm) {
        return new TypeIncludedExpr(pos(), gm.mapIf(type), constraint);
    }

    @Override
    public boolean hasTypeVar() {
        return type.hasTypeVar();
    }

    @Override
    public boolean check() {
        return constraint.contains(type);
    }

    @Override
    public String toString() {
        return type + " <: " + constraint;
    }
}
