package org.cossbow.feng.ast;

import org.cossbow.feng.util.Optional;

public interface ReadMap<K extends Entity, V> {

    Optional<V> tryGet(K key);

}
