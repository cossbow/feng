package org.cossbow.feng.ast.micro;

import org.cossbow.feng.ast.*;
import org.cossbow.feng.ast.expr.Expression;
import org.cossbow.feng.ast.stmt.Statement;
import org.cossbow.feng.util.Optional;

import java.util.List;

public class MacroProcedure extends Entity {
    private Identifier name;
    private IdentifierTable<MacroVariable> params;
    private List<Statement> body;
    private Optional<Expression> result;

    public MacroProcedure(Position pos,
                          Identifier name,
                          IdentifierTable<MacroVariable> params,
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

    public IdentifierTable<MacroVariable> params() {
        return params;
    }

    public List<Statement> body() {
        return body;
    }

    public Optional<Expression> result() {
        return result;
    }

}
