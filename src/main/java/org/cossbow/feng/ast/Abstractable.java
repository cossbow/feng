package org.cossbow.feng.ast;

import org.cossbow.feng.util.Optional;

public interface Abstractable<M extends Method> {

    Optional<M> method(Identifier name);

    ReadMap<Identifier, M> methods();

}
