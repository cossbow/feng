package org.cossbow.feng.ast.dcl;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.proc.Prototype;

/**
 * This type of variable or field will reference a function.
 *
 */
abstract
public class FuncTypeDeclarer extends TypeDeclarer {
    /**
     * required: it can't be nil if true.
     * <p>
     * example: {@code var f !func();}, {@code var f !Task;}
     */
    private final boolean required;

    public FuncTypeDeclarer(Position pos,
                            boolean required) {
        super(pos);
        this.required = required;
    }

    public boolean required() {
        return required;
    }

    abstract
    public Prototype prototype();

    public boolean hasTypeVar() {
        return prototype().hasTypeVar();
    }

}
