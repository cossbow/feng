package org.cossbow.feng.util;

import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.Position;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

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
                "Feng$" + prefix + "_" + h, true);
    }

    public static String upperFirst(String s) {
        return s.substring(0, 1).toUpperCase() + s.substring(1);
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

    //

    public static <T> T required(T t) {
        if (t != null) return t;
        throw new NullPointerException();
    }
}
