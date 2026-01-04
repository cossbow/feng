package org.cossbow.feng.ast.dcl;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.proc.Prototype;

public class FuncTypeDeclarer extends TypeDeclarer {
    private Prototype prototype;

    public FuncTypeDeclarer(Position pos,
                            Prototype prototype) {
        super(pos);
        this.prototype = prototype;
    }

    public Prototype prototype() {
        return prototype;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof FuncTypeDeclarer ftd) {
            return prototype.equals(ftd.prototype);
        }
        return false;
    }
}
