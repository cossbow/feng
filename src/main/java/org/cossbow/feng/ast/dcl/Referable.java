package org.cossbow.feng.ast.dcl;

import org.cossbow.feng.util.Optional;

public interface Referable {

    Optional<Refer> refer();

    default boolean isKind(ReferKind kind) {
        return refer().match(r -> r.isKind(kind));
    }

}
