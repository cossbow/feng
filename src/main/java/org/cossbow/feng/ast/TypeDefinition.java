package org.cossbow.feng.ast;

import org.cossbow.feng.ast.attr.Modifier;
import org.cossbow.feng.ast.gen.TypeParameters;
import org.cossbow.feng.util.Optional;

abstract
public class TypeDefinition extends Definition {

    public TypeDefinition(Position pos,
                          Modifier modifier,
                          Optional<Identifier> name,
                          TypeParameters generic) {
        super(pos, modifier, name, generic);
    }

}
