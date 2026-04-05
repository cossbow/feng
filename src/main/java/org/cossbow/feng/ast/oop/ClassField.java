package org.cossbow.feng.ast.oop;

import org.cossbow.feng.ast.Field;
import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.attr.Modifier;
import org.cossbow.feng.ast.dcl.Declare;
import org.cossbow.feng.ast.dcl.TypeDeclarer;
import org.cossbow.feng.util.Lazy;

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

    public Declare declare() {
        return declare;
    }

    public Modifier modifier() {
        return modifier;
    }

    public boolean export() {
        return modifier.export();
    }

    @Override
    public boolean immutable() {
        return declare == Declare.CONST;
    }

    //

    private Lazy<ClassDefinition> master = Lazy.nil();

    public Lazy<ClassDefinition> master() {
        return master;
    }

    public ClassField clone() {
        var n = (ClassField) super.clone();
        n.master = master.clone();
        return n;
    }

    //

    //

    @Override
    public String toString() {
        return declare + " " + super.toString();
    }
}
