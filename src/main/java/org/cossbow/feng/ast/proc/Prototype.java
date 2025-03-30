package org.cossbow.feng.ast.proc;

import org.cossbow.feng.ast.Entity;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.dcl.TypeDeclarer;

import java.util.List;

public class Prototype extends Entity {
    private final ParameterSet parameterSet;
    private final List<TypeDeclarer> returnSet;

    public Prototype(Position pos,
                     ParameterSet parameterSet,
                     List<TypeDeclarer> returnSet) {
        super(pos);
        this.parameterSet = parameterSet;
        this.returnSet = returnSet;
    }

    public ParameterSet parameterSet() {
        return parameterSet;
    }

    public List<TypeDeclarer> returnSet() {
        return returnSet;
    }
}
