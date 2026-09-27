package org.cossbow.feng.ast.type;

import org.cossbow.feng.ast.*;

final
public class Concept extends Entity implements Exportable {
    private final boolean export;
    private final Symbol symbol;
    private TypeConstraint expr;

    public Concept(Position pos,
                   boolean export,
                   Symbol symbol,
                   TypeConstraint expr) {
        super(pos);
        this.export = export;
        this.symbol = symbol;
        this.expr = expr;
    }

    public boolean export() {
        return export;
    }

    public Symbol symbol() {
        return symbol;
    }

    public TypeConstraint expr() {
        return expr;
    }

    public void expr(TypeConstraint expr) {
        this.expr = expr;
    }

    //

    public static final Concept Syncable;

    static {
        Syncable = new Concept(
                Position.ZERO, true,
                new Symbol(new Identifier("Syncable")),
                new SyncableTypeConstraint());
    }

    //

    @Override
    public boolean equals(Object o) {
        return o instanceof Concept c &&
                symbol.equals(c.symbol);

    }

    @Override
    public int hashCode() {
        return symbol.hashCode();
    }

    //
    @Override
    public String toString() {
        return "concept " + symbol;
    }
}
