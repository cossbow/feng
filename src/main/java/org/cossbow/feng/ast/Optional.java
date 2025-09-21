package org.cossbow.feng.ast;

import java.util.NoSuchElementException;

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

    private static final Optional<?> empty = new Optional<>(null);

}
