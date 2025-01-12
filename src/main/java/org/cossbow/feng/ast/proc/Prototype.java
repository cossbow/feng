package org.cossbow.feng.ast.proc;

import org.cossbow.feng.ast.Entity;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.dcl.TypeDeclarer;

import java.util.List;

public class Prototype extends Entity {
    private final List<Parameter> parameters;
    private final List<TypeDeclarer> returnSet;

    public Prototype(Position pos,
                     List<Parameter> parameters,
                     List<TypeDeclarer> returnSet) {
        super(pos);
        this.parameters = parameters;
        this.returnSet = returnSet;
    }

    public List<Parameter> parameters() {
        return parameters;
    }

    public List<TypeDeclarer> returnSet() {
        return returnSet;
    }
}
