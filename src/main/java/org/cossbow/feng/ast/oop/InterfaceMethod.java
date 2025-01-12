package org.cossbow.feng.ast.oop;

import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.ProcDefinition;
import org.cossbow.feng.ast.attr.Modifier;
import org.cossbow.feng.ast.gen.TypeParameters;
import org.cossbow.feng.ast.proc.Prototype;

import java.util.Optional;

public class InterfaceMethod extends ProcDefinition {
    private final Prototype prototype;

    public InterfaceMethod(Position pos,
                           Modifier modifier,
                           Identifier name,
                           TypeParameters generic,
                           Prototype prototype) {
        super(pos, modifier, Optional.of(name), generic);
        this.prototype = prototype;
    }

    public Prototype prototype() {
        return prototype;
    }
}
