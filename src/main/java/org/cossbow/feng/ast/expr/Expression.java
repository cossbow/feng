package org.cossbow.feng.ast.expr;

import org.cossbow.feng.ast.Entity;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.dcl.TypeDeclarer;
import org.cossbow.feng.util.Lazy;

abstract
public class Expression extends Entity {

    public Expression(Position pos) {
        super(pos);
    }

    public boolean isFinal() {
        return false;
    }

    public boolean unbound() {
        return false;
    }

    public final Lazy<TypeDeclarer> resultType = Lazy.nil();


    //

    private volatile boolean expectCallable;

    public boolean expectCallable() {
        return expectCallable;
    }

    public void expectCallable(boolean expectCallable) {
        this.expectCallable = expectCallable;
    }

}
