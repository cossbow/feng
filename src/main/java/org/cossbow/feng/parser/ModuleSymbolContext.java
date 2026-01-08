package org.cossbow.feng.parser;

import org.cossbow.feng.ast.Optional;
import org.cossbow.feng.ast.Symbol;
import org.cossbow.feng.ast.TypeDefinition;
import org.cossbow.feng.ast.dcl.Variable;
import org.cossbow.feng.ast.proc.FunctionDefinition;
import org.cossbow.feng.visit.SymbolContext;

public class ModuleSymbolContext implements SymbolContext {



    @Override
    public Optional<TypeDefinition> findType(Symbol symbol) {
        return null;
    }

    @Override
    public Optional<FunctionDefinition> findFunc(Symbol symbol) {
        return null;
    }

    @Override
    public Optional<Variable> findVar(Symbol symbol) {
        return null;
    }
}
