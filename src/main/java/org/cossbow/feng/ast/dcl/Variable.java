package org.cossbow.feng.ast.dcl;

import org.cossbow.feng.ast.Entity;
import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.attr.Modifier;
import org.cossbow.feng.ast.expr.Expression;
import org.cossbow.feng.util.Lazy;

import java.util.concurrent.atomic.AtomicInteger;

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

    public Expression requireValue() {
        return value.has() ? value.must() : defVal.must();
    }

    private final Lazy<Expression> defVal = Lazy.nil();
    private final int id = IdGenerator.getAndIncrement();

    public Lazy<Expression> defVal() {
        return defVal;
    }

    public int id() {
        return id;
    }

    //

    public static Variable newArg(Identifier name, TypeDeclarer type) {
        return new Variable(name.pos(), Modifier.empty(), Declare.CONST,
                name, Lazy.of(type), Lazy.nil());
    }

    //

    private static final AtomicInteger IdGenerator =
            new AtomicInteger(1);

    //

    @Override
    public String toString() {
        if (type.none()) return declare.code + " " + name;
        return declare.code + " " + name + " " + type.get();
    }
}
