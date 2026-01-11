package org.cossbow.feng.ast.proc;


import org.cossbow.feng.ast.dcl.TypeDeclarer;

import java.util.List;

public class ParameterSet {

    public List<TypeDeclarer> types() {
        return List.of();
    }

    public int size() {
        return 0;
    }

    public boolean isEmpty() {
        return size() == 0;
    }
}
