package org.cossbow.feng.ast.expr;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.dcl.TypeDeclarer;
import org.cossbow.feng.ast.gen.GenericMap;

/**
 * Memory size occupied by the acquisition type.
 * <p>
 * {@code var s = sizeof(int);},
 * {@code var s = sizeof(Block);},
 * etc.
 */
public class SizeofExpression extends PrimaryExpression {
    private final TypeDeclarer type;

    public SizeofExpression(Position pos,
                            TypeDeclarer type) {
        super(pos);
        this.type = type;
    }

    public TypeDeclarer type() {
        return type;
    }

    //

    private long size = -1;

    public void size(long size) {
        this.size = size;
    }

    public long size() {
        return size;
    }

    @Override
    public SizeofExpression mirror() {
        var n = new SizeofExpression(pos(), type);
        n.size = -1;
        return n;
    }

    @Override
    public SizeofExpression mono(GenericMap gm) {
        var n = new SizeofExpression(pos(), gm.mapIf(type));
        n.size = -1;
        return monoCopy(n, gm);
    }

    //

    @Override
    public String toString() {
        return "sizeof(" + type + ")";
    }
}
