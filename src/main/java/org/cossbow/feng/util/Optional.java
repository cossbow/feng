package org.cossbow.feng.util;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

public class Optional<T> {
    private final T value;

    private Optional(T value) {
        this.value = value;
    }

    public T get() {
        return value;
    }

    public T getOrElse(T defVal) {
        return has() ? value : defVal;
    }

    public boolean has() {
        return value != null;
    }

    public boolean none() {
        return value == null;
    }

    public boolean match(Predicate<T> matcher) {
        return has() && matcher.test(value);
    }

    public void use(Consumer<T> user) {
        if (has()) user.accept(value);
    }

    public T must() {
        if (none()) {
            throw new NoSuchElementException("No value present");
        }
        return value;
    }

    public <R> Optional<R> map(Function<T, R> f) {
        if (none()) return empty();
        return of(f.apply(value));
    }

    public <R> Optional<R> flat(Function<T, Optional<R>> f) {
        if (none()) return empty();
        return f.apply(value);
    }

    //

    public static <T> Optional<T> of(T value) {
        if (value == null) return empty();
        return new Optional<>(value);
    }

    @SuppressWarnings("unchecked")
    public static <T> Optional<T> empty() {
        return (Optional<T>) empty;
    }

    //

    @Override
    public boolean equals(Object o) {
        return o instanceof Optional<?> t &&
                Objects.equals(value, t.value);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(value);
    }

    @Override
    public String toString() {
        if (value == null) return "<>";
        return "<" + value + ">";
    }

    //

    private static final Optional<?> empty = new Optional<>(null);

}
