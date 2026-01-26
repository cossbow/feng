package org.cossbow.feng.util;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

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

    public void use(Consumer<T> present, Runnable absent) {
        if (has()) present.accept(value);
        else absent.run();
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

    public Stream<T> stream() {
        return has() ? Stream.of(value) : Stream.empty();
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
        return value != null ? value.hashCode() : 0;
    }

    @Override
    public String toString() {
        if (value == null) return "nil";
        return value.toString();
    }

    //

    private static final Optional<?> empty = new Optional<>(null);

}
