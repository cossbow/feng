package org.cossbow.feng.ast;

import org.cossbow.feng.ast.attr.Modifier;
import org.cossbow.feng.ast.gen.TypeParameters;

import java.util.Optional;

abstract
public class ProcDefinition extends Definition {

    public ProcDefinition(Position pos,
                          Modifier modifier,
                          Optional<Identifier> name,
                          TypeParameters generic) {
        super(pos, modifier, name, generic);
    }

}
