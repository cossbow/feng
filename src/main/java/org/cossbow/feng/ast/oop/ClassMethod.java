package org.cossbow.feng.ast.oop;

import org.cossbow.feng.ast.Exportable;
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

public class ClassMethod extends Method
        implements Exportable {
    private Modifier modifier;
    private Identifier name;
    private TypeParameters generic;
    private final boolean escaped;
    private final boolean unmodifiable;
    private Prototype prototype;
    private Optional<Procedure> procedure;
    private boolean returnThis;

    public ClassMethod(Position pos,
                       Modifier modifier,
                       Identifier name,
                       TypeParameters generic,
                       boolean escaped,
                       boolean unmodifiable,
                       Prototype prototype,
                       Optional<Procedure> procedure,
                       boolean returnThis) {
        super(pos);
        this.modifier = modifier;
        this.name = name;
        this.generic = generic;
        this.escaped = escaped;
        this.unmodifiable = unmodifiable;
        this.prototype = prototype;
        this.procedure = procedure;
        this.returnThis = returnThis;
    }

    public ClassMethod(Position pos,
                       Modifier modifier,
                       Identifier name,
                       TypeParameters generic,
                       boolean escaped,
                       boolean unmodifiable,
                       Procedure procedure,
                       boolean returnThis) {
        this(pos, modifier, name, generic, escaped,
                unmodifiable, procedure.prototype(),
                Optional.of(procedure), returnThis);
    }

    public ClassMethod(Position pos,
                       Modifier modifier,
                       Identifier name,
                       TypeParameters generic,
                       boolean escaped,
                       boolean unmodifiable,
                       Prototype prototype,
                       boolean returnThis) {
        this(pos, modifier, name, generic, escaped,
                unmodifiable, prototype,
                Optional.empty(), returnThis);
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

    public boolean escaped() {
        return escaped;
    }

    public boolean unmodifiable() {
        return unmodifiable;
    }

    public ClassMethod declaration() {
        return new ClassMethod(pos(), modifier, name,
                generic, escaped, unmodifiable, prototype,
                returnThis);
    }

    public Position pos() {
        return name.pos();
    }

    //

    private ClassDefinition master;
    private List<ClassMethod> override = new ArrayList<>();

    public ClassDefinition master() {
        return master;
    }

    public void master(ClassDefinition master) {
        this.master = master;
    }

    public List<ClassMethod> override() {
        return override;
    }


    //
    @Override
    public String toString() {
        if (master == null)
            return name.value() + generic + prototype;
        return master.symbol() + "." +
                name + generic + prototype;
    }
}
