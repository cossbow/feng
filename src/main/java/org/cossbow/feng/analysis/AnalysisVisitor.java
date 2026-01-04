package org.cossbow.feng.analysis;

import org.cossbow.feng.visit.EntityVisitor;
import org.cossbow.feng.ast.expr.BinaryExpression;

public class AnalysisVisitor implements EntityVisitor {



    @Override
    public void visit(BinaryExpression be) {
        be.left();
    }
}
