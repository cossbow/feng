package org.cossbow.feng.ast.oop;

import org.cossbow.feng.ast.Exportable;
import org.cossbow.feng.ast.Field;
import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.attr.Modifier;
import org.cossbow.feng.ast.dcl.Declare;
import org.cossbow.feng.ast.dcl.TypeDeclarer;
import org.cossbow.feng.ast.dcl.Variable;
import org.cossbow.feng.util.Lazy;

public class ClassField extends Field implements Exportable {
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

    public FieldVariable variable() {
        return new FieldVariable(pos(), modifier, declare,
                name(), Lazy.of(type()), this);
    }

    @Override
    public boolean immutable() {
        return declare == Declare.CONST;
    }

    //

    private final Lazy<ClassDefinition> master = Lazy.nil();

    public Lazy<ClassDefinition> master() {
        return master;
    }

    //

    public static class FieldVariable extends Variable {
        private final ClassField field;

        public FieldVariable(Position pos, Modifier modifier,
                             Declare declare, Identifier name,
                             Lazy<TypeDeclarer> type, ClassField field) {
            super(pos, modifier, declare, name, type,
                    Lazy.nil());
            this.field = field;
        }

        public ClassField field() {
            return field;
        }
    }

    //

    @Override
    public String toString() {
        return declare + " " + super.toString();
    }
}
