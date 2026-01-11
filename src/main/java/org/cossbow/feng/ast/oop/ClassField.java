package org.cossbow.feng.ast.oop;

import org.cossbow.feng.ast.Exportable;
import org.cossbow.feng.ast.Field;
import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.attr.Modifier;
import org.cossbow.feng.ast.dcl.Declare;
import org.cossbow.feng.ast.dcl.TypeDeclarer;

public class ClassField extends Field
        implements Exportable {
    private Modifier modifier;
    private boolean export;
    private Declare declare;

    public ClassField(Position pos,
                      Modifier modifier,
                      boolean export,
                      Declare declare,
                      Identifier name,
                      TypeDeclarer type) {
        super(pos, name, type);
        this.export = export;
        this.declare = declare;
        this.modifier = modifier;
    }

    public Declare declare() {
        return declare;
    }

    public Modifier modifier() {
        return modifier;
    }

    @Override
    public boolean export() {
        return export;
    }

}
