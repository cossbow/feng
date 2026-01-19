package org.cossbow.feng.ast.dcl;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.gen.TypeArguments;
import org.cossbow.feng.ast.proc.Prototype;

/**
 * 临时及(AST)都有
 */
public class FuncTypeDeclarer extends TypeDeclarer {
    private Prototype prototype;
    private TypeArguments generic;

    public FuncTypeDeclarer(Position pos,
                            Prototype prototype,
                            TypeArguments generic) {
        super(pos);
        this.prototype = prototype;
        this.generic = generic;
    }

    public Prototype prototype() {
        return prototype;
    }

    public TypeArguments generic() {
        return generic;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof FuncTypeDeclarer t))
            return false;

        return prototype.equals(t.prototype)
                || generic.equals(t.generic);
    }
}
