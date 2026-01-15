package org.cossbow.feng.ast;

import java.util.List;

public class SymbolTable<T>
        extends UniqueTable<Symbol, T> {
    public SymbolTable() {
    }

    public SymbolTable(int initCapacity) {
        super(initCapacity);
    }

    public SymbolTable(List<Node<Symbol, T>> nodes) {
        super(nodes);
    }
}
