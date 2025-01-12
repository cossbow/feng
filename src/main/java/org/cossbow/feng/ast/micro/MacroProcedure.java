package org.cossbow.feng.ast.micro;

import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.expr.Expression;
import org.cossbow.feng.ast.stmt.Statement;

import java.util.List;
import java.util.Optional;

public class MacroProcedure extends Macro {
    private final List<MacroVariable> params;
    private final List<Statement> body;
    private final Optional<Expression> result;

    public MacroProcedure(Position pos,
                          Identifier name,
                          List<MacroVariable> params,
                          List<Statement> body,
                          Optional<Expression> result) {
        super(pos, name);
        this.params = params;
        this.body = body;
        this.result = result;
    }

    public List<MacroVariable> params() {
        return params;
    }

    public List<Statement> body() {
        return body;
    }

    public Optional<Expression> result() {
        return result;
    }

}
