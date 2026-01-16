package org.cossbow.feng.util;

import java.util.NoSuchElementException;
import java.util.function.Predicate;

public class Lazy<T> {
    private volatile T value;

    private Lazy(T value) {
        this.value = value;
    }

    public Optional<T> get() {
        return Optional.of(value);
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

    public boolean none() {
        return value == null;
    }

    public boolean has() {
        return value != null;
    }

    public boolean match(Predicate<T> matcher) {
        return has() && matcher.test(value);
    }

    //

    public static <T> Lazy<T> of(T v) {
        return new Lazy<>(v);
    }

    public static <T> Lazy<T> of(Optional<T> o) {
        return new Lazy<>(o.get());
    }

    public static <T> Lazy<T> nil() {
        return new Lazy<>(null);
    }

}
