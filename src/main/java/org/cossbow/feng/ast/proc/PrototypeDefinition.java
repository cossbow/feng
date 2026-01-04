package org.cossbow.feng.ast.proc;

import org.cossbow.feng.ast.*;
import org.cossbow.feng.util.Optional;
import org.cossbow.feng.ast.attr.Modifier;
import org.cossbow.feng.ast.gen.TypeParameters;

public class PrototypeDefinition extends TypeDefinition {
    private Prototype prototype;

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
