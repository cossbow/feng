package org.cossbow.feng.ast.expr;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.lit.*;
import org.cossbow.feng.util.Optional;

public class LiteralExpression extends PrimaryExpression {
    private final Literal literal;

    public LiteralExpression(Position pos, Literal literal) {
        super(pos);
        this.literal = literal;
    }

    public Literal literal() {
        return literal;
    }

    @Override
    public boolean isFinal() {
        return true;
    }

    public Optional<IntegerLiteral> asInteger() {
        if (literal instanceof IntegerLiteral il)
            return Optional.of(il);
        return Optional.empty();
    }

    public Optional<FloatLiteral> asFloat() {
        if (literal instanceof FloatLiteral fl)
            return Optional.of(fl);
        return Optional.empty();
    }

    public Optional<BoolLiteral> asBool() {
        if (literal instanceof BoolLiteral bl)
            return Optional.of(bl);
        return Optional.empty();
    }

    public Optional<NilLiteral> asNil() {
        if (literal instanceof NilLiteral nl)
            return Optional.of(nl);
        return Optional.empty();
    }

    public Optional<StringLiteral> asString() {
        if (literal instanceof StringLiteral sl)
            return Optional.of(sl);
        return Optional.empty();
    }

    @Override
    public String toString() {
        return literal.toString();
    }

}
