package org.cossbow.feng.ast;

import org.cossbow.feng.util.Groups;

import java.util.List;

public class IdentifierMap<T>
        extends OrderlyMap<Identifier, T> {
    public IdentifierMap() {
    }

    public IdentifierMap(int initCapacity) {
        super(initCapacity);
    }

    public IdentifierMap(List<Groups.G2<Identifier, T>> data) {
        super(data);
    }

}
