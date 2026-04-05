package org.cossbow.feng.ast.oop;

import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.Method;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.attr.Modifier;
import org.cossbow.feng.ast.gen.TypeParameters;
import org.cossbow.feng.ast.proc.Procedure;
import org.cossbow.feng.ast.proc.Prototype;
import org.cossbow.feng.util.Optional;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class ClassMethod extends Method {
    private Modifier modifier;
    private Identifier name;
    private TypeParameters generic;
    private Prototype prototype;
    private Optional<Procedure> procedure;
    private boolean returnThis;

    public ClassMethod(Position pos,
                       Modifier modifier,
                       Identifier name,
                       TypeParameters generic,
                       Prototype prototype,
                       Optional<Procedure> procedure,
                       boolean returnThis) {
        super(pos);
        this.modifier = modifier;
        this.name = name;
        this.generic = generic;
        this.prototype = prototype;
        this.procedure = procedure;
        this.returnThis = returnThis;
    }

    public ClassMethod(Position pos,
                       Modifier modifier,
                       Identifier name,
                       TypeParameters generic,
                       Procedure procedure,
                       boolean returnThis) {
        this(pos, modifier, name, generic,
                procedure.prototype(),
                Optional.of(procedure), returnThis);
    }

    public ClassMethod(Position pos,
                       Modifier modifier,
                       Identifier name,
                       TypeParameters generic,
                       Prototype prototype,
                       boolean returnThis) {
        this(pos, modifier, name, generic,
                prototype, Optional.empty(), returnThis);
    }

    public boolean export() {
        return modifier().export();
    }

    public Modifier modifier() {
        return modifier;
    }

    public Identifier name() {
        return name;
    }

    public boolean returnThis() {
        return returnThis;
    }

    public Prototype prototype() {
        return prototype;
    }

    public void prototype(Prototype prototype) {
        this.prototype = prototype;
    }

    public Optional<Procedure> procedure() {
        return procedure;
    }

    public TypeParameters generic() {
        return generic;
    }

    public ClassMethod declaration() {
        return new ClassMethod(pos(), modifier,
                name, generic, prototype, returnThis);
    }

    //

    private ClassDefinition master;
    private List<ClassMethod> override = new ArrayList<>();
    private boolean updater;

    public ClassDefinition master() {
        return master;
    }

    public void master(ClassDefinition master) {
        this.master = master;
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
        if (master == null)
            return name.value() + prototype;
        return master.symbol() + "." +
                name + prototype;
    }
}
