package org.cossbow.feng.util;

final
public class Groups {
    private Groups() {
    }

    //

    public record G2<A, B>(A a, B b) {
    }

    public static <A, B> G2<A, B> g2(A a, B b) {
        return new G2<>(a, b);
    }

    //

    public record G3<A, B, C>(A a, B b, C c) {
    }

    public static <A, B, C> G3<A, B, C> g3(A a, B b, C c) {
        return new G3<>(a, b, c);
    }

    //


}
