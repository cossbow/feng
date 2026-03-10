package org.cossbow.feng.analysis;

import org.cossbow.feng.ast.lit.BoolLiteral;
import org.cossbow.feng.ast.stmt.*;
import org.cossbow.feng.util.Optional;

import java.util.List;

import static org.cossbow.feng.util.ErrorUtil.semantic;
import static org.cossbow.feng.util.ErrorUtil.unreachable;

/**
 * 用于分析函数体是否缺失返回值的工具
 */
public class ReturnAnalyzer {

    public ReturnAnalyzer() {
    }

    //

    private boolean check(Optional<Statement> o) {
        return o.has() && check(o.get());
    }

    public boolean check(Statement s) {
        return switch (s) {
            case BlockStatement ee -> check(ee);
            case ForStatement ee -> check(ee);
            case IfStatement ee -> check(ee);
            case LabeledStatement ee -> check(ee.target());
            case SwitchStatement ee -> check(ee);
            case TryStatement ee -> check(ee);
            case ThrowStatement ee -> true;
            case ReturnStatement ee -> true;
            case null, default -> false;
        };
    }

    private boolean check(List<Statement> list) {
        var found = false;
        for (var s : list) {
            if (found) {
                semantic("unreachable statement: %s", s.pos());
            } else if (check(s)) {
                found = true;
            }
        }
        return found;
    }

    private boolean check(BlockStatement bs) {
        return check(bs.list());
    }

    private boolean check(ForStatement s) {
        return switch (s) {
            case ConditionalForStatement fs -> check(fs);
            case IterableForStatement fs -> check(fs);
            case null, default -> unreachable();
        };
    }

    private boolean check(ConditionalForStatement s) {
        return s.cond().match(BoolLiteral::value);
    }

    private boolean check(IterableForStatement s) {
        return false;
    }

    private boolean check(IfStatement s) {
        if (s.cond().has()) {
            if (s.cond().must().value()) {
                return check(s.yes());
            } else {
                return check(s.not());
            }
        }
        return check(s.yes()) && check(s.not());
    }

    private boolean check(SwitchStatement s) {
        var found = true;
        for (var b : s.branches()) {
            found = found && check(b.body());
        }
        if (s.defaultBranch().none()) return found;

        return found && check(s.defaultBranch().get().body());
    }

    private boolean check(TryStatement s) {
        var found = check(s.body());
        for (var c : s.catchClauses()) {
            found = found && check(c.body());
        }
        if (found) return true;
        if (s.finallyClause().none()) return false;
        return check(s.finallyClause().get());
    }

}
