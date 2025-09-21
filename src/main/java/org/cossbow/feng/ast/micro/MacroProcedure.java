package org.cossbow.feng.ast.micro;

import org.cossbow.feng.ast.*;
import org.cossbow.feng.ast.expr.Expression;
import org.cossbow.feng.ast.stmt.Statement;

import java.util.List;

public class MacroProcedure extends Entity {
    private final Identifier name;
    private final UniqueTable<MacroVariable> params;
    private final List<Statement> body;
    private final Optional<Expression> result;

    public MacroProcedure(Position pos,
                          Identifier name,
                          UniqueTable<MacroVariable> params,
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

    public UniqueTable<MacroVariable> params() {
        return params;
    }

    public List<Statement> body() {
        return body;
    }

    public Optional<Expression> result() {
        return result;
    }

}
