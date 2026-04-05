package org.cossbow.feng.dag;

import org.cossbow.feng.util.Counter;
import org.cossbow.feng.util.Groups;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.cossbow.feng.util.CommonUtil.subtract;


/**
 * <h3>有向无环图</h3>
 * <div>设计为不可变类型，创建时构建好图并检查错误</div>
 * <div>2021-12-14</div>
 *
 * @author jiangjianjun5
 */
final
public class DAGGraph<Key>
        implements Iterable<Key> {

    private final Set<Key> all;

    private final Set<Key> heads, tails;

    private final Map<Key, Set<Key>> forward;
    private final Map<Key, Set<Key>> reverse;

    public DAGGraph(Set<Key> all,
                    Map<Key, Set<Key>> forward,
                    Map<Key, Set<Key>> reverse) {
        this.all = all;
        this.forward = Map.copyOf(forward);
        this.reverse = Map.copyOf(reverse);
        this.tails = Set.copyOf(subtract(all, this.forward.keySet()));
        this.heads = Set.copyOf(subtract(all, this.reverse.keySet()));
    }

    private DAGGraph() {
        this.all = Set.of();
        this.heads = Set.of();
        this.tails = Set.of();
        this.forward = Map.of();
        this.reverse = Map.of();
    }

    private static final DAGGraph<?> EMPTY = new DAGGraph<>();

    @SuppressWarnings("unchecked")
    public static <Key> DAGGraph<Key> empty() {
        return (DAGGraph<Key>) EMPTY;
    }

    public static <Key> DAGGraph<Key> make(Collection<Key> nodes,
                                           Iterable<Groups.G2<Key, Key>> edges) {
        if (null == nodes || nodes.isEmpty()) {
            throw new IllegalArgumentException("keys empty");
        }
        var all = Set.copyOf(nodes);
        if (all.size() != nodes.size()) {
            throw new IllegalArgumentException("Has duplicate Key");
        }

        var keyMap = new HashMap<Key, Key>(all.size());
        for (Key k : all) {
            keyMap.put(k, k);
        }

        var forward = new HashMap<Key, Set<Key>>();
        var reverse = new HashMap<Key, Set<Key>>();
        for (var edge : edges) {
            Key from = keyMap.get(edge.a()), to = keyMap.get(edge.b());
            if (from == null) {
                throw new IllegalArgumentException("Key not exists: " + edge.a());
            }
            if (to == null) {
                throw new IllegalArgumentException("Key not exists: " + edge.b());
            }

            forward.computeIfAbsent(from, hashSet()).add(to);
            reverse.computeIfAbsent(to, hashSet()).add(from);
        }
        return new DAGGraph<>(all, forward, reverse);
    }

    //

    public boolean isEmpty() {
        return all.isEmpty();
    }

    public int size() {
        return all.size();
    }

    public Iterator<Key> iterator() {
        return all.iterator();
    }

    public Stream<Key> stream() {
        return all.stream();
    }

    public Set<Key> all() {
        return all;
    }

    public Set<Key> heads() {
        return heads;
    }

    public Set<Key> tails() {
        return tails;
    }

    public Set<Key> prev(Key key) {
        return reverse.getOrDefault(key, Set.of());
    }

    public Set<Key> next(Key key) {
        return forward.getOrDefault(key, Set.of());
    }

    //

    public DAGGraph<Key> acyclic() {
        var c = checkCyclic();
        if (c.isEmpty()) return this;
        throw new IllegalArgumentException("cyclic graph: " + c);
    }

    public List<Key> checkCyclic() {
        for (Key key : all) {
            var cyc = DAGUtil.checkCyclic(key, this::next);
            if (!cyc.isEmpty())
                return cyc;
        }
        return List.of();
    }

    private void useKey(Key key, Consumer<Key> user) {
        var deps = prev(key);
        for (Key d : deps)
            useKey(d, user);
        user.accept(key);
    }

    private List<Key> linear() {
        var list = new ArrayList<Key>(all.size());
        var set = new HashSet<Key>(all.size());
        for (Key key : tails) {
            useKey(key, k -> {
                if (set.add(k)) list.add(k);
            });
        }
        return list;
    }

    public void visit(BiConsumer<Key, Set<Key>> user) {
        for (Key key : linear()) {
            user.accept(key, prev(key));
        }
    }

    public void bfs(Consumer<Key> user) {
        for (Key key : linear()) {
            user.accept(key);
        }
    }

    //
    // static function

    //

    static <Key> Function<Key, Set<Key>> hashSet() {
        return k -> new HashSet<>();
    }

    public static <Key> boolean checkAcyclic(
            Collection<Key> keys,
            Map<Key, Set<Key>> forward,
            Map<Key, Set<Key>> reverse) {
        var zeroInDegree = new ArrayDeque<Key>(keys.size());
        var hasInDegree = new HashMap<Key, Counter>();
        for (Key key : keys) {
            var prev = reverse.get(key);
            int size = sizeOf(prev);
            if (size == 0) {
                zeroInDegree.add(key);
            } else {
                hasInDegree.put(key, new Counter(size));
            }
        }
        if (zeroInDegree.isEmpty()) {
            return false;
        }

        while (!zeroInDegree.isEmpty()) {
            Key key = zeroInDegree.poll();

            var next = forward.get(key);
            if (sizeOf(next) > 0) {
                for (Key nk : next) {
                    if (hasInDegree.get(nk).dec() == 0) {
                        zeroInDegree.add(nk);
                        hasInDegree.remove(nk);
                    }
                }
            }
        }

        return hasInDegree.isEmpty();
    }

    private static int sizeOf(Collection<?> c) {
        return c != null ? c.size() : 0;
    }

}
