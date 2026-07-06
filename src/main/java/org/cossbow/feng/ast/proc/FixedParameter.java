package org.cossbow.feng.ast.proc;

import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.attr.Modifier;
import org.cossbow.feng.ast.dcl.Declare;
import org.cossbow.feng.ast.dcl.Primitive;
import org.cossbow.feng.ast.dcl.TypeDeclarer;
import org.cossbow.feng.ast.dcl.Variable;
import org.cossbow.feng.util.Lazy;
import org.cossbow.feng.util.Optional;

/**
 * Fixed parameters is common parameters
 */
public class FixedParameter extends Parameter {
    /**
     * Can have attributes
     */
    private final Modifier modifier;
    private final Optional<Identifier> name;
    private TypeDeclarer type;

    public FixedParameter(Position pos,
                          Modifier modifier,
                          Optional<Identifier> name,
                          TypeDeclarer type) {
        super(pos);
        this.modifier = modifier;
        this.name = name;
        this.type = type;
    }

    public FixedParameter(Position pos,
                          Modifier modifier,
                          Identifier name,
                          TypeDeclarer type) {
        this(pos, modifier, Optional.of(name), type);
    }

    public FixedParameter(Position pos,
                          Identifier name,
                          TypeDeclarer type) {
        this(pos, Modifier.empty(), name, type);
    }

    public FixedParameter(Position pos,
                          TypeDeclarer type) {
        this(pos, Modifier.empty(), Optional.empty(), type);
    }

    public Modifier modifier() {
        return modifier;
    }

    public Optional<Identifier> name() {
        return name;
    }

    public TypeDeclarer type() {
        return type;
    }

    public void type(TypeDeclarer type) {
        this.type = type;
    }

    //

    private Variable variable;

    /**
     * Analyzing the body of procedures need make local
     * variables from the parameters.
     * <p>
     * But if it's anonymous, no variable will be created.
     */
    public Optional<Variable> var() {
        if (name.none()) return Optional.empty();

        var v = variable;
        if (v != null) return Optional.of(v);

        v = new Variable(pos(), modifier(),
                Declare.CONST, name.must(),
                Lazy.of(type()), Lazy.nil());
        variable = v;
        return Optional.of(v);
    }

    // creator

    public static FixedParameter create(
            String name, Primitive primitive) {
        return new FixedParameter(Position.ZERO,
                new Identifier(name),
                primitive.declarer());
    }

    //
    @Override
    public String toString() {
        if (name.none())
            return "_ " + type();
        return name + " " + type();
    }
}
