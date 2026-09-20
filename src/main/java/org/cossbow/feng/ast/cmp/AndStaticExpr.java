package org.cossbow.feng.ast.cmp;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.gen.GenericMap;

/**
 * 编译期布尔与：{@code staticExpr AND staticExpr}。
 */
public class AndStaticExpr extends StaticExpr {
    private final StaticExpr left, right;

    public AndStaticExpr(Position pos,
                         StaticExpr left,
                         StaticExpr right) {
        super(pos);
        this.left = left;
        this.right = right;
    }

    public StaticExpr left() {
        return left;
    }

    public StaticExpr right() {
        return right;
    }

    @Override
    public StaticExpr mono(GenericMap gm) {
        return new AndStaticExpr(pos(), left.mono(gm), right.mono(gm));
    }

    @Override
    public boolean hasTypeVar() {
        return left.hasTypeVar() || right.hasTypeVar();
    }

    @Override
    public boolean check() {
        return left.check() && right.check();
    }

    @Override
    public String toString() {
        return "(" + left + " && " + right + ")";
    }
}
