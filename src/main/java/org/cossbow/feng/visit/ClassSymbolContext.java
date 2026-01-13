package org.cossbow.feng.visit;

import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.Symbol;
import org.cossbow.feng.ast.dcl.Variable;
import org.cossbow.feng.ast.oop.ClassDefinition;
import org.cossbow.feng.ast.oop.ClassField;
import org.cossbow.feng.ast.oop.ClassMethod;
import org.cossbow.feng.ast.proc.FunctionDefinition;
import org.cossbow.feng.util.Optional;

import static org.cossbow.feng.util.ErrorUtil.semantic;
import static org.cossbow.feng.util.ErrorUtil.unsupported;

public class ClassSymbolContext extends LocalSymbolContext {
    private final ClassDefinition definition;

    public ClassSymbolContext(SymbolContext parent,
                              ClassDefinition definition) {
        super(parent);
        this.definition = definition;
    }

    private Optional<ClassDefinition> findParent(ClassDefinition cd) {
        return cd.parent().flat(dt -> {
            if (!dt.generic().isEmpty())
                return unsupported("generic");

            return findType(dt.symbol()).map(def -> {
                if (def instanceof ClassDefinition pcd)
                    return pcd;
                return semantic("must be class: %s", def);
            });
        });
    }

    private Optional<ClassMethod> findMethod(ClassDefinition cd, Identifier name) {
        var m = cd.methods().tryGet(name);
        if (m.has()) return m;
        return findParent(cd).flat(pcd -> findMethod(pcd, name));
    }

    private Optional<ClassField> findField(ClassDefinition cd, Identifier name) {
        var f = cd.fields().tryGet(name);
        if (f.has()) return f;
        return findParent(cd).flat(pcd -> findField(pcd, name));
    }

    @Override
    public Optional<FunctionDefinition> findFunc(Symbol symbol) {
        var f = super.findFunc(symbol);
        if (f.has()) return f;
        if (symbol.module().has())
            return semantic("func %s not defined", symbol);
        return findMethod(definition, symbol.name()).map(m -> m);
    }

    @Override
    public Optional<Variable> findVar(Symbol symbol) {
        var v = super.findVar(symbol);
        if (v.has()) return v;
        if (symbol.module().has())
            return semantic("var %s not declared", symbol);

        return findField(definition, symbol.name()).map(ClassField::variable);
    }

}
