package org.cossbow.feng.ast.cmp;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.dcl.TypeDeclarer;
import org.cossbow.feng.ast.gen.GenericMap;

/**
 * 类型判断 {@code left == right}：
 * 两侧类型结构化相等（{@link Object#equals}）。
 */
public class TypeEqualsExpr extends StaticExpr {
    private TypeDeclarer left, right;

    public TypeEqualsExpr(Position pos,
                          TypeDeclarer left,
                          TypeDeclarer right) {
        super(pos);
        this.left = left;
        this.right = right;
    }

    public TypeDeclarer left() {
        return left;
    }

    public TypeDeclarer right() {
        return right;
    }

    /**
     * 分析期把已解析的操作数写回（如原型类型 → 具名函数类型），
     * 保证 mono 期裁决与分析期看到的是同一类型。
     */
    public void left(TypeDeclarer left) {
        this.left = left;
    }

    public void right(TypeDeclarer right) {
        this.right = right;
    }

    @Override
    public StaticExpr mono(GenericMap gm) {
        return new TypeEqualsExpr(pos(), gm.mapIf(left), gm.mapIf(right));
    }

    @Override
    public boolean hasTypeVar() {
        return left.hasTypeVar() || right.hasTypeVar();
    }

    @Override
    public boolean check() {
        return left.equals(right);
    }

    @Override
    public String toString() {
        return left + " == " + right;
    }
}
