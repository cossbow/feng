package org.cossbow.feng.ast;

import org.cossbow.feng.ast.oop.ClassMethod;
import org.cossbow.feng.util.Optional;

public record IndexOperator(
        Optional<ClassMethod> get,
        Optional<ClassMethod> set) {
}
