package org.cossbow.feng.ast.oop;

import org.cossbow.feng.ast.*;
import org.cossbow.feng.ast.gen.TypeParameters;
import org.cossbow.feng.ast.proc.FunctionDefinition;
import org.cossbow.feng.ast.proc.Prototype;
import org.cossbow.feng.util.Lazy;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class ClassMethod extends Method implements Exportable {
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

    public final Lazy<ClassDefinition> master = Lazy.nil();
    private final List<ClassMethod> override = new ArrayList<>();
    private boolean updater;

    public ClassDefinition master() {
        return master.must();
    }

    public List<ClassMethod> override() {
        return override;
    }

    public void seeOverride(Consumer<ClassMethod> user) {
        for (var m : override) {
            user.accept(m);
            m.seeOverride(user);
        }
    }

    public boolean updater() {
        return updater;
    }

    public void updater(boolean updater) {
        this.updater = updater;
    }

    //


    @Override
    public String toString() {
        if (master.none())
            return name.value() + func;
        return master.must().symbol() + "$" + name.value() + func;
    }
}
