package org.cossbow.feng.ast;

import org.cossbow.feng.util.Groups;

import java.util.List;

public class IdentifierTable<T>
        extends UniqueTable<Identifier, T> {
    public IdentifierTable() {
    }

    public IdentifierTable(int initCapacity) {
        super(initCapacity);
    }

    public IdentifierTable(List<Groups.G2<Identifier, T>> data) {
        super(data);
    }

}
