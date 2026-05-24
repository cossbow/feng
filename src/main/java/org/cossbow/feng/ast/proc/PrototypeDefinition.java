package org.cossbow.feng.ast.proc;

import org.cossbow.feng.ast.*;
import org.cossbow.feng.util.Optional;
import org.cossbow.feng.ast.attr.Modifier;
import org.cossbow.feng.ast.gen.TypeParameters;

/**
 * Define a named prototype to simplify the declaration
 * of function type variables.
 * <p>
 * A function type variable can be defined like:
 * <p>
 * {@code var call func(int)bool;}
 * <p>
 * If we define a named prototype like:
 * <p>
 * {@code func Callable=(int)bool;}
 * <p>
 * Variable declaration can be simplified as:
 * <p>
 * {@code var call Callable;}
 * <p>
 * And the two statements are equivalent.
 */
public class PrototypeDefinition extends TypeDefinition {
    private final Prototype prototype;

    public PrototypeDefinition(Position pos,
                               Modifier modifier,
                               Symbol symbol,
                               TypeParameters generic,
                               Prototype prototype) {
        super(pos, modifier, symbol, generic, TypeDomain.FUNC);
        this.prototype = prototype;
    }

    public Prototype prototype() {
        return prototype;
    }
}
