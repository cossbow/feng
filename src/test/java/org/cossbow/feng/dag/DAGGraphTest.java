package org.cossbow.feng.dag;


import org.cossbow.feng.util.Groups;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.Collectors;

public class DAGGraphTest {

    @Test
    public void testLegal() {
        var nodes = Set.of(1, 2, 3, 4);
        var edges = List.of(
                Groups.g2(1, 2),
                Groups.g2(1, 3),
                Groups.g2(2, 4),
                Groups.g2(3, 4)
        );
        DAGGraph.make(nodes, edges).acyclic();
    }

    @Test
    public void testIllegal1() {
        var nodes = Set.of(1, 2, 3, 4);
        var edges = List.of(
                Groups.g2(1, 2),
                Groups.g2(2, 3),
                Groups.g2(3, 4),
                Groups.g2(4, 1)
        );
        try {
            DAGGraph.make(nodes, edges).acyclic();
            Assertions.fail("The graph is illegal");
        } catch (IllegalArgumentException e) {
            System.out.println(e.getMessage());
        }
    }

    @Test
    public void testIllegal2() {
        var nodes = new HashSet<Integer>();
        for (int i = 1; i <= 6; i++) {
            nodes.add(i);
        }
        var edges = List.of(
                Groups.g2(1, 2),
                Groups.g2(1, 3),
                Groups.g2(2, 4),
                Groups.g2(3, 4),
                Groups.g2(3, 5),
                Groups.g2(5, 6),
                Groups.g2(6, 1)
        );

        try {
            DAGGraph.make(nodes, edges).acyclic();
            Assertions.fail("It's a cyclic graph");
        } catch (IllegalArgumentException e) {
            System.out.println(e.getMessage());
        }

    }

    @Test
    public void testKeys() {
        var nodes = Set.of(1, 2, 3, 4, 5, 6);
        var edges = List.of(
                Groups.g2(1, 2),
                Groups.g2(1, 3),
                Groups.g2(2, 4),
                Groups.g2(3, 4),
                Groups.g2(1, 5),
                Groups.g2(3, 5),
                Groups.g2(6, 5)
        );
        var graph = DAGGraph.make(nodes, edges).acyclic();

        Assertions.assertEquals(Set.of(1, 6), graph.heads());
        Assertions.assertEquals(Set.of(4, 5), graph.tails());
    }

    @Test
    public void testDependency() {
        var nodes = List.of(1, 2, 3, 4);
        var edges = List.of(
                Groups.g2(1, 2),
                Groups.g2(1, 3),
                Groups.g2(2, 4),
                Groups.g2(3, 4)
        );
        var r = DAGGraph.make(nodes, edges).acyclic();
        Assertions.assertEquals(Set.copyOf(nodes), r.all());

        Assertions.assertEquals(Set.of(1), r.heads());
        Assertions.assertEquals(Set.of(2, 3), r.next(1));
        Assertions.assertEquals(Set.of(4), r.next(2));
        Assertions.assertEquals(Set.of(4), r.next(3));
        Assertions.assertTrue(r.next(4).isEmpty());

        Assertions.assertEquals(Set.of(4), r.tails());
        Assertions.assertEquals(Set.of(2, 3), r.prev(4));
        Assertions.assertEquals(Set.of(1), r.prev(2));
        Assertions.assertEquals(Set.of(1), r.prev(3));
        Assertions.assertTrue(r.prev(1).isEmpty());
    }


    @Test
    public void testBFS() {
        var nodes = Set.of(1, 2, 3, 4, 5, 6);
        var edges = List.of(
                Groups.g2(1, 2),
                Groups.g2(1, 3),
                Groups.g2(2, 4),
                Groups.g2(2, 5),
                Groups.g2(3, 4),
                Groups.g2(5, 6)
        );
        DAGGraph.make(nodes, edges).acyclic().bfs(System.out::println);
    }

    @Test
    public void testRand() {
        var size = ThreadLocalRandom.current().nextInt(10, 1000);
        var r = randDAG(size);
        r.bfs(System.out::println);
    }

    //

    static DAGGraph<Integer> randDAG(int size) {
        return randDAG(size, Function.identity());
    }

    static <Key> DAGGraph<Key> randDAG(int size, Function<Integer, Key> mapper) {
        if (size <= 0) throw new IllegalArgumentException();

        var rand = ThreadLocalRandom.current();
        var nodes = rand
                .ints(1, size * 10)
                .boxed().distinct().limit(size).sorted().map(mapper).collect(Collectors.toList());
        var edges = new ArrayList<Groups.G2<Key, Key>>();
        for (int i = 0; i < size; i++) {
            for (int j = i + 1; j < size; j++) {
                if (rand.nextInt() % 2 == 0) {
                    edges.add(Groups.g2(nodes.get(i), nodes.get(j)));
                }
            }
        }

        return DAGGraph.make(nodes, edges).acyclic();
    }

}
