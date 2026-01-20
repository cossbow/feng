package org.cossbow.feng.ast.oop;

import org.cossbow.feng.ast.*;
import org.cossbow.feng.ast.gen.TypeParameters;
import org.cossbow.feng.ast.proc.FunctionDefinition;
import org.cossbow.feng.ast.proc.Prototype;
import org.cossbow.feng.util.Lazy;

public class ClassMethod extends Entity implements Method, Exportable {
    private boolean export;
    private Identifier name;
    private FunctionDefinition func;
    private boolean returnThis;

    public ClassMethod(Position pos,
                       boolean export,
                       Identifier name,
                       FunctionDefinition func,
                       boolean returnThis) {
        super(pos);
        this.export = export;
        this.name = name;
        this.func = func;
        this.returnThis = returnThis;
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

    public boolean returnThis() {
        return returnThis;
    }

    @Override
    public Prototype prototype() {
        return func.prototype();
    }

    @Override
    public TypeParameters generic() {
        return func.generic();
    }

    //

    private transient Lazy<ClassDefinition> master = Lazy.nil();

    public Lazy<ClassDefinition> master() {
        return master;
    }

    //


    @Override
    public String toString() {
        return name.value() + func;
    }
}
