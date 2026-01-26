package org.cossbow.feng.ast.proc;

import org.cossbow.feng.ast.Entity;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.dcl.TypeDeclarer;
import org.cossbow.feng.util.Optional;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class Prototype extends Entity {
    private VariableParameterSet parameterSet;
    private Optional<TypeDeclarer> returnSet;

    public Prototype(Position pos,
                     VariableParameterSet parameterSet,
                     Optional<TypeDeclarer> returnSet) {
        super(pos);
        this.parameterSet = parameterSet;
        this.returnSet = returnSet;
    }

    public VariableParameterSet parameterSet() {
        return parameterSet;
    }

    public Optional<TypeDeclarer> returnSet() {
        return returnSet;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Prototype p)) return false;
        return parameterSet.equals(p.parameterSet) &&
                returnSet.equals(p.returnSet);
    }

    @Override
    public int hashCode() {
        int result = parameterSet.hashCode();
        result = 31 * result + returnSet.hashCode();
        return result;
    }
//


    @Override
    public String toString() {
        if (returnSet.none())
            return "(" + parameterSet + ") ";
        return "(" + parameterSet + ") " + returnSet.get();
    }
}
