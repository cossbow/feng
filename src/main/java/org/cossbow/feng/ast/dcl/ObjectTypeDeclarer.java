package org.cossbow.feng.ast.dcl;

import org.cossbow.feng.ast.IdentifierTable;
import org.cossbow.feng.ast.Position;

public class ObjectTypeDeclarer extends TypeDeclarer {
    private IdentifierTable<TypeDeclarer> entries;

    public ObjectTypeDeclarer(Position pos,
                              IdentifierTable<TypeDeclarer> entries) {
        super(pos);
        this.entries = entries;
    }

    public IdentifierTable<TypeDeclarer> entries() {
        return entries;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ObjectTypeDeclarer t)) return false;
        return entries.equals(t.entries);
    }

}
