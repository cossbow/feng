package org.cossbow.feng.util;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Objects;

import java.util.Set;

public class DAGUtilTest {
    static class A {
        final int id;
        Set<A> slaves = new HashSet<>();

        A(int id) {
            this.id = id;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof A a)) return false;
            return id == a.id;
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(id);
        }

        @Override
        public String toString() {
            return "" + id;
        }
    }

    static Optional<A> checkA(A master) {
        return DAGUtil.checkCyclic(master, a -> a.slaves);
    }

    @Test
    public void testCheck() {
        A a1 = new A(1);
        A a2 = new A(2);
        A a3 = new A(3);
        A a4 = new A(4);

        a1.slaves.add(a2);
        a1.slaves.add(a3);
        a2.slaves.add(a4);
        a3.slaves.add(a4);
        var r = checkA(a1);
        Assertions.assertTrue(r.none());
        System.out.println("-----------------");

        a4.slaves.add(a1);
        r = checkA(a1);
        Assertions.assertTrue(r.has());
        System.out.println("-----------------");

        a4.slaves.remove(a1);
        a3.slaves.add(a1);
        r = checkA(a1);
        Assertions.assertTrue(r.has());
        System.out.println("-----------------");
    }

}
