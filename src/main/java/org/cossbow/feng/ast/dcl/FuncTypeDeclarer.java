package org.cossbow.feng.ast.dcl;

import org.cossbow.feng.ast.Method;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.gen.TypeArguments;
import org.cossbow.feng.ast.proc.Prototype;
import org.cossbow.feng.util.Optional;

/**
 * 临时及(AST)都有
 */
public class FuncTypeDeclarer extends TypeDeclarer {
    private final Prototype prototype;
    private final TypeArguments generic;
    private final Optional<? extends Method> method;

    public FuncTypeDeclarer(Position pos,
                            Prototype prototype,
                            TypeArguments generic,
                            Optional<? extends Method> method) {
        super(pos);
        this.prototype = prototype;
        this.generic = generic;
        this.method = method;
    }

    public FuncTypeDeclarer(Position pos,
                            Prototype prototype,
                            TypeArguments generic) {
        this(pos, prototype, generic, Optional.empty());
    }

    public Prototype prototype() {
        return prototype;
    }

    public TypeArguments generic() {
        return generic;
    }

    public Optional<? extends Method> method() {
        return method;
    }


    @Override
    public boolean equals(Object o) {
        if (!(o instanceof FuncTypeDeclarer t))
            return false;

        return prototype.equals(t.prototype)
                && generic.equals(t.generic);
    }

    @Override
    public int hashCode() {
        int result = prototype.hashCode();
        result = 31 * result + generic.hashCode();
        return result;
    }

    //

    @Override
    public String toString() {
        if (generic.isEmpty())
            return prototype.toString();
        return generic.toString() + prototype;
    }
}
