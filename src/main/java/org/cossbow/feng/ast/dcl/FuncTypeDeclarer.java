package org.cossbow.feng.ast.dcl;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.proc.Prototype;

/**
 * 临时及(AST)都有
 */
abstract
public class FuncTypeDeclarer extends TypeDeclarer {
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

    public boolean hasTemplate() {
        return prototype().hasTemplate();
    }

}
