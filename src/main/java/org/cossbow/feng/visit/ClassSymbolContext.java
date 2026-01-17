package org.cossbow.feng.visit;

import org.cossbow.feng.ast.Symbol;
import org.cossbow.feng.ast.dcl.Variable;
import org.cossbow.feng.ast.oop.ClassDefinition;
import org.cossbow.feng.ast.oop.ClassField;
import org.cossbow.feng.ast.oop.ClassMethod;
import org.cossbow.feng.ast.proc.FunctionDefinition;
import org.cossbow.feng.util.Optional;

import static org.cossbow.feng.util.ErrorUtil.semantic;

public class ClassSymbolContext extends LocalSymbolContext {
    private final ClassDefinition definition;

    public ClassSymbolContext(SymbolContext parent,
                              ClassDefinition definition) {
        super(parent);
        this.definition = definition;
    }

    @Override
    public Optional<FunctionDefinition> findFunc(Symbol symbol) {
        var f = super.findFunc(symbol);
        if (f.has()) return f;
        if (symbol.module().has())
            return semantic("func %s not defined", symbol);
        return definition.allMethods().must().tryGet(symbol.name())
                .map(ClassMethod::func);
    }

    @Override
    public Optional<Variable> findVar(Symbol symbol) {
        var v = super.findVar(symbol);
        if (v.has()) return v;
        if (symbol.module().has())
            return semantic("var %s not declared", symbol);

        return definition.allFields().must().tryGet(symbol.name())
                .map(ClassField::variable);
    }

}
