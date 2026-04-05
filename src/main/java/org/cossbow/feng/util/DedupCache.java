package org.cossbow.feng.util;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DedupCache<K> extends LinkedHashMap<K, K> {

    public DedupCache() {
    }

    public DedupCache(int initialCapacity) {
        super(initialCapacity);
    }

    public DedupCache(Map<K, K> m) {
        super(m);
    }

    public K dedup(K key) {
        var old = putIfAbsent(key, key);
        return old != null ? old : key;
    }

    public List<K> toList() {
        return List.copyOf(values());
    }

}
