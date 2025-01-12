package org.cossbow.feng.ast.dcl;

import org.cossbow.feng.ast.Entity;
import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.Lazy;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.attr.Modifier;

public class Variable extends Entity {
    private final Modifier modifier;
    private final Declare declare;
    private final Identifier name;
    private final Lazy<TypeDeclarer> type;

    public Variable(Position pos,
                    Modifier modifier,
                    Declare declare,
                    Identifier name,
                    Lazy<TypeDeclarer> type) {
        super(pos);
        this.modifier = modifier;
        this.declare = declare;
        this.name = name;
        this.type = type;
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
}
