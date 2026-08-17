package org.cossbow.feng.ast.expr;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.Symbol;
import org.cossbow.feng.ast.gen.GenericMap;
import org.cossbow.feng.ast.Mangle;
import org.cossbow.feng.ast.gen.TypeArguments;
import org.cossbow.feng.ast.proc.FunctionDefinition;

public class FunctionExpression extends PrimaryExpression {
    private final FunctionDefinition func;
    private TypeArguments generic;

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

    public void generic(TypeArguments generic) {
        this.generic = generic;
    }

    public Symbol symbol() {
        return func.symbol();
    }

    @Override
    public PrimaryExpression mirror() {
        return this;
    }

    @Override
    public PrimaryExpression mono(GenericMap gm) {
        var mapped = gm.mapAll(generic);
        if (!mapped.isEmpty() && !mapped.hasTypeVar()) {
            return monoCopy(new SymbolExpression(pos(),
                    Mangle.symbol(func.symbol(), mapped), TypeArguments.EMPTY), gm);
        }
        return monoCopy(new FunctionExpression(pos(), func, mapped), gm);
    }

    //
    @Override
    public String toString() {
        if (generic.isEmpty())
            return symbol().toString();
        return symbol().toString() + generic;
    }
}
