package org.cossbow.feng.ast.expr;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.dcl.ArrayTypeDeclarer;
import org.cossbow.feng.ast.dcl.TypeDeclarer;
import org.cossbow.feng.util.Lazy;
import org.cossbow.feng.util.Optional;

import java.util.List;

public class ArrayExpression extends PrimaryExpression {
    private List<Expression> elements;
    private final Optional<ArrayTypeDeclarer> type;

    public ArrayExpression(Position pos,
                           List<Expression> elements,
                           Optional<ArrayTypeDeclarer> type) {
        super(pos);
        this.elements = elements;
        this.type = type;
    }

    public ArrayExpression(Position pos,
                           List<Expression> elements) {
        this(pos, elements, Optional.empty());
    }

    public List<Expression> elements() {
        return elements;
    }

    public void elements(List<Expression> elements) {
        this.elements = elements;
    }

    public Optional<ArrayTypeDeclarer> type() {
        return type;
    }

    public int size() {
        return elements.size();
    }

    public boolean isEmpty() {
        return elements.isEmpty();
    }

    public Expression get(int i) {
        return elements.get(i);
    }

    @Override
    public boolean unbound() {
        return true;
    }

    public final Lazy<TypeDeclarer> lt = Lazy.nil();

    //

    @Override
    public String toString() {
        return elements.toString();
    }
}
