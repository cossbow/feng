package org.cossbow.feng.ast;

import org.cossbow.feng.util.Optional;

import java.util.List;
import java.util.Set;

public interface ReadMap<K extends Entity, V>
        extends Iterable<V> {

    Optional<V> tryGet(K key);

    boolean exists(K key);

    int size();

    boolean isEmpty();

    Set<K> keys();

    List<V> values();

}
