package org.cossbow.feng.util;

import java.util.ArrayList;
import java.util.Iterator;

public class Stack<E> extends ArrayList<E> implements Iterable<E> {

    public synchronized E pop() {
        return remove(size() - 1);
    }

    public synchronized void push(E e) {
        add(e);
    }

    public synchronized E peek() {
        return getLast();
    }

    public Iterator<E> iterator() {
        return new Iterator<>() {
            private int cursor = size();

            @Override
            public boolean hasNext() {
                cursor--;
                return cursor >= 0;
            }

            @Override
            public E next() {
                return get(cursor);
            }
        };
    }

}
