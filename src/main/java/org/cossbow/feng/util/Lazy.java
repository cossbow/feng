package org.cossbow.feng.util;

import java.util.NoSuchElementException;
import java.util.function.Consumer;
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

    public void set(Optional<T> t) {
        this.value = t.get();
    }

    public void set(Lazy<T> l) {
        this.value = l.value;
    }

    public void setIfNone(T value) {
        T v = this.value;
        if (v != null) return;
        this.value = value;
    }

    public boolean none() {
        return value == null;
    }

    public boolean has() {
        return value != null;
    }

    public boolean match(Predicate<T> matcher) {
        var v = value;
        if (v == null) return false;
        return matcher.test(v);
    }

    public void use(Consumer<T> user) {
        var v = value;
        if (v != null) user.accept(v);
    }

    public void use(Consumer<T> user, Runnable or) {
        var v = value;
        if (v != null) user.accept(v);
        else or.run();
    }

    //

    @Override
    public String toString() {
        if (value == null) return "nil";
        return value.toString();
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
