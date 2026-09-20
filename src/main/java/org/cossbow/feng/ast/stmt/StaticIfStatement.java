package org.cossbow.feng.ast.stmt;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.gen.GenericMap;
import org.cossbow.feng.ast.cmp.StaticExpr;
import org.cossbow.feng.util.Optional;

import java.util.List;

/**
 * 编译期执行语句：{@code static if (staticExpr) block (else ...)?}。
 * <p>
 * 分析期：条件与两个分支全部分析检查（死分支同样报错）；
 * 仅优化条件（可判定 → {@link org.cossbow.feng.ast.cmp.ConstStaticExpr}），
 * 节点保留、由 mono 期统一裁决（常量与泛型条件一视同仁）。
 * <p>
 * mono 期：{@link #mono(GenericMap)} 映射条件后裁决，返回选中的分支
 * （blockStatement 包成 {@link BlockStatement}，保留分支作用域，不直接展开拼接）。
 */
public class StaticIfStatement extends Statement {
    private StaticExpr condition;
    private BlockStatement yes;
    private Optional<Statement> not;

    public StaticIfStatement(Position pos,
                             StaticExpr condition,
                             BlockStatement yes,
                             Optional<Statement> not) {
        super(pos);
        this.condition = condition;
        this.yes = yes;
        this.not = not;
    }

    public StaticExpr condition() {
        return condition;
    }

    /**
     * 分析期写回优化后的条件（可判定 → {@link org.cossbow.feng.ast.cmp.ConstStaticExpr}）。
     */
    public void condition(StaticExpr condition) {
        this.condition = condition;
    }

    public BlockStatement yes() {
        return yes;
    }

    /**
     * 分支收窄：写回重建后的真分支（{@code mono(narrowGm)} 产物），
     * 另一分支与模板声明不受影响。
     */
    public void yes(BlockStatement yes) {
        this.yes = yes;
    }

    public Optional<Statement> not() {
        return not;
    }

    /**
     * 分支收窄：写回重建后的假分支（{@code mono(narrowGm)} 产物）。
     */
    public void not(Optional<Statement> not) {
        this.not = not;
    }

    @Override
    public StaticIfStatement mirror() {
        return new StaticIfStatement(pos(), condition,
                yes.mirror(), not.map(Statement::mirror));
    }

    @Override
    public Statement mono(GenericMap gm) {
        boolean c = condition.mono(gm).check();
        if (c) {
            return yes.mono(gm);
        }
        if (not.none()) {
            return new BlockStatement(pos(), List.of());
        }
        return not.get().mono(gm);
    }
}
