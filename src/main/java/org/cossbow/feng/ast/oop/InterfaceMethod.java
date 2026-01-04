package org.cossbow.feng.ast.oop;

import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.Optional;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.attr.Modifier;
import org.cossbow.feng.ast.gen.TypeParameters;
import org.cossbow.feng.ast.proc.Prototype;
import org.cossbow.feng.ast.proc.PrototypeDefinition;

public class InterfaceMethod extends PrototypeDefinition {
    public InterfaceMethod(Position pos,
                           Modifier modifier,
                           Identifier name,
                           TypeParameters generic,
                           Prototype prototype) {
        super(pos, modifier, Optional.of(name),
                generic, prototype);
    }
}
