package org.cossbow.feng.ast;

import java.util.NoSuchElementException;

public class Lazy<T> {
    private T value;

    private Lazy(T value) {
        this.value = value;
    }

    public static <T> Lazy<T> of(T v) {
        return new Lazy<>(v);
    }

    public static <T> Lazy<T> of(Optional<T> o) {
        return new Lazy<>(o.get());
    }

    public static <T> Lazy<T> nil() {
        return new Lazy<>(null);
    }

    public T must() {
        if (value == null) {
            throw new NoSuchElementException("No value present");
        }
        return value;
    }

    public void set(T value) {
        this.value = value;
    }

    public boolean isNil() {
        return value == null;
    }

}
