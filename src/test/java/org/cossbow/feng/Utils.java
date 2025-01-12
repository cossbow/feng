package org.cossbow.feng;

import java.util.*;
import java.util.function.Function;

final
public class Utils {
    private Utils() {
    }

    public static <T, K, V>
    Map<K, V> toMap(Collection<T> collection,
                    Function<T, K> keyMap,
                    Function<T, V> valueMap) {
        Map<K, V> map = new HashMap<>(collection.size());
        for (T t : collection) {
            map.put(keyMap.apply(t), valueMap.apply(t));
        }
        return map;
    }

    public static <T, K>
    Map<K, T> toMap(Collection<T> collection,
                    Function<T, K> keyMap) {
        return toMap(collection, keyMap, Function.identity());
    }

    public static <T, R>
    List<R> listOf(List<T> src, Function<T, R> map) {
        var tgt = new ArrayList<R>(src.size());
        for (var t : src) tgt.add(map.apply(t));
        return tgt;
    }

    public static <T, R>
    Set<R> setOf(Set<T> src, Function<T, R> map) {
        var tgt = new LinkedHashSet<R>(src.size());
        for (var t : src) tgt.add(map.apply(t));
        return tgt;
    }

    @SuppressWarnings("unchecked")
    public static <R, T extends R> List<T> convert(List<R> s) {
        var result = new ArrayList<T>();
        for (R r : s) result.add((T) r);
        return result;
    }
}
