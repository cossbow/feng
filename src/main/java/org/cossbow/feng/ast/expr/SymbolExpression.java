package org.cossbow.feng.ast.expr;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.Symbol;
import org.cossbow.feng.ast.gen.TypeArguments;

public class SymbolExpression extends PrimaryExpression {
    private Symbol symbol;
    private TypeArguments generic;

    public SymbolExpression(Position pos,
                            Symbol symbol,
                            TypeArguments generic) {
        super(pos);
        this.symbol = symbol;
        this.generic = generic;
    }

    public Symbol symbol() {
        return symbol;
    }

    public TypeArguments generic() {
        return generic;
    }

    //


    @Override
    public String toString() {
        if (generic.isEmpty())
            return symbol.toString();
        return symbol.toString() + generic;
    }
}
