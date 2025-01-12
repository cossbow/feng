package org.cossbow.feng.ast.stmt;

import org.cossbow.feng.ast.Position;

import java.util.Optional;

public class ReturnStatement extends Statement {
    private final Optional<Tuple> result;

    public ReturnStatement(Position pos,
                           Optional<Tuple> result) {
        super(pos);
        this.result = result;
    }

    public Optional<Tuple> result() {
        return result;
    }
}
