package org.cossbow.feng.ast.proc;

import org.cossbow.feng.ast.dcl.TypeDeclarer;

import java.util.List;

public class UnnamedParameterSet extends ParameterSet {
    private List<TypeDeclarer> types;

    public UnnamedParameterSet(List<TypeDeclarer> types) {
        this.types = types;
    }

    public List<TypeDeclarer> types() {
        return types;
    }

    @Override
    public int size() {
        return types.size();
    }

    public TypeDeclarer getType(int index) {
        return types.get(index);
    }

}
