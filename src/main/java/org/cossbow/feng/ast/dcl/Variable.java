package org.cossbow.feng.ast.dcl;

import org.cossbow.feng.ast.Entity;
import org.cossbow.feng.ast.Exportable;
import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.attr.Modifier;
import org.cossbow.feng.ast.expr.Expression;
import org.cossbow.feng.util.Lazy;

import java.util.concurrent.atomic.AtomicInteger;

public class Variable extends Entity
        implements Exportable {
    /**
     * For modifying variable
     */
    private final Modifier modifier;
    private final Declare declare;
    /**
     * For identifying variables
     */
    private final Identifier name;
    /**
     * If the type is omitted, it's inferred in the analysis
     */
    private Lazy<TypeDeclarer> type;
    /**
     * For initialization
     */
    private Lazy<Expression> value;

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

    public boolean export() {
        return modifier().export();
    }

    public Modifier modifier() {
        return modifier;
    }

    public Declare declare() {
        return declare;
    }

    public boolean isConst() {
        return declare == Declare.CONST;
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

    public Variable mirror() {
        return new Variable(pos(), modifier, declare, name,
                type.clone(), value.clone());
    }

    //

    /**
     * Because variable Shadowing is allowed, this self
     * increasing ID is used to distinguish it.
     */
    private final int id = IdGenerator.getAndIncrement();

    public int id() {
        return id;
    }


    //

    @Override
    public Variable clone() {
        var r = (Variable) super.clone();
        // Sharing Lazy<T>may result in unexpected modifications
        r.type = type.clone();
        r.value = value.clone();
        return r;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Variable v))
            return false;
        return id == v.id;
    }

    @Override
    public int hashCode() {
        return id;
    }

    private static final AtomicInteger IdGenerator =
            new AtomicInteger(1);

    //

    @Override
    public String toString() {
        if (type.none()) return declare.code + " " + name;
        return declare.code + " " + name + " " + type.get();
    }
}
