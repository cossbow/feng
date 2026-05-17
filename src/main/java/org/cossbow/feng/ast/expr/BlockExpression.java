package org.cossbow.feng.ast.expr;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.Scope;
import org.cossbow.feng.ast.dcl.Variable;
import org.cossbow.feng.ast.stmt.Statement;
import org.cossbow.feng.util.Lazy;

import java.util.List;

/**
 * A block expression is enclosed by a pair of curly braces {}
 * and contains an optional series of statements and a final
 * expression.
 * <p>
 * 1. Scope: Variables declared within a block are only valid
 * within that block, and the scope of the block limits the
 * visibility of the variables.
 * <p>
 * 2. Execution order: Statements within a block are executed
 * sequentially, with the final expression being executed last.
 * <p>
 * 3. Type: The type of the block is the type of the final
 * expression.
 *
 * <p>
 * Example:
 * {@code {var i int = 10; i += 1; i};}
 */
public class BlockExpression extends PrimaryExpression
        implements Scope {
    /**
     * The sequence of statements within the block
     */
    private final List<Statement> block;
    /**
     * The final expression
     */
    private Expression result;

    public BlockExpression(Position pos,
                           List<Statement> block,
                           Expression result) {
        super(pos);
        this.block = block;
        this.result = result;
        resultType.set(result.resultType);
    }

    public List<Statement> block() {
        return block;
    }

    public Expression result() {
        return result;
    }

    public void result(Expression result) {
        this.result = result;
    }

    /**
     * The return value of a block statement is
     * also a temporary value
     */
    @Override
    public boolean unbound() {
        return true;
    }

    //

    /**
     * Variables defined in block expressions
     */
    private List<Variable> stack = List.of();
    /**
     * When necessary, the expression will be converted into
     * a block expression, referring to the original expression
     */
    private final Lazy<Expression> origin = Lazy.nil();

    public List<Variable> stack() {
        return stack;
    }

    public void stack(List<Variable> variables) {
        stack = List.copyOf(variables);
    }

    public Lazy<Expression> origin() {
        return origin;
    }

    //

    @Override
    public String toString() {
        return "{ ... ; " + result + '}';
    }
}
