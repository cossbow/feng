package org.cossbow.feng.analysis;

import org.cossbow.feng.ast.Scope;
import org.cossbow.feng.ast.Symbol;
import org.cossbow.feng.ast.TypeDefinition;
import org.cossbow.feng.ast.dcl.Variable;
import org.cossbow.feng.ast.proc.FunctionDefinition;
import org.cossbow.feng.util.Optional;
import org.cossbow.feng.util.Stack;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class StackedContext implements SymbolContext {
    private final Stack<SymbolContext> stack = new Stack<>();

    public StackedContext(SymbolContext root) {
        stack.push(root);
    }

    public void enterScope() {
        stack.push(new LocalSymbolContext(stack.peek()));
    }

    public SymbolContext getScope() {
        return stack.peek();
    }

    public void exitScope() {
        stack.pop();
    }

    public void exitScope(Scope scope) {
        var ctx = stack.pop();
        if (!(ctx instanceof LocalSymbolContext lsc))
            return;
        var list = new ArrayList<>(lsc.scope());
        scope.stack(list);
    }

    @Override
    public boolean isLocal(Symbol s) {
        return getScope().isLocal(s);
    }

    @Override
    public Optional<TypeDefinition> findType(Symbol symbol) {
        return getScope().findType(symbol);
    }

    @Override
    public Optional<FunctionDefinition> findFunc(Symbol symbol) {
        return getScope().findFunc(symbol);
    }

    @Override
    public Optional<Variable> findVar(Symbol symbol) {
        return getScope().findVar(symbol);
    }

    @Override
    public void putVar(Variable variable) {
        getScope().putVar(variable);
    }

    @Override
    public List<Variable> scope() {
        return getScope().scope();
    }

    @Override
    public Stream<Variable> local() {
        return getScope().local();
    }

    public boolean lockVar(Variable variable) {
        return getScope().lockVar(variable);
    }

    public boolean isVarLocked(Variable variable) {
        return getScope().isVarLocked(variable);
    }

}
