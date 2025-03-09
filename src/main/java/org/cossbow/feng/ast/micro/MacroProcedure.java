package org.cossbow.feng.ast.micro;

import org.cossbow.feng.ast.Entity;
import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.expr.Expression;
import org.cossbow.feng.ast.stmt.Statement;

import java.util.List;
import java.util.Optional;

public class MacroProcedure extends Entity {
    private final Identifier name;
    private final List<MacroVariable> params;
    private final List<Statement> body;
    private final Optional<Expression> result;

    public MacroProcedure(Position pos,
                          Identifier name,
                          List<MacroVariable> params,
                          List<Statement> body,
                          Optional<Expression> result) {
        super(pos);
        this.name = name;
        this.params = params;
        this.body = body;
        this.result = result;
    }

    public Identifier name() {
        return name;
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
