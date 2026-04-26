package org.cossbow.feng.ast.oop;

import org.cossbow.feng.ast.Field;
import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.attr.Modifier;
import org.cossbow.feng.ast.dcl.Declare;
import org.cossbow.feng.ast.dcl.TypeDeclarer;

import java.util.Objects;

public class ClassField extends Field {
    private Modifier modifier;
    private Declare declare;

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
