package org.cossbow.feng.ast.oop;

import org.cossbow.feng.ast.Field;
import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.attr.Modifier;
import org.cossbow.feng.ast.dcl.Declare;
import org.cossbow.feng.ast.dcl.TypeDeclarer;

import java.util.Objects;

/**
 * Fields defined in a subclass cannot shadow fields
 * of the same name in the superclass.
 * <p>
 * Define fields in classes:
 * <p>
 * Define a unmodifiable field: {@code const id int;}.
 * <p>
 * Define a modifiable field: {@code var count int;}
 */
public class ClassField extends Field {
    /**
     * Independent setting modifier
     */
    private final Modifier modifier;
    /**
     * Field can be const
     */
    private final Declare declare;

    public ClassField(Position pos,
                      Modifier modifier,
                      Declare declare,
                      Identifier name,
                      TypeDeclarer type) {
        super(pos, name, type);
        this.declare = declare;
        this.modifier = modifier;
    }

    public boolean export() {
        return modifier.export();
    }

    public Declare declare() {
        return declare;
    }

    public Modifier modifier() {
        return modifier;
    }

    @Override
    public boolean unmodifiable() {
        return declare == Declare.CONST;
    }

    //

    /**
     * Which class this field belong to
     */
    private ClassDefinition master;

    public ClassDefinition master() {
        return master;
    }

    public void master(ClassDefinition master) {
        this.master = Objects.requireNonNull(master);
    }

    public ClassField clone() {
        return (ClassField) super.clone();
    }

    //

    //

    @Override
    public String toString() {
        return declare + " " + super.toString();
    }
}
