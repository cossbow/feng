package org.cossbow.feng.ast;

import org.cossbow.feng.util.ErrorUtil;
import org.cossbow.feng.util.Optional;

import java.util.*;

public class UniqueTable<K extends Entity, V> {
    private final HashMap<K, Node<K, V>> table;
    private final ArrayList<Node<K, V>> nodes;

    public UniqueTable() {
        table = new HashMap<>();
        nodes = new ArrayList<>();
    }

    public UniqueTable(int initCapacity) {
        table = new HashMap<>(initCapacity);
        nodes = new ArrayList<>(initCapacity);
    }

    public void add(K id, V value) {
        var node = new Node<>(id, value);
        var old = table.putIfAbsent(id, node);
        if (old != null) {
            ErrorUtil.duplicate(id, old.id);
        }
        nodes.add(node);
    }

    public V get(K id) {
        var node = table.get(id);
        if (node != null) {
            return node.value;
        }
        throw new NoSuchElementException("not exists '" + id + "'");
    }

    public org.cossbow.feng.util.Optional<V> tryGet(K id) {
        var node = table.get(id);
        if (node != null) {
            return org.cossbow.feng.util.Optional.of(node.value);
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

    //

    record Node<K, T>(K id, T value) {
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
