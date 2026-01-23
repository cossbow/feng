package org.cossbow.feng.ast;

import org.cossbow.feng.ast.gen.TypeParameters;
import org.cossbow.feng.ast.proc.Prototype;

abstract
public class Method extends Entity {

    public Method(Position pos) {
        super(pos);
    }

    abstract
    public Prototype prototype();

    abstract
    public TypeParameters generic();

}
