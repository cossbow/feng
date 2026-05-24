package org.cossbow.feng.ast;

import org.cossbow.feng.ast.dcl.TypeDeclarer;

/**
 * Using to storage the value:
 * <p>
 * 1. read the value: {@code var id = user.id;}
 * <p>
 * 2. write the value: {@code user.id = id;}
 */
abstract
public class Field extends Entity
        implements Exportable {
    /**
     * The identifier for the field
     */
    private final Identifier name;
    /**
     * The type can be resetted in analysis,
     * because the length of the array may be
     * a constant expression, it needs to be
     * set to the calculated value during analysis.
     */
    private TypeDeclarer type;

    public Field(Position pos,
                 Identifier name,
                 TypeDeclarer type) {
        super(pos);
        this.name = name;
        this.type = type;
    }

    /**
     * Allow some types to set whether to export
     * when defining fields
     */
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

    /**
     * Some abstract field can't be phantom-referenced
     */
    public boolean enablePhantom() {
        return true;
    }

    //

    @Override
    public String toString() {
        return name + " " + type;
    }
}
