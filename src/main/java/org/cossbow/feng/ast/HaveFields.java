package org.cossbow.feng.ast;

public interface HaveFields<F extends Field> {

    IdentifierTable<F> fields();

}
