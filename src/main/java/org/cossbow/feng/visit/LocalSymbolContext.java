package org.cossbow.feng.visit;

import org.cossbow.feng.ast.IdentifierTable;
import org.cossbow.feng.ast.Symbol;
import org.cossbow.feng.ast.TypeDefinition;
import org.cossbow.feng.ast.dcl.Variable;
import org.cossbow.feng.ast.proc.FunctionDefinition;
import org.cossbow.feng.util.Optional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Stream;

public class LocalSymbolContext implements SymbolContext {
    private final SymbolContext parent;

    public LocalSymbolContext(SymbolContext parent) {
        this.parent = parent;
    }

    private final IdentifierTable<Variable> variables = new IdentifierTable<>();
    private final HashMap<Integer, Variable> lockedVars = new HashMap<>();
    private final HashMap<Integer, Variable> checkedNonNil = new HashMap<>();

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

    public List<Variable> scope() {
        return new ArrayList<>(variables.values());
    }

    public Stream<Variable> local() {
        return Stream.concat(parent.local(), variables.stream());
    }

    public boolean lockVar(Variable v) {
        lockedVars.put(v.id(), v);
        return true;
    }

    public boolean isVarLocked(Variable v) {
        return lockedVars.containsKey(v.id());
    }

    public void checkedNonNil(Variable v) {
        checkedNonNil.put(v.id(), v);
    }

    public boolean isNotNil(Variable v) {
        return checkedNonNil.containsKey(v.id());
    }

}
