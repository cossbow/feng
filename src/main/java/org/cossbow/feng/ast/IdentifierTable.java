package org.cossbow.feng.ast;

import java.util.List;
import java.util.Map;

public class IdentifierTable<T>
        extends UniqueTable<Identifier, T> {
    public IdentifierTable() {
    }

    public IdentifierTable(int initCapacity) {
        super(initCapacity);
    }

    public IdentifierTable(List<Node<Identifier, T>> nodes) {
        super(nodes);
    }

    public IdentifierTable(Map<Identifier, T> entries) {
        super(entries);
    }
}
