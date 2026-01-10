package org.cossbow.feng.visit;

import org.cossbow.feng.ast.IdentifierTable;
import org.cossbow.feng.util.Optional;
import org.cossbow.feng.ast.Symbol;
import org.cossbow.feng.ast.TypeDefinition;
import org.cossbow.feng.ast.dcl.Variable;
import org.cossbow.feng.ast.proc.FunctionDefinition;

public class LocalSymbolContext implements SymbolContext {

    private final SymbolContext outer;
    private final IdentifierTable<Variable> variables;

    public LocalSymbolContext(SymbolContext outer,
                              IdentifierTable<Variable> variables) {
        this.outer = outer;
        this.variables = variables;
    }

    @Override
    public Optional<TypeDefinition> findType(Symbol symbol) {
        return outer.findType(symbol);
    }

    @Override
    public Optional<FunctionDefinition> findFunc(Symbol symbol) {
        return outer.findFunc(symbol);
    }

    @Override
    public Optional<Variable> findVar(Symbol symbol) {
        if (symbol.module().has()) return outer.findVar(symbol);
        var v = variables.tryGet(symbol.name());
        if (v.has()) return v;
        return outer.findVar(symbol);
    }

}
