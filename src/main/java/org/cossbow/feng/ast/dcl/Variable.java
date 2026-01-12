package org.cossbow.feng.ast.dcl;

import org.cossbow.feng.ast.Entity;
import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.attr.Modifier;
import org.cossbow.feng.ast.oop.ClassField;
import org.cossbow.feng.util.Lazy;
import org.cossbow.feng.util.Optional;

public class Variable extends Entity {
    private Modifier modifier;
    private Declare declare;
    private Identifier name;
    private Lazy<TypeDeclarer> type;
    private Optional<ClassField> context;

    public Variable(Position pos,
                    Modifier modifier,
                    Declare declare,
                    Identifier name,
                    Lazy<TypeDeclarer> type,
                    Optional<ClassField> context) {
        super(pos);
        this.modifier = modifier;
        this.declare = declare;
        this.name = name;
        this.type = type;
        this.context = context;
    }

    public Variable(Position pos,
                    Modifier modifier,
                    Declare declare,
                    Identifier name,
                    Lazy<TypeDeclarer> type) {
        this(pos, modifier, declare, name, type, Optional.empty());
    }

    public Variable(Position pos,
                    Modifier modifier,
                    Declare declare,
                    Identifier name) {
        this(pos, modifier, declare, name, Lazy.nil());
    }

    public Modifier modifier() {
        return modifier;
    }

    public Declare declare() {
        return declare;
    }

    public Identifier name() {
        return name;
    }

    public Lazy<TypeDeclarer> type() {
        return type;
    }

    public Optional<ClassField> context() {
        return context;
    }
}
