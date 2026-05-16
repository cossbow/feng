package org.cossbow.feng.ast.expr;

import org.cossbow.feng.ast.Entity;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.dcl.TypeDeclarer;
import org.cossbow.feng.util.Lazy;

/**
 * Expression root type
 */
abstract
public class Expression extends Entity {

    public Expression(Position pos) {
        super(pos);
    }

    /**
     * A free, temporary value that is not bound to a
     * variable, field, or array element
     */
    public boolean unbound() {
        return false;
    }

    /**
     * Composite literals require an expected type for checking.
     * Please fill in the type of the expression on the left here.
     */
    public final Lazy<TypeDeclarer> expectType = Lazy.nil();
    /**
     * The type inferred from the expression is placed here
     */
    public final Lazy<TypeDeclarer> resultType = Lazy.nil();

    /**
     * For analysis
     * <p>
     * used to indicate the expected value is a callable procedure:
     * a function or method
     */
    private boolean expectCallable;

    public boolean expectCallable() {
        return expectCallable;
    }

    public void expectCallable(boolean expectCallable) {
        this.expectCallable = expectCallable;
    }

}
