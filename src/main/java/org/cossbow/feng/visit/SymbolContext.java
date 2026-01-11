package org.cossbow.feng.visit;

import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.util.Optional;
import org.cossbow.feng.ast.Symbol;
import org.cossbow.feng.ast.TypeDefinition;
import org.cossbow.feng.ast.dcl.Variable;
import org.cossbow.feng.ast.proc.FunctionDefinition;

public interface SymbolContext {

    Optional<TypeDefinition> findType(Symbol symbol);

    Optional<FunctionDefinition> findFunc(Symbol symbol);

    Optional<Variable> findVar(Symbol symbol);

    void putVar(Variable variable);

}
