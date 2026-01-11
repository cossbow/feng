package org.cossbow.feng.util;

import java.util.ArrayList;

public class Stack<E> extends ArrayList<E> {

    public E pop() {
        return remove(size() - 1);
    }

    public void push(E e) {
        add(e);
    }

    public E peek() {
        return getLast();
    }

}
