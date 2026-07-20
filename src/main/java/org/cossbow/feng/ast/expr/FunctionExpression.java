package org.cossbow.feng.ast.expr;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.Symbol;
import org.cossbow.feng.ast.gen.TypeArguments;
import org.cossbow.feng.ast.proc.FunctionDefinition;

public class FunctionExpression extends PrimaryExpression {
    private final FunctionDefinition func;
    private final TypeArguments generic;

    public FunctionExpression(Position pos,
                              FunctionDefinition func,
                              TypeArguments generic) {
        super(pos);
        this.func = func;
        this.generic = generic;
    }

    public FunctionDefinition func() {
        return func;
    }

    public TypeArguments generic() {
        return generic;
    }

    public Symbol symbol() {
        return func.symbol();
    }

    @Override
    public PrimaryExpression mirror() {
        return this;
    }

    //
    @Override
    public String toString() {
        if (generic.isEmpty())
            return symbol().toString();
        return symbol().toString() + generic;
    }
}
