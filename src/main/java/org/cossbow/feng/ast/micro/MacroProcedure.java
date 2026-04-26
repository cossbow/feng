package org.cossbow.feng.ast.micro;

import org.cossbow.feng.ast.*;
import org.cossbow.feng.ast.dcl.TypeDeclarer;
import org.cossbow.feng.ast.expr.Expression;
import org.cossbow.feng.ast.stmt.Statement;
import org.cossbow.feng.util.Optional;

import java.util.List;

public class MacroProcedure extends Entity {
    private Identifier name;
    private IdentifierMap<MacroVariable> params;
    private Optional<TypeDeclarer> result;
    private List<Statement> body;
    private Optional<Expression> value;

    public MacroProcedure(Position pos,
                          Identifier name,
                          IdentifierMap<MacroVariable> params,
                          Optional<TypeDeclarer> result,
                          List<Statement> body,
                          Optional<Expression> value) {
        super(pos);
        this.name = name;
        this.params = params;
        this.result = result;
        this.body = body;
        this.value = value;
    }

    public Identifier name() {
        return name;
    }

    public IdentifierMap<MacroVariable> params() {
        return params;
    }

    public Optional<TypeDeclarer> result() {
        return result;
    }

    public List<Statement> body() {
        return body;
    }

    public Optional<Expression> value() {
        return value;
    }

}
