package org.cossbow.feng.ast.cmp;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.gen.GenericMap;

/**
 * 编译期布尔取反：{@code !staticExpr}。
 */
public class NotStaticExpr extends StaticExpr {
    private final StaticExpr expr;

    public NotStaticExpr(Position pos, StaticExpr expr) {
        super(pos);
        this.expr = expr;
    }

    public StaticExpr expr() {
        return expr;
    }

    @Override
    public StaticExpr mono(GenericMap gm) {
        return new NotStaticExpr(pos(), expr.mono(gm));
    }

    @Override
    public boolean hasTypeVar() {
        return expr.hasTypeVar();
    }

    @Override
    public boolean check() {
        return !expr.check();
    }

    @Override
    public String toString() {
        return "!(" + expr + ")";
    }
}
