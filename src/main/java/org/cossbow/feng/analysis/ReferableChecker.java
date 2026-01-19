package org.cossbow.feng.analysis;

import org.cossbow.feng.ast.dcl.DefinedTypeDeclarer;
import org.cossbow.feng.ast.dcl.MemTypeDeclarer;
import org.cossbow.feng.ast.dcl.Refer;
import org.cossbow.feng.ast.dcl.TypeDeclarer;
import org.cossbow.feng.ast.expr.*;
import org.cossbow.feng.ast.lit.StringLiteral;
import org.cossbow.feng.ast.stmt.ArrayTuple;
import org.cossbow.feng.ast.stmt.ReturnTuple;
import org.cossbow.feng.ast.stmt.Tuple;
import org.cossbow.feng.visit.SymbolContext;

import static org.cossbow.feng.ast.dcl.ReferKind.STRONG;
import static org.cossbow.feng.util.ErrorUtil.*;

public class ReferableChecker {

    private SymbolContext context;
    private TypeDeclarer type;
    private Refer refer;

    public ReferableChecker(SymbolContext context,
                            TypeDeclarer type,
                            Refer refer) {
        this.context = context;
        this.type = type;
        this.refer = refer;
    }

    //

    public Void check(Tuple e) {
        if (e instanceof ArrayTuple at)
            return check(at);

        if (e instanceof ReturnTuple) {
            if (refer.checkType(STRONG)) return null;
            return semantic("%s can't refer returns: %s",
                    refer.kind(), e.pos());
        }

        return unreachable();
    }

    public Void check(ArrayTuple e) {
        return unreachable();
    }

    //

    public Void check(Expression e) {
        if (e instanceof PrimaryExpression pe)
            return check(pe);

        return semantic("can't refer: %s", e.pos());
    }

    public Void check(PrimaryExpression e) {
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

    public Void check(AssertExpression e) {
        return unsupported("assert");
    }

    public Void check(CallExpression e) {
        return unreachable();
    }

    public Void check(CurrentExpression e) {
        return unreachable();
    }

    public Void check(IndexOfExpression e) {
        return unreachable();
    }

    public Void check(LambdaExpression e) {
        return unreachable();
    }

    public Void check(MemberOfExpression e) {
        return unreachable();
    }

    public Void check(NewExpression e) {
        return unreachable();
    }

    public Void check(ParenExpression e) {
        return unreachable();
    }

    public Void check(ReferExpression e) {
        if (!e.generic().isEmpty()) return unsupported("generic");
        var v = context.findVar(e.symbol());
        if (v.none()) return semantic("not declared: %s", e.pos());
        var td = v.get().type().must();
        if (td instanceof DefinedTypeDeclarer dtd) {

        }
        return unreachable();
    }

    public Void check(LiteralExpression e) {
        if (!(type instanceof MemTypeDeclarer m && m.readonly()) ||
                !(e.literal() instanceof StringLiteral)) {
            return semantic("only rom can refer string: %s", e.pos());
        }

        return null;
    }

}
