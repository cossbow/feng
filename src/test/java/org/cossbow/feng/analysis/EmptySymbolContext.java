package org.cossbow.feng.analysis;

import org.cossbow.feng.util.Optional;
import org.cossbow.feng.ast.Symbol;
import org.cossbow.feng.ast.TypeDefinition;
import org.cossbow.feng.ast.dcl.Variable;
import org.cossbow.feng.ast.proc.FunctionDefinition;
import org.cossbow.feng.visit.SymbolContext;

public class EmptySymbolContext implements SymbolContext {
    @Override
    public Optional<TypeDefinition> findType(Symbol symbol) {
        return Optional.empty();
    }

    @Override
    public Optional<FunctionDefinition> findFunc(Symbol symbol) {
        return Optional.empty();
    }

    @Override
    public Optional<Variable> findVar(Symbol symbol) {
        return Optional.empty();
    }

    @Override
    public void putVar(Variable variable) {
    }

    public static final EmptySymbolContext EMPTY = new EmptySymbolContext();
}
