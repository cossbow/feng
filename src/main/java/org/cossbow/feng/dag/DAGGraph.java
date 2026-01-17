package org.cossbow.feng.dag;

import org.cossbow.feng.util.Groups;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;


/**
 * <h3>有向无环图</h3>
 * <div>设计为不可变类型，创建时构建好图并检查错误</div>
 * <div>2021-12-14</div>
 *
 * @author jiangjianjun5
 */
final
public class DAGGraph<Key> {

    private final Set<Key> all;

    private final Set<Key> heads, tails;

    private final Map<Key, Set<Key>> forwardIndex;
    private final Map<Key, Set<Key>> reverseIndex;

    public DAGGraph(Collection<Key> all,
                    Iterable<Groups.G2<Key, Key>> edges) {
        if (null == all || all.isEmpty()) {
            throw new IllegalArgumentException("keys empty");
        }
        this.all = Set.copyOf(all);
        if (this.all.size() != all.size()) {
            throw new IllegalArgumentException("Has duplicate Key");
        }

        var keyMap = new HashMap<Key, Key>(this.all.size());
        for (Key k : this.all) {
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
        if (!checkAcyclic(this.all, forward, reverse)) {
            throw new IllegalArgumentException("Serious error: graph has cycle！");
        }

        this.forwardIndex = Map.copyOf(forward);
        this.reverseIndex = Map.copyOf(reverse);
        this.tails = Set.copyOf(subtract(this.all, this.forwardIndex.keySet()));
        this.heads = Set.copyOf(subtract(this.all, this.reverseIndex.keySet()));
    }


    //

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
        return reverseIndex.getOrDefault(key, Set.of());
    }

    public Set<Key> next(Key key) {
        return forwardIndex.getOrDefault(key, Set.of());
    }

    //

    public void bfs(Consumer<Key> consumer) {
        var traveled = new HashMap<Key, Boolean>(all.size());
        var heads = subtract(all, reverseIndex.keySet());
        var queue = new ArrayDeque<>(heads);
        for (Key head : heads) {
            traveled.put(head, Boolean.FALSE);
        }
        while (!queue.isEmpty()) {
            var ck = queue.pollLast();
            consumer.accept(ck);
            var next = forwardIndex.get(ck);
            if (null != next && !next.isEmpty()) {
                // 发现后继
                for (Key nk : next) {
                    if (traveled.putIfAbsent(nk, Boolean.FALSE) == null) {
                        queue.addFirst(nk);
                    }
                }
            }
            traveled.put(ck, Boolean.TRUE);
        }
    }

    //
    // static function

    //

    static <Key> Function<Key, Set<Key>> hashSet() {
        return k -> new HashSet<>();
    }

    static <Key> Set<Key> subtract(Set<Key> minuend, Set<Key> subtraction) {
        var difference = new HashSet<Key>(minuend.size() - subtraction.size());
        for (Key k : minuend) {
            if (subtraction.contains(k)) continue;
            difference.add(k);
        }
        return difference;
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

    static class Counter {
        private transient int value;

        public Counter(int value) {
            this.value = value;
        }

        public int dec() {
            return --value;
        }

        public int inc() {
            return ++value;
        }
    }
}
