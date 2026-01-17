package org.cossbow.feng.ast.dcl;

import org.cossbow.feng.ast.Position;

import java.util.List;
import java.util.Objects;

/**
 * 非用于创建AST，仅用于语义分析
 */
public class TupleTypeDeclarer extends TypeDeclarer {
    private List<TypeDeclarer> tuple;

    public TupleTypeDeclarer(Position pos,
                             List<TypeDeclarer> tuple) {
        super(pos);
        this.tuple = tuple;
    }

    public List<TypeDeclarer> tuple() {
        return tuple;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof TupleTypeDeclarer t))
            return false;
        return tuple.equals(t.tuple);
    }

}
