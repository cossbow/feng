package org.cossbow.feng.visit;

import org.cossbow.feng.ast.expr.*;
import org.cossbow.feng.util.ErrorUtil;

public interface ExpressionVisitor {

	default void visit(Expression e) {
		switch (e) {
			case BinaryExpression ee:
				visit(ee);
				break;
			case PrimaryExpression ee:
				visit(ee);
				break;
			case UnaryExpression ee:
				visit(ee);
				break;
			case null, default:
				ErrorUtil.unreachable();
		}
	}

	default void visit(BinaryExpression e) {}

	default void visit(PrimaryExpression e) {
		switch (e) {
			case ArrayExpression ee:
				visit(ee);
				break;
			case AssertExpression ee:
				visit(ee);
				break;
			case CallExpression ee:
				visit(ee);
				break;
			case IndexOfExpression ee:
				visit(ee);
				break;
			case LambdaExpression ee:
				visit(ee);
				break;
			case LiteralExpression ee:
				visit(ee);
				break;
			case MemberOfExpression ee:
				visit(ee);
				break;
			case NewExpression ee:
				visit(ee);
				break;
			case ObjectExpression ee:
				visit(ee);
				break;
			case PairsExpression ee:
				visit(ee);
				break;
			case ParenExpression ee:
				visit(ee);
				break;
			case ReferExpression ee:
				visit(ee);
				break;
			case null, default:
				ErrorUtil.unreachable();
		}
	}

	default void visit(ArrayExpression e) {}

	default void visit(AssertExpression e) {}

	default void visit(CallExpression e) {}

	default void visit(IndexOfExpression e) {}

	default void visit(LambdaExpression e) {}

	default void visit(LiteralExpression e) {}

	default void visit(MemberOfExpression e) {}

	default void visit(NewExpression e) {}

	default void visit(ObjectExpression e) {}

	default void visit(PairsExpression e) {}

	default void visit(ParenExpression e) {}

	default void visit(ReferExpression e) {}

	default void visit(UnaryExpression e) {}

}

