package org.cossbow.feng.ast.proc;

import org.cossbow.feng.ast.Entity;
import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.Scope;
import org.cossbow.feng.ast.dcl.Variable;
import org.cossbow.feng.ast.stmt.BlockStatement;
import org.cossbow.feng.ast.stmt.LabeledStatement;

import java.util.List;
import java.util.Map;

/**
 * This is the executable body of function and class-method.
 */
public class Procedure extends Entity implements Scope {
    /**
     * This prototype is the same as the prototypes
     * of functions and methods
     */
    private final Prototype prototype;
    /**
     * The executable body
     */
    private final BlockStatement body;
    /**
     * The labels defined in body of procedure
     */
    private final Map<Identifier, LabeledStatement> labels;

    public Procedure(Position pos,
                     Prototype prototype,
                     BlockStatement body,
                     Map<Identifier, LabeledStatement> labels) {
        super(pos);
        this.prototype = prototype;
        this.body = body;
        this.labels = labels;
    }

    public Prototype prototype() {
        return prototype;
    }

    public BlockStatement body() {
        return body;
    }

    public Map<Identifier, LabeledStatement> labels() {
        return labels;
    }


    //

    /**
     * Defined variables in this procedure, including parameters
     */
    private volatile List<Variable> stack = List.of();

    public List<Variable> stack() {
        return stack;
    }

    public void stack(List<Variable> variables) {
        stack = variables;
    }


    //
    @Override
    public String toString() {
        return prototype.toString();
    }
}
