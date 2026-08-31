package org.cossbow.feng.ast;

import org.cossbow.feng.util.Optional;

public interface Aggregatable<F extends Field> {

    Optional<F> field(Identifier name);

    ReadMap<Identifier, F> fields();

}
