package org.cossbow.feng.analysis;

import org.cossbow.feng.ast.expr.CallExpression;
import org.cossbow.feng.ast.expr.Expression;
import org.cossbow.feng.ast.micro.Macro;
import org.cossbow.feng.ast.stmt.*;
import org.cossbow.feng.util.ErrorUtil;
import org.cossbow.feng.visit.EntityVisitor;
import org.cossbow.feng.visit.SymbolContext;

public class CheckTupleAlign implements EntityVisitor<Integer> {
    private final SymbolContext context;
    private final TypeDeducer typeDeducer;

    public CheckTupleAlign(SymbolContext context) {
        this.context = context;
        this.typeDeducer = new TypeDeducer(context);
    }


    public Integer visit(DeclarationStatement ds) {

        return 0;
    }

    private void checkReturns(Expression e) {
        if (e instanceof CallExpression ce) {
            var td = typeDeducer.visit(ce);

        }
    }

    @Override
    public Integer visit(ArrayTuple e) {
        for (var expr : e.values()) {

        }
        return 0;
    }

    public Integer visit(IfTuple e) {
        return 0;
    }

    public Integer visit(SwitchTuple e) {
        return 0;
    }

    @Override
    public Integer visit(ReturnTuple e) {
        return 0;
    }
}
