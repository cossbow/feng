package org.cossbow.feng.ast.var;

import org.cossbow.feng.ast.Entity;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.dcl.TypeDeclarer;
import org.cossbow.feng.ast.dcl.Variable;
import org.cossbow.feng.ast.expr.Expression;
import org.cossbow.feng.util.Lazy;

import java.util.ArrayList;
import java.util.List;

/**
 * The target modified in the assignment statement
 */
abstract
public class Operand extends Entity {
    public Operand(Position pos) {
        super(pos);
    }

    /**
     * Convert to the same type of right-hand expression
     */
    abstract public Expression rhs();

    /**
     * Types of operand: derived
     */
    public final Lazy<TypeDeclarer> type = Lazy.nil();

    public Lazy<TypeDeclarer> type() {
        return type;
    }

    /**
     * Deep copy for variadic inline expansion.
     * Resets mutable state: type.
     */
    public abstract Operand mirror();
}
