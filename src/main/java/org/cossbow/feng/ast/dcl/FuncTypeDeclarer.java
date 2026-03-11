package org.cossbow.feng.ast.dcl;

import org.cossbow.feng.ast.Method;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.proc.FunctionDefinition;
import org.cossbow.feng.ast.proc.Prototype;
import org.cossbow.feng.util.Lazy;

/**
 * 临时及(AST)都有
 */
public class FuncTypeDeclarer extends TypeDeclarer {
    private final Prototype prototype;
    private final Type type;

    public FuncTypeDeclarer(Position pos,
                            Prototype prototype,
                            Type type) {
        super(pos);
        this.prototype = prototype;
        this.type = type;
    }

    public Prototype prototype() {
        return prototype;
    }


    public Type type() {
        return type;
    }

    public boolean isRefer() {
        return type == Type.REFER;
    }

    public boolean hasTemplate() {
        return prototype.hasTemplate();
    }

    public enum Type {
        FUNC,
        REFER,
        METHOD,
    }

    //

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof FuncTypeDeclarer t))
            return false;

        return prototype.equals(t.prototype);
    }

    @Override
    public int hashCode() {
        return prototype.hashCode();
    }

    //

    @Override
    public String toString() {
        return prototype.toString();
    }
}
