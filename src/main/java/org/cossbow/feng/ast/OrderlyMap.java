package org.cossbow.feng.ast;

import org.cossbow.feng.util.ErrorUtil;
import org.cossbow.feng.util.Groups;
import org.cossbow.feng.util.Optional;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class OrderlyMap<K extends Entity, V> implements Iterable<V> {
    private final HashMap<K, Node<K, V>> index;
    private final List<Node<K, V>> nodes;

    public OrderlyMap() {
        index = new HashMap<>();
        nodes = new ArrayList<>();
    }

    public OrderlyMap(int initCapacity) {
        index = new HashMap<>(initCapacity);
        nodes = new ArrayList<>(initCapacity);
    }

    public OrderlyMap(List<Groups.G2<K, V>> data) {
        index = new HashMap<>(data.size());
        nodes = new ArrayList<>(data.size());
        for (var g : data) add(g.a(), g.b());
    }

    private void addIndex(Node<K, V> node) {
        var old = index.putIfAbsent(node.key, node);
        if (old != null)
            ErrorUtil.duplicate(node.key, old.key);
    }

    public void add(K key, V value) {
        var node = new Node<>(key, value);
        addIndex(node);
        nodes.add(node);
    }

    public void addAll(OrderlyMap<K, V> other) {
        nodes.addAll(other.nodes);
        for (var n : other.nodes) {
            addIndex(n);
        }
    }

    public V get(K key) {
        var node = index.get(key);
        if (node != null) {
            return node.value;
        }
        throw new NoSuchElementException("not exists '" + key + "'");
    }

    public Optional<V> tryGet(K key) {
        var node = index.get(key);
        if (node != null) {
            return Optional.of(node.value);
        }
        return Optional.empty();
    }

    public K getKey(int i) {
        return nodes.get(i).key;
    }

    public V getValue(int i) {
        return nodes.get(i).value;
    }

    public boolean exists(K key) {
        return index.containsKey(key);
    }

    public List<Node<K, V>> nodes() {
        return Collections.unmodifiableList(nodes);
    }

    public Set<K> keys() {
        return index.keySet();
    }

    public List<V> values() {
        return new PhantomValueList();
    }

    public int size() {
        return nodes.size();
    }

    public boolean isEmpty() {
        return index.isEmpty();
    }

    public void each(BiConsumer<K, V> c) {
        for (var n : nodes)
            c.accept(n.key, n.value);
    }

    public void each(Consumer<V> c) {
        for (var n : nodes)
            c.accept(n.value);
    }

    public <R> List<Node<K, R>> map(Function<V, R> f) {
        return nodes.stream()
                .map(n -> new Node<>(n.key, f.apply(n.value)))
                .toList();
    }

    public Stream<V> stream() {
        return StreamSupport.stream(spliterator(), false);
    }

    @Override
    public Iterator<V> iterator() {
        return values().iterator();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof OrderlyMap<?, ?> t)) return false;
        return nodes.equals(t.nodes);
    }

    @Override
    public int hashCode() {
        return nodes.hashCode();
    }

    //

    public record Node<K, V>(K key, V value) {
        @Override
        public String toString() {
            return value.toString();
        }
    }

    class PhantomValueList extends AbstractList<V> {

        @Override
        public V get(int index) {
            return nodes.get(index).value;
        }

        @Override
        public int size() {
            return nodes.size();
        }
    }

    //

    @Override
    public String toString() {
        return values().toString();
    }
}
