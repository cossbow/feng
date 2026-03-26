package org.cossbow.feng.ast.dcl;

import org.cossbow.feng.ast.IdentifierTable;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.util.Lazy;
import org.cossbow.feng.util.Optional;

import java.util.stream.Collectors;

/**
 * 临时，不在AST上
 */
public class ObjectTypeDeclarer extends TypeDeclarer {
    private IdentifierTable<TypeDeclarer> entries;
    private Optional<DerivedTypeDeclarer> dtd;

    public ObjectTypeDeclarer(Position pos,
                              IdentifierTable<TypeDeclarer> entries,
                              Optional<DerivedTypeDeclarer> dtd) {
        super(pos);
        this.entries = entries;
        this.dtd = dtd;
    }

    public IdentifierTable<TypeDeclarer> entries() {
        return entries;
    }

    public Optional<DerivedTypeDeclarer> dtd() {
        return dtd;
    }

    // 左边的类型
    public final Lazy<DerivedTypeDeclarer> lt = Lazy.nil();

    //

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ObjectTypeDeclarer t))
            return false;
        return entries.equals(t.entries);
    }

    @Override
    public int hashCode() {
        return entries.hashCode();
    }

    //

    @Override
    public String toString() {
        return entries.nodes().stream()
                .map(n -> n.key() + "=" + n.value())
                .collect(Collectors.joining(
                        ", ", "{", "}"));
    }
}
