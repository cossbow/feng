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
        var et = (EnumDefinition) doParseDefinition(code);
        Assertions.assertEquals(identifier("TaskStatus"), et.name().orElseThrow());

        var expect = List.of(Pair.<String, Integer>of("INIT", null),
                Pair.of("WAIT", 1001),
                Pair.of("RUN", 0x2f),
                Pair.of("DONE", 7),
                Pair.of("STOP", 2));
        Assertions.assertEquals(expect.size(), et.values().size());
        for (int i = 0; i < expect.size(); i++) {
            var p = expect.get(i);
            var v = et.values().get(i);
            Assertions.assertEquals(p.a(), v.name().value());
            if (p.b() == null) {
                Assertions.assertTrue(v.init().isEmpty());
            } else {
                Assertions.assertEquals(BigInteger.valueOf(p.b()), integer(v.init().get()).value());
            }
        }
    }

}
