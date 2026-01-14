package org.cossbow.feng.parser;

import org.cossbow.feng.Pair;
import org.cossbow.feng.ast.oop.EnumDefinition;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;

public class EnumParseTest extends BaseParseTest {

    @Test
    public void testAll() {
        var code = """
                export
                enum TaskStatus {
                    INIT,
                    WAIT =1001,
                    RUN =0x2f,
                    DONE = 0o7,
                    STOP =0b010,
                }
                """;
        var et = (EnumDefinition) doParseType(code, "TaskStatus");
        Assertions.assertEquals(symbol("TaskStatus"), et.symbol());

        var expect = List.of(Pair.<String, Integer>of("INIT", null),
                Pair.of("WAIT", 1001),
                Pair.of("RUN", 0x2f),
                Pair.of("DONE", 7),
                Pair.of("STOP", 2));
        Assertions.assertEquals(expect.size(), et.values().size());
        for (var pair : expect) {
            var v = et.values().get(identifier(pair.a()));
            Assertions.assertEquals(pair.a(), v.name().value());
            if (pair.b() == null) {
                Assertions.assertTrue(v.init().none());
            } else {
                Assertions.assertEquals(BigInteger.valueOf(pair.b()),
                        integer(v.init().get()).value());
            }
        }
    }

}
