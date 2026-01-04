package org.cossbow.feng.ast.proc;

import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.Optional;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.TypeDefinition;
import org.cossbow.feng.ast.attr.Modifier;
import org.cossbow.feng.ast.gen.TypeParameters;

public class PrototypeDefinition extends TypeDefinition {
    private Prototype prototype;

    public PrototypeDefinition(Position pos,
                               Modifier modifier,
                               Optional<Identifier> name,
                               TypeParameters generic,
                               Prototype prototype) {
        super(pos, modifier, name, generic);
        this.prototype = prototype;
    }

    public Prototype prototype() {
        return prototype;
    }
}
