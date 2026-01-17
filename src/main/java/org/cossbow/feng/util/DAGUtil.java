package org.cossbow.feng.util;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

final
public class DAGUtil {
    private DAGUtil() {
    }

    private static <A> Optional<A> checkCyclic(
            A master, HashMap<A, Boolean> visited,
            Function<A, Iterable<A>> slaveOf) {
        visited.put(master, Boolean.FALSE);
        for (var slave : slaveOf.apply(master)) {
            var stat = visited.get(slave);

            if (Boolean.FALSE == stat)
                return Optional.of(slave);

            if (stat == Boolean.TRUE)
                continue;

            var r = checkCyclic(slave, visited, slaveOf);
            if (r.has()) return r;
        }
        visited.put(master, Boolean.TRUE);
        return Optional.empty();
    }

    public static <A> Optional<A> checkCyclic(
            A master, Function<A, Iterable<A>> slaveOf) {
        var set = new HashMap<A, Boolean>();
        return checkCyclic(master, set, slaveOf);
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
