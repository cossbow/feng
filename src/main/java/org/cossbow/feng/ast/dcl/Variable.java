package org.cossbow.feng.ast.dcl;

import org.cossbow.feng.ast.Entity;
import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.attr.Modifier;
import org.cossbow.feng.ast.expr.Expression;
import org.cossbow.feng.util.Lazy;

public class Variable extends Entity {
    private final Modifier modifier;
    private final Declare declare;
    private final Identifier name;
    private final Lazy<TypeDeclarer> type;
    private final Lazy<Expression> value;

    public Variable(Position pos,
                    Modifier modifier,
                    Declare declare,
                    Identifier name,
                    Lazy<TypeDeclarer> type,
                    Lazy<Expression> value) {
        super(pos);
        this.modifier = modifier;
        this.declare = declare;
        this.name = name;
        this.type = type;
        this.value = value;
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

    public Lazy<Expression> value() {
        return value;
    }

    //

    @Override
    public String toString() {
        if (type.none()) return declare.code + " " + name;
        return declare.code + " " + name + " " + type.get();
    }
}
