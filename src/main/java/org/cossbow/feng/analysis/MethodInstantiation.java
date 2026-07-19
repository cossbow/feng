package org.cossbow.feng.analysis;

import org.cossbow.feng.ast.gen.DerivedType;
import org.cossbow.feng.ast.oop.ClassMethod;
import org.cossbow.feng.ast.gen.TypeArguments;

/**
 * Records a concrete instantiation of a method-level generic:
 * the class DerivedType, the method, and its concrete type arguments.
 */
public record MethodInstantiation(DerivedType classDt, ClassMethod method, TypeArguments methodArgs) {
    public boolean hasTypeVar() {
        return methodArgs.hasTypeVar();
    }
}
