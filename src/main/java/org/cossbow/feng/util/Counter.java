package org.cossbow.feng.util;

public class Counter {
    private int value;

    public Counter() {
    }

    public Counter(int value) {
        this.value = value;
    }

    public int dec() {
        return --value;
    }

    public int inc() {
        return ++value;
    }

    public int value() {
        return value;
    }

    public void value(int value) {
        this.value = value;
    }
}
