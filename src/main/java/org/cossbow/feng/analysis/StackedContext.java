package org.cossbow.feng.analysis;

import org.cossbow.feng.ast.Symbol;
import org.cossbow.feng.ast.TypeDefinition;
import org.cossbow.feng.ast.dcl.Variable;
import org.cossbow.feng.ast.oop.ClassDefinition;
import org.cossbow.feng.ast.proc.FunctionDefinition;
import org.cossbow.feng.util.Optional;
import org.cossbow.feng.util.Stack;
import org.cossbow.feng.visit.ClassSymbolContext;
import org.cossbow.feng.visit.LocalSymbolContext;
import org.cossbow.feng.visit.SymbolContext;

public class StackedContext implements SymbolContext {
    private final Stack<SymbolContext> stack = new Stack<>();

    public StackedContext(SymbolContext root) {
        stack.push(root);
    }

    public void enterScope(ClassDefinition cd) {
        stack.push(new ClassSymbolContext(stack.peek(), cd));
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
}
