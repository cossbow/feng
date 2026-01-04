package org.cossbow.feng.util;

import java.util.AbstractList;
import java.util.List;

public class RepeatList<T> extends AbstractList<T> implements List<T> {
    private final T value;
    private final int size;

    public RepeatList(T value, int size) {
        this.value = value;
        this.size = size;
    }

    @Override
    public T get(int index) {
        return value;
    }

    @Override
    public int size() {
        return size;
    }
}
