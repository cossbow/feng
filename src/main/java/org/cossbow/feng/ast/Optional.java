package org.cossbow.feng.ast;

import java.util.NoSuchElementException;
import java.util.Objects;

public class Optional<T> {
    private final T value;

    private Optional(T value) {
        this.value = value;
    }

    public T get() {
        return value;
    }

    public boolean has() {
        return value != null;
    }

    public boolean none() {
        return value == null;
    }

    public boolean is(T v) {
        return value == v;
    }

    public T must() {
        if (value == null) {
            throw new NoSuchElementException("No value present");
        }
        return value;
    }

    public static <T> Optional<T> of(T value) {
        return new Optional<>(value);
    }

    @SuppressWarnings("unchecked")
    public static <T> Optional<T> empty() {
        return (Optional<T>) empty;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Optional<?> optional)) return false;
        return Objects.equals(value, optional.value);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(value);
    }

    @Override
    public String toString() {
        if (value == null) return "[]";
        return "[" + value + "]";
    }

    //

    private static final Optional<?> empty = new Optional<>(null);

}
