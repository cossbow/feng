package org.cossbow.feng.ast;

import org.cossbow.feng.util.ErrorUtil;
import org.cossbow.feng.util.Optional;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

public class UniqueTable<K extends Entity, V> {
    private final HashMap<K, Node<K, V>> table;
    private final List<Node<K, V>> nodes;

    public UniqueTable() {
        table = new HashMap<>();
        nodes = new ArrayList<>();
    }

    public UniqueTable(int initCapacity) {
        table = new HashMap<>(initCapacity);
        nodes = new ArrayList<>(initCapacity);
    }

    public UniqueTable(List<Node<K, V>> nodes) {
        this.nodes = List.copyOf(nodes);
        table = new HashMap<>();
        for (Node<K, V> n : nodes)
            addIndex(n);

    }

    private void addIndex(Node<K, V> node) {
        var old = table.putIfAbsent(node.id, node);
        if (old != null)
            ErrorUtil.duplicate(node.id, old.id);
    }

    public void add(K id, V value) {
        var node = new Node<>(id, value);
        addIndex(node);
        nodes.add(node);
    }

    public V get(K id) {
        var node = table.get(id);
        if (node != null) {
            return node.value;
        }
        throw new NoSuchElementException("not exists '" + id + "'");
    }

    public Optional<V> tryGet(K id) {
        var node = table.get(id);
        if (node != null) {
            return Optional.of(node.value);
        }
        return Optional.empty();
    }

    public K getKey(int i) {
        return nodes.get(i).id;
    }

    public V getValue(int i) {
        return nodes.get(i).value;
    }

    public boolean exists(K id) {
        return table.containsKey(id);
    }

    public List<V> values() {
        return new PhantomList();
    }

    public int size() {
        return nodes.size();
    }

    public boolean isEmpty() {
        return table.isEmpty();
    }

    public void each(BiConsumer<K, V> c) {
        for (var n : nodes)
            c.accept(n.id, n.value);
    }

    public void each(Consumer<V> c) {
        for (var n : nodes)
            c.accept(n.value);
    }

    public List<Node<K, V>> map(Function<V, V> f) {
        return nodes.stream()
                .map(n -> new Node<>(n.id, f.apply(n.value)))
                .toList();
    }

    //

    public record Node<K, V>(K id, V value) {
    }

    class PhantomList extends AbstractList<V> {

        @Override
        public V get(int index) {
            return nodes.get(index).value;
        }

        @Override
        public int size() {
            return nodes.size();
        }
    }
}
