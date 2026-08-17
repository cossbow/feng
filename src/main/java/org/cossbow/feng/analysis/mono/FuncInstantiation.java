package org.cossbow.feng.analysis.mono;

import org.cossbow.feng.ast.proc.FunctionDefinition;
import org.cossbow.feng.ast.gen.TypeArguments;

/**
 * Records a concrete instantiation of a generic function:
 * a FunctionDefinition with its concrete TypeArguments.
 */
public record FuncInstantiation(FunctionDefinition fd, TypeArguments args) {
    public boolean hasTypeVar() {
        return args.hasTypeVar();
    }
}
