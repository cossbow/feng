package org.cossbow.feng.ast;

import org.cossbow.feng.ast.dcl.TypeDeclarer;

abstract
public class Field extends Entity
        implements Exportable {
    private Identifier name;
    private TypeDeclarer type;

    public Field(Position pos,
                 Identifier name,
                 TypeDeclarer type) {
        super(pos);
        this.name = name;
        this.type = type;
    }

    public boolean export() {
        return true;
    }

    public Identifier name() {
        return name;
    }

    public TypeDeclarer type() {
        return type;
    }

    public void type(TypeDeclarer type) {
        this.type = type;
    }

    public boolean unmodifiable() {
        return false;
    }

    public boolean enablePhantom() {
        return true;
    }

    //

    @Override
    public String toString() {
        return name + " " + type;
    }
}
