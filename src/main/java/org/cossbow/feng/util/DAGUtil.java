package org.cossbow.feng.util;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;

final
public class DAGUtil {
    private DAGUtil() {
    }

    private static class CyclicPath<A> extends ArrayList<A> {
        @Override
        public String toString() {
            if (isEmpty()) return "()";
            var sb = new StringBuilder();
            sb.append('(');
            var first = true;
            for (A a : this) {
                if (first) {
                    first = false;
                } else {
                    sb.append(" -> ");
                }
                sb.append(a);
            }
            sb.append(')');
            return sb.toString();
        }
    }

    private static <A> boolean checkCyclic(
            A master, HashMap<A, Boolean> visited,
            Function<A, Iterable<A>> slaveOf,
            ArrayList<A> path) {
        visited.put(master, Boolean.FALSE);
        path.add(master);
        for (var slave : slaveOf.apply(master)) {
            var stat = visited.get(slave);
            if (Boolean.FALSE == stat) {
                return true;
            }

            if (stat == Boolean.TRUE) {
                continue;
            }
            var r = checkCyclic(slave, visited, slaveOf, path);
            if (r) return true;
        }
        path.removeLast();
        visited.put(master, Boolean.TRUE);
        return false;
    }

    public static <A> List<A> checkCyclic(
            A master, Function<A, Iterable<A>> slaveOf) {
        var set = new HashMap<A, Boolean>();
        var path = new CyclicPath<A>();
        var re = checkCyclic(master, set, slaveOf, path);
        if (!re) return List.of();
        return path;
    }


    public static <A> void bfsVisit(
            A master, Function<A, Iterable<A>> slaveOf,
            BiConsumer<A, A> user) {
        var dedup = new HashSet<A>();
        var queue = new ArrayDeque<Groups.G2<A, A>>();
        queue.addFirst(Groups.g2(null, master));
        while (!queue.isEmpty()) {
            var cur = queue.removeLast();
            user.accept(cur.a(), cur.b());
            for (A slave : slaveOf.apply(cur.b())) {
                if (dedup.add(slave))
                    queue.addFirst(Groups.g2(cur.b(), slave));
            }
        }
    }

}
