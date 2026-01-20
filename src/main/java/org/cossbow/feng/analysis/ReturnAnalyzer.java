package org.cossbow.feng.analysis;

import org.cossbow.feng.ast.stmt.*;

import java.util.function.Predicate;

public class ReturnAnalyzer {
    private final Predicate<Statement> checker;

    public ReturnAnalyzer(Predicate<Statement> checker) {
        this.checker = checker;
    }

    public boolean check(Statement s) {
        return switch (s) {
            case BlockStatement ee -> check(ee);
            case ForStatement ee -> check(ee);
            case IfStatement ee -> check(ee);
            case LabeledStatement ee -> check(ee);
            case SwitchStatement ee -> check(ee);
            case ThrowStatement ee -> check(ee);
            case TryStatement ee -> check(ee);
            case ReturnStatement ee -> true;
            case null, default -> false;
        };
    }

    private boolean isEnd(Statement s) {
        return s instanceof ReturnStatement
                || s instanceof ThrowStatement;
    }

    public boolean check(BlockStatement bs) {
        if (bs.isEmpty()) return false;

        for (int i = bs.size() - 1; i >= 0; i--) {
            var s = bs.get(i);
            if (check(s)) return true;
        }

        return false;
    }

    public boolean check(CallStatement s) {
        return false;
    }

    public boolean check(ContinueStatement s) {
        return false;
    }

    public boolean check(DeclarationStatement s) {
        return false;
    }

    public boolean check(ForStatement s) {
        return false;
    }

    public boolean check(ConditionalForStatement s) {
        return false;
    }

    public boolean check(IterableForStatement s) {
        return false;
    }

    public boolean check(GotoStatement s) {
        return false;
    }

    public boolean check(IfStatement s) {
        return false;
    }

    public boolean check(LabeledStatement s) {
        return false;
    }

    public boolean check(SwitchStatement s) {
        return false;
    }

    public boolean check(ThrowStatement s) {
        return false;
    }

    public boolean check(TryStatement s) {
        return false;
    }

}
