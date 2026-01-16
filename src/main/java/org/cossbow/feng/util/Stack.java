package org.cossbow.feng.util;

import java.util.ArrayList;

public class Stack<E> extends ArrayList<E> {

    public synchronized E pop() {
        return remove(size() - 1);
    }

    public synchronized void push(E e) {
        add(e);
    }

    public synchronized E peek() {
        return getLast();
    }

}
