package org.cossbow.feng.ast.oop;

import org.cossbow.feng.ast.Entity;
import org.cossbow.feng.ast.Exportable;
import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.proc.FunctionDefinition;

public class ClassMethod extends Entity implements Exportable {
    private boolean export;
    private Identifier name;
    private FunctionDefinition func;

    public ClassMethod(Position pos,
                       boolean export,
                       Identifier name,
                       FunctionDefinition func) {
        super(pos);
        this.export = export;
        this.name = name;
        this.func = func;
    }

    public boolean export() {
        return export;
    }

    public Identifier name() {
        return name;
    }

    public FunctionDefinition func() {
        return func;
    }

    private volatile boolean updater;

    public boolean updater() {
        return updater;
    }

    public void updater(boolean updater) {
        this.updater = updater;
    }

}
