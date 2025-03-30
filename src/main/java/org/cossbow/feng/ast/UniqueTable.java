package org.cossbow.feng.ast;

import org.cossbow.feng.parser.SyntaxException;

import java.util.*;

public class UniqueTable<T> {
    private final HashMap<Identifier, Node<T>> table;
    private final ArrayList<Node<T>> nodes;

    public UniqueTable() {
        table = new HashMap<>();
        nodes = new ArrayList<>();
    }

    public UniqueTable(int initCapacity) {
        table = new HashMap<>(initCapacity);
        nodes = new ArrayList<>(initCapacity);
    }

    public void add(Identifier id, T value) {
        var node = new Node<>(id, value);
        var old = table.putIfAbsent(id, node);
        if (old != null) {
            throw new SyntaxException("parse duplicate '%s' at %s, prev at %s"
                    .formatted(id, id.pos(), old.id.pos()));
        }
        nodes.add(node);
    }

    public T get(Identifier id) {
        var node = table.get(id);
        if (node != null) {
            return node.value;
        }
        throw new NoSuchElementException("not exists '" + id + "'");
    }

    public Identifier getId(int i) {
        return nodes.get(i).id;
    }

    public T getValue(int i) {
        return nodes.get(i).value;
    }

    public boolean exists(Identifier id) {
        return table.containsKey(id);
    }

    public List<T> values() {
        return new PhantomList();
    }

    public int size() {
        return nodes.size();
    }

    public boolean isEmpty() {
        return table.isEmpty();
    }

    public record Node<T>(Identifier id, T value) {
    }

    class PhantomList extends AbstractList<T> {

        @Override
        public T get(int index) {
            return nodes.get(index).value;
        }

        @Override
        public int size() {
            return nodes.size();
        }
    }
}
