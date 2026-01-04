package org.cossbow.feng.visit;


import org.cossbow.feng.ast.expr.*;
import org.cossbow.feng.util.ErrorUtil;

public interface ExpressionParser<R> {

    default R visit(Expression e) {
        return switch (e) {
            case BinaryExpression ee -> visit(ee);
            case PrimaryExpression ee -> visit(ee);
            case UnaryExpression ee -> visit(ee);
            case null, default -> ErrorUtil.unreachable();
        };
    }

    R visit(BinaryExpression e);

    default R visit(PrimaryExpression e) {
        return switch (e) {
            case ArrayExpression ee -> visit(ee);
            case AssertExpression ee -> visit(ee);
            case CallExpression ee -> visit(ee);
            case IndexOfExpression ee -> visit(ee);
            case LambdaExpression ee -> visit(ee);
            case LiteralExpression ee -> visit(ee);
            case MemberOfExpression ee -> visit(ee);
            case NewExpression ee -> visit(ee);
            case ObjectExpression ee -> visit(ee);
            case PairsExpression ee -> visit(ee);
            case ParenExpression ee -> visit(ee);
            case ReferExpression ee -> visit(ee);
            case null, default -> ErrorUtil.unreachable();
        };
    }

    R visit(ArrayExpression e);

    R visit(AssertExpression e);

    R visit(CallExpression e);

    R visit(IndexOfExpression e);

    R visit(LambdaExpression e);

    R visit(LiteralExpression e);

    R visit(MemberOfExpression e);

    R visit(NewExpression e);

    R visit(ObjectExpression e);

    R visit(PairsExpression e);

    R visit(ParenExpression e);

    R visit(ReferExpression e);

    R visit(UnaryExpression e);

}

