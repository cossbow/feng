package org.cossbow.feng.analysis;

import org.cossbow.feng.ast.IdentifierMap;
import org.cossbow.feng.ast.Symbol;
import org.cossbow.feng.ast.TypeDefinition;
import org.cossbow.feng.ast.dcl.Variable;
import org.cossbow.feng.ast.proc.FunctionDefinition;
import org.cossbow.feng.util.Optional;

import java.util.*;
import java.util.stream.Stream;

public class LocalSymbolContext implements SymbolContext {
    private final SymbolContext parent;

    public LocalSymbolContext(SymbolContext parent) {
        this.parent = parent;
    }

    private final IdentifierMap<Variable> variables = new IdentifierMap<>();
    private final Set<Variable> lockedVars = new HashSet<>();
    private final Set<Variable> varNotNil = new HashSet<>();

    @Override
    public boolean isLocal(Symbol s) {
        return s.module().none() || parent.isLocal(s);
    }

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
        lockedVars.add(v);
        return true;
    }

    public boolean isVarLocked(Variable v) {
        return lockedVars.contains(v);
    }

    @Override
    public void setNotNil(Variable v) {
        varNotNil.add(v);
    }

    @Override
    public void delNotNil(Variable v) {
        if (!varNotNil.remove(v)) {
            parent.delNotNil(v);
        }
    }

    @Override
    public boolean isNotNil(Variable v) {
        return varNotNil.contains(v) || parent.isNotNil(v);
    }

}
