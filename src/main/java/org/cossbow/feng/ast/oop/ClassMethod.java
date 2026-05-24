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

/**
 * Class method is an executable procedure.
 */
public class ClassMethod extends Method
        implements Exportable {
    /**
     * Independent setting modifier
     */
    private final Modifier modifier;
    /**
     * For identify method
     */
    private final Identifier name;
    /**
     * Allow generic parameters to be defined on methods
     */
    private final TypeParameters generic;
    /**
     * This method must be an escaped instance to be called
     */
    private final boolean escaped;
    /**
     * This method can't modify the instance
     */
    private final boolean unmodifiable;
    /**
     * The prototype of this method
     */
    private final Prototype prototype;
    /**
     * Procedure is the implementation of this method,
     * it is non empty in the defined class.
     * <p>
     * The metadata does not include the procedure.
     */
    private final Optional<Procedure> procedure;
    /**
     * [imcompleted]
     */
    private final boolean returnThis;

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

    public Position pos() {
        return name.pos();
    }

    //

    /**
     * Which class this method belong to
     */
    private ClassDefinition master;
    /**
     * Recorded all the method definition that override this method
     */
    private final List<ClassMethod> override = new ArrayList<>();

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
