package org.cossbow.feng.analysis;

import org.cossbow.feng.ast.dcl.*;
import org.cossbow.feng.ast.expr.*;
import org.cossbow.feng.ast.lit.StringLiteral;
import org.cossbow.feng.ast.stmt.ArrayTuple;
import org.cossbow.feng.ast.stmt.Tuple;
import org.cossbow.feng.visit.SymbolContext;

import static org.cossbow.feng.util.ErrorUtil.*;

/**
 * 详情见<a href="https://gitee.com/cossbow/feng/tree/semantics/#虚引用类型">虚引用类型</a>
 */
public class PhantomChecker {
    private final SymbolContext context;
    private final TypeTool typeTool;
    private final TypeDeducer deducer;

    public PhantomChecker(SymbolContext context) {
        this.context = context;
        typeTool = new TypeTool(context);
        deducer = new TypeDeducer(context);
    }

    //

    public boolean check(Tuple t, int i) {
        if (t instanceof ArrayTuple at)
            return check(at, i);
        return false;
    }

    public boolean check(ArrayTuple t, int i) {
        return check(t.values().get(i));
    }

    //

    public boolean check(Expression e) {
        if (e instanceof PrimaryExpression pe)
            return check(pe);

        return semantic("can't refer: %s", e.pos());
    }

    public boolean check(PrimaryExpression e) {
        return switch (e) {
            case AssertExpression ee -> check(ee);
            case CallExpression ee -> check(ee);
            case CurrentExpression ee -> check(ee);
            case IndexOfExpression ee -> check(ee);
            case LambdaExpression ee -> check(ee);
            case LiteralExpression ee -> check(ee);
            case MemberOfExpression ee -> check(ee);
            case NewExpression ee -> check(ee);
            case ParenExpression ee -> check(ee);
            case ReferExpression ee -> check(ee);
            default -> semantic("can't refer: %s", e.pos());
        };
    }

    public boolean check(AssertExpression e) {
        return unsupported("assert");
    }

    public boolean check(CallExpression e) {
        return semantic("can't refer call returns");
    }

    /**
     * 详情见<a href="https://gitee.com/cossbow/feng/tree/semantics/#this关键字">this关键字</a>
     */
    public boolean check(CurrentExpression e) {
        return true;
    }

    public boolean check(IndexOfExpression e) {
        var td = deducer.visit(e.subject());
        if (!(td instanceof ArrayTypeDeclarer atd))
            return unreachable();
        return !atd.refer().has() && check(e.subject());
    }

    public boolean check(LambdaExpression e) {
        return unsupported("lambda");
    }

    public boolean check(MemberOfExpression e) {
        var g2 = typeTool.getField(e.subject(), e.member());
        if (g2.none())
            return semantic("field not defined: %s", e.member().pos());

        var t = g2.get().a();
        var f = g2.get().b();
        var able = f.type().maybeRefer().has() == f.immutable();
        return check(e.subject()) == able;
    }

    public boolean check(NewExpression e) {
        return false;
    }

    public boolean check(ParenExpression e) {
        return check(e.child());
    }

    public boolean check(ReferExpression e) {
        if (!e.generic().isEmpty()) return unsupported("generic");
        var v = context.findVar(e.symbol());
        if (v.none()) return semantic("not declared: %s", e.pos());

        var isConst = v.get().declare() == Declare.CONST;
        var hasRefer = check(v.must().type().must());
        return isConst == hasRefer;
    }

    public boolean check(LiteralExpression e) {
        return e.literal() instanceof StringLiteral;
    }

    private boolean check(TypeDeclarer t) {
        if (!(t instanceof DerivedTypeDeclarer) &&
                !(t instanceof MemTypeDeclarer)) {
            return false;
        }
        var r = (Referable) t;
        return r.refer().has();
    }

}
