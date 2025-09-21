package org.cossbow.feng.ast;

import java.util.Objects;

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

    public T get() {
        return Objects.requireNonNull(value, "value is null");
    }

    public void set(T value) {
        this.value = value;
    }

    public boolean isNil() {
        return value == null;
    }

}
