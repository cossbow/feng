package org.cossbow.feng.visit;

import org.cossbow.feng.ast.IdentifierTable;
import org.cossbow.feng.ast.Symbol;
import org.cossbow.feng.ast.TypeDefinition;
import org.cossbow.feng.ast.dcl.Variable;
import org.cossbow.feng.ast.proc.FunctionDefinition;
import org.cossbow.feng.util.Optional;

public class LocalSymbolContext implements SymbolContext {
    private final SymbolContext parent;

    public LocalSymbolContext(SymbolContext parent) {
        this.parent = parent;
    }

    private final IdentifierTable<Variable> variables = new IdentifierTable<>();

    @Override
    public Optional<TypeDefinition> findType(Symbol symbol) {
        return parent.findType(symbol);
    }

    @Override
    public Optional<FunctionDefinition> findFunc(Symbol symbol) {
        return parent.findFunc(symbol);
    }

    @Override
    public Optional<Variable> findVar(Symbol symbol) {
        if (symbol.module().none()) {
            var v = variables.tryGet(symbol.name());
            if (v.has()) return v;
        }
        return parent.findVar(symbol);
    }

    @Override
    public void putVar(Variable variable) {
        variables.add(variable.name(), variable);
    }
}
