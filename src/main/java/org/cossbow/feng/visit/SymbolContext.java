package org.cossbow.feng.visit;

import org.cossbow.feng.ast.Symbol;
import org.cossbow.feng.ast.TypeDefinition;
import org.cossbow.feng.ast.dcl.Variable;
import org.cossbow.feng.ast.proc.FunctionDefinition;
import org.cossbow.feng.util.Optional;

import java.util.List;
import java.util.stream.Stream;

public interface SymbolContext {

    Optional<TypeDefinition> findType(Symbol symbol);

    Optional<FunctionDefinition> findFunc(Symbol symbol);

    Optional<Variable> findVar(Symbol symbol);

    void putVar(Variable variable);

    List<Variable> scope();

    Stream<Variable> local();

    boolean lockVar(Variable v);

    boolean isVarLocked(Variable v);

}
