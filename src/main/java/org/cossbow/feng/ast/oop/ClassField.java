package org.cossbow.feng.ast.oop;

import org.cossbow.feng.ast.Entity;
import org.cossbow.feng.ast.Exportable;
import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.attr.Modifier;
import org.cossbow.feng.ast.dcl.Declare;
import org.cossbow.feng.ast.dcl.TypeDeclarer;

public class ClassField extends Entity
        implements Exportable {
    private Modifier modifier;
    private boolean export;
    private Declare declare;
    private Identifier name;
    private TypeDeclarer type;

    public ClassField(Position pos,
                      Modifier modifier,
                      boolean export,
                      Declare declare,
                      Identifier name,
                      TypeDeclarer type) {
        super(pos);
        this.export = export;
        this.declare = declare;
        this.type = type;
        this.modifier = modifier;
        this.name = name;
    }

    public Declare declare() {
        return declare;
    }

    public TypeDeclarer type() {
        return type;
    }

    public Modifier modifier() {
        return modifier;
    }

    @Override
    public boolean export() {
        return export;
    }

    public Identifier name() {
        return name;
    }
}
