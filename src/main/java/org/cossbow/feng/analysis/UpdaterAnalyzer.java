package org.cossbow.feng.analysis;

import org.cossbow.feng.ast.dcl.NewArrayType;
import org.cossbow.feng.ast.expr.*;
import org.cossbow.feng.ast.oop.ClassDefinition;
import org.cossbow.feng.ast.oop.ClassMethod;
import org.cossbow.feng.ast.proc.Procedure;
import org.cossbow.feng.ast.stmt.*;
import org.cossbow.feng.ast.var.FieldOperand;
import org.cossbow.feng.ast.var.Operand;

import java.util.List;

import static org.cossbow.feng.util.ErrorUtil.*;

public class UpdaterAnalyzer {

    private ClassDefinition enterClass;
    private ClassMethod enterMethod;

    public void analyse(ClassDefinition def) {
        enterClass = def;
        for (var method : def.methods()) {
            analyse(method);
        }
    }

    public void analyse(ClassMethod method) {
        enterMethod = method;
        analyse(method.func().procedure());
    }

    private void analyse(Procedure proc) {
        analyse(proc.body());
    }

    private void analyse(Statement s) {
        switch (s) {
            case AssignmentsStatement ss -> analyse(ss);
            case BlockStatement ss -> analyse(ss);
            case CallStatement ss -> analyse(ss);
            case DeclarationStatement ss -> analyse(ss);
            case ConditionalForStatement ss -> analyse(ss);
            case IterableForStatement ss -> analyse(ss);
            case IfStatement ss -> analyse(ss);
            case LabeledStatement ss -> analyse(ss.target());
            case ReturnStatement ss -> analyse(ss);
            case SwitchStatement ss -> analyse(ss);
            case ThrowStatement ss -> analyse(ss);
            case TryStatement ss -> analyse(ss);
            case SwitchBranch ss -> analyse(ss);
            case null, default -> {
            }
        }
    }

    private void analyse(AssignmentsStatement as) {
        for (var a : as.list()) {
            analyse(a.operand());
        }
    }

    private void analyse(BlockStatement bs) {
        for (var s : bs.list()) analyse(s);
    }

    private void analyse(CallStatement cs) {
        analyse(cs.call());
    }

    private void analyse(DeclarationStatement s) {
        analyse(s.init());
    }

    private void analyse(ConditionalForStatement s) {
        s.initializer().use(this::analyse);
        analyse(s.condition());
        s.updater().use(this::analyse);
        analyse(s.body());
    }

    private void analyse(IterableForStatement s) {
        analyse(s.iterable());
        analyse(s.body());
    }

    private void analyse(IfStatement s) {
        s.init().use(this::analyse);
        analyse(s.condition());
        analyse(s.yes());
        s.not().use(this::analyse);
    }

    private void analyse(ReturnStatement s) {
        s.result().use(this::analyse);
    }

    private void analyse(SwitchStatement s) {
        s.init().use(this::analyse);
        analyse(s.value());
        for (var b : s.branches()) {
            analyse(b.constants());
            analyse(b);
        }
        s.defaultBranch().use(this::analyse);
    }

    private void analyse(Branch s) {
        analyse(s.body());
    }

    private void analyse(ThrowStatement s) {
        analyse(s.exception());
    }

    private void analyse(TryStatement s) {
        analyse(s.body());
        for (var c : s.catchClauses())
            analyse(c);
        s.finallyClause().use(this::analyse);
    }

    private void analyse(CatchClause c) {
        analyse(c.body());
    }

    //

    private void analyse(List<Expression> list) {
        for (var e : list) analyse(e);
    }

    private void analyse(Expression e) {
        switch (e) {
            case ArrayExpression ee -> analyse(ee);
            case AssertExpression ee -> analyse(ee);
            case CallExpression ee -> analyse(ee);
            case IndexOfExpression ee -> analyse(ee);
            case LambdaExpression ee -> analyse(ee);
            case MemberOfExpression ee -> analyse(ee);
            case NewExpression ee -> analyse(ee);
            case ObjectExpression ee -> analyse(ee);
            case PairsExpression ee -> analyse(ee);
            case ParenExpression ee -> analyse(ee.child());
            case ReferExpression ee -> analyse(ee);
            case BinaryExpression ee -> analyse(ee);
            case UnaryExpression ee -> analyse(ee);
            case null, default -> {
            }
        }
    }

    private void analyse(NewExpression e) {
        if (e.type() instanceof NewArrayType t) {
            analyse(t.length());
        }
        e.arg().use(this::analyse);
    }

    private void analyse(ArrayExpression e) {
        analyse(e.elements());
    }

    private void analyse(AssertExpression e) {
        analyse(e.subject());
    }

    private void analyse(MemberOfExpression e) {
        analyse(e.subject());
    }

    private void analyse(IndexOfExpression e) {
        analyse(e.subject());
        analyse(e.index());
    }

    private void analyse(LambdaExpression e) {
        unreachable();
    }

    private void analyse(ObjectExpression e) {
        analyse(e.entries().values());
    }

    private void analyse(PairsExpression e) {
        unreachable();
    }

    private void analyse(ReferExpression e) {
    }

    private void analyse(BinaryExpression e) {
        analyse(e.left());
        analyse(e.right());
    }

    private void analyse(UnaryExpression e) {
        analyse(e.operand());
    }

    private void analyse(CallExpression s) {
        if (s.callee() instanceof MemberOfExpression me) {
            if (me.subject() instanceof CurrentExpression) {
                var o = enterClass.allMethods().tryGet(me.member());
                o.use(cm -> {
                    enterMethod.updater(cm.updater());
                });
            }
        }
        for (var a : s.arguments()) analyse(a);
    }

    private void analyse(Operand o) {
        if (!(o instanceof FieldOperand fo))
            return;

        var s = fo.subject();
        while (true) {
            switch (s) {
                case CurrentExpression ce -> {
                    enterMethod.updater(true);
                    return;
                }
                case MemberOfExpression me -> {
                    s = me.subject();
                }
                case IndexOfExpression me -> {
                    s = me.subject();
                }
                case CallExpression ce -> {
                    analyse(ce);
                    return;
                }
                case null, default -> {
                    return;
                }
            }
        }
    }

}
