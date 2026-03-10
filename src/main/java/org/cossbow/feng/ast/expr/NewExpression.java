package org.cossbow.feng.ast.expr;

import org.cossbow.feng.util.Optional;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.dcl.NewType;

public class NewExpression extends PrimaryExpression {
    private NewType type;
    private Optional<Expression> arg;

    public NewExpression(Position pos,
                         NewType type,
                         Optional<Expression> arg) {
        super(pos);
        this.type = type;
        this.arg = arg;
    }

    public NewType type() {
        return type;
    }

    public Optional<Expression> arg() {
        return arg;
    }

    @Override
    public boolean unbound() {
        return true;
    }

    //

    @Override
    public String toString() {
        if (arg.none()) return "new(" + type + ")";
        return "new(" + type + ", " + arg.get() + ")";
    }
}
