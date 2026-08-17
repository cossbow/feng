package org.cossbow.feng.ast.expr;

import org.cossbow.feng.ast.Entity;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.dcl.TypeDeclarer;
import org.cossbow.feng.ast.gen.GenericMap;
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
     * Means the value is uniquely referenced
     */
    public boolean unique() {
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

    /**
     * Deep copy for variadic inline expansion.
     * Each expansion gets independent mutable state
     * (expectType, resultType, expectCallable).
     */
    public abstract Expression mirror();

    /**
     * Rebuild a fully monomorphized copy of this expression: substitute type
     * variables through {@code gm}, rewrite generic call sites, and remap
     * {@link #resultType}/{@link #expectType}.
     */
    public abstract Expression mono(GenericMap gm);

    /**
     * Map this node's {@code resultType}/{@code expectType} through {@code gm}
     * onto a freshly constructed node {@code n}, returning {@code n}.
     */
    protected <E extends Expression> E monoCopy(E n, GenericMap gm) {
        resultType.use(t -> n.resultType.set(gm.mapIf(t)));
        expectType.use(t -> n.expectType.set(gm.mapIf(t)));
        return n;
    }

}
