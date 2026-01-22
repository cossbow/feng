package org.cossbow.feng.analysis;

import org.cossbow.feng.ast.dcl.*;
import org.cossbow.feng.ast.expr.*;
import org.cossbow.feng.ast.lit.NilLiteral;
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

    public boolean checkEnable(Expression e) {
        if (e instanceof PrimaryExpression pe)
            return checkEnable(pe);

        return semantic("can't refer: %s", e.pos());
    }

    public boolean checkEnable(PrimaryExpression e) {
        return switch (e) {
            case AssertExpression ee -> checkEnable(ee);
            case SizeofExpression ee -> checkEnable(ee);
            case CallExpression ee -> checkEnable(ee);
            case CurrentExpression ee -> checkEnable(ee);
            case IndexOfExpression ee -> checkEnable(ee);
            case LambdaExpression ee -> checkEnable(ee);
            case LiteralExpression ee -> checkEnable(ee);
            case MemberOfExpression ee -> checkEnable(ee);
            case NewExpression ee -> checkEnable(ee);
            case ParenExpression ee -> checkEnable(ee);
            case ReferExpression ee -> checkEnable(ee);
            default -> semantic("can't refer: %s", e.pos());
        };
    }

    public boolean checkEnable(AssertExpression e) {
        return false;
    }
    public boolean checkEnable(SizeofExpression e) {
        return false;
    }

    public boolean checkEnable(CallExpression e) {
        return semantic("can't refer call returns");
    }

    /**
     * 详情见<a href="https://gitee.com/cossbow/feng/tree/semantics/#this关键字">this关键字</a>
     */
    public boolean checkEnable(CurrentExpression e) {
        return true;
    }

    public boolean checkEnable(IndexOfExpression e) {
        var td = deducer.visit(e.subject());
        if (!(td instanceof ArrayTypeDeclarer atd))
            return unreachable();
        return !atd.refer().has() && checkEnable(e.subject());
    }

    public boolean checkEnable(LambdaExpression e) {
        return unsupported("lambda");
    }

    public boolean checkEnable(MemberOfExpression e) {
        var g2 = typeTool.getField(e.subject(), e.member());
        if (g2.none())
            return semantic("field not defined: %s", e.member().pos());

        var f = g2.get().b();
        var able = f.type().maybeRefer().has() == f.immutable();
        return checkEnable(e.subject()) == able;
    }

    public boolean checkEnable(NewExpression e) {
        return false;
    }

    public boolean checkEnable(ParenExpression e) {
        return checkEnable(e.child());
    }

    public boolean checkEnable(ReferExpression e) {
        if (!e.generic().isEmpty()) return unsupported("generic");
        var v = context.findVar(e.symbol());
        if (v.none()) return semantic("not declared: %s", e.pos());

        var isConst = v.get().declare() == Declare.CONST;
        var hasRefer = checkEnable(v.must().type().must());
        return isConst == hasRefer;
    }

    public boolean checkEnable(LiteralExpression e) {
        return e.literal() instanceof StringLiteral
                || e.literal() instanceof NilLiteral;
    }

    private boolean checkEnable(TypeDeclarer t) {
        if (!(t instanceof DerivedTypeDeclarer) &&
                !(t instanceof MemTypeDeclarer)) {
            return false;
        }
        var r = (Referable) t;
        return r.refer().has();
    }

}
