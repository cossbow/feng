package org.cossbow.feng.ast.expr;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.gen.GenericMap;
import org.cossbow.feng.ast.proc.Procedure;

/**
 * Anonymous function definition, written as {@code func(){...} }.
 * <p>
 * A function lambda is a pure function pointer: it cannot capture outer
 * local variables, and its target type must be a function type. It is
 * synthesized into a named function during semantic analysis and emitted
 * through the existing function pipeline.
 */
public class FuncLambdaExpression extends PrimaryExpression {
    private final Procedure procedure;

    public FuncLambdaExpression(Position pos,
                                Procedure procedure) {
        super(pos);
        this.procedure = procedure;
    }

    public Procedure procedure() {
        return procedure;
    }

    @Override
    public FuncLambdaExpression mirror() {
        return this;
    }

    @Override
    public FuncLambdaExpression mono(GenericMap gm) {
        return this;
    }

}