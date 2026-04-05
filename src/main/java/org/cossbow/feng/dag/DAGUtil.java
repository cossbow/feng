package org.cossbow.feng.dag;

import org.cossbow.feng.util.Groups;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.function.Function;

import static org.cossbow.feng.util.ErrorUtil.semantic;

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


    public static <Key> DAGGraph<Key> make(
            Collection<Key> nodes,
            Iterable<Groups.G2<Key, Key>> edges) {
        var dag = DAGGraph.make(nodes, edges);
        var c = dag.checkCyclic();
        if (c.isEmpty()) return dag;
        return semantic("cyclic dependence: %s", c);
    }


}
