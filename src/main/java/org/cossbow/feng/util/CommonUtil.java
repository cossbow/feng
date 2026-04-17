package org.cossbow.feng.util;

import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.Position;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;

public class CommonUtil {

    public static String rand() {
        var rand = ThreadLocalRandom.current();
        var buf = new byte[16];
        rand.nextBytes(buf);
        return HexFormat.of().formatHex(buf);
    }

    public static Identifier rand(String prefix) {
        var h = rand();
        return new Identifier(Position.ZERO,
                "$" + prefix + "_" + h, true);
    }

    public static String upperFirst(String s) {
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }

    public static String trimExt(String fn) {
        var i = fn.lastIndexOf(".");
        if (i < 0) return fn;
        return fn.substring(0, i);
    }

    public static byte[] concat(byte[] first, byte[]... more) {
        var len = first.length;
        for (byte[] m : more) len += m.length;

        var buf = new byte[len];
        System.arraycopy(first, 0, buf, 0, first.length);
        var offset = first.length;
        for (byte[] m : more) {
            System.arraycopy(m, 0, buf, offset, m.length);
            offset += m.length;
        }
        return buf;
    }

    public static <T>
    List<T> concat(List<T> a, List<T> b) {
        var buf = new ArrayList<T>(a.size() + b.size());
        buf.addAll(a);
        buf.addAll(b);
        return buf;
    }

    @SuppressWarnings("unchecked")
    public static <T> List<T> list(T... a) {
        var l = new ArrayList<T>(a.length);
        for (T t : a) l.add(t);
        return l;
    }

    public static <T> Optional<Groups.G2<T, T>>
    diff(List<T> la, List<T> lb) {
        var sa = la.size();
        if (sa != lb.size())
            throw new IllegalArgumentException(
                    "required same size of tow lists");
        if (sa == 0) return Optional.empty();
        for (int i = 0; i < sa; i++) {
            var va = la.get(i);
            var vb = lb.get(i);
            if (la.get(i).equals(lb.get(i)))
                continue;
            return Optional.of(Groups.g2(va, vb));
        }
        return Optional.empty();
    }

    public static <K, V> Map<K, V> map(K k, V v) {
        var m = new HashMap<K, V>(1);
        m.put(k, v);
        return m;
    }

    public static <K, V> Map<K, V> map(K k1, V v1, K k2, V v2) {
        var m = new HashMap<K, V>(1);
        m.put(k1, v1);
        m.put(k2, v2);
        return m;
    }

    public static <T, K> Map<K, T> toMap(List<T> l, Function<T, K> kf) {
        var r = new LinkedHashMap<K, T>(l.size());
        for (T t : l) {
            r.put(kf.apply(t), t);
        }
        return r;
    }

    public static <Key> Set<Key>
    subtract(Collection<Key> minuend, Collection<Key> subtraction) {
        var difference = new HashSet<Key>(
                minuend.size() - subtraction.size());
        var sub = Set.copyOf(subtraction);
        for (Key k : minuend) {
            if (sub.contains(k)) continue;
            difference.add(k);
        }
        return difference;
    }

    public static <K, V> Map<K, V>
    extract(Map<K, V> src, Collection<K> keys) {
        var result = new HashMap<K, V>(keys.size());
        for (K k : keys) result.put(k, src.get(k));
        return result;
    }

    public static <K, V> Map<K, V> merge(Map<K, V> a, Map<K, V> b) {
        var r = new HashMap<K, V>(a.size(), b.size());
        r.putAll(a);
        r.putAll(b);
        return r;
    }

    //

    public static <T> T required(T t) {
        if (t != null) return t;
        throw new NullPointerException();
    }
}
