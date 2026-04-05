package org.cossbow.feng.parser;

import org.cossbow.feng.ast.proc.PrototypeDefinition;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class FunctionParseTest extends BaseParseTest {

    @Test
    public void testFunction() {
        var symbol = randVarSymbol(16);
        var fn = doParseFunc("func %s(){}"
                .formatted(symbol), symbol.name());
        Assertions.assertEquals(symbol, fn.symbol());
        Assertions.assertTrue(fn.generic().isEmpty());
    }

    @Test
    public void testPrototype() {
        var name = randVarSymbol(16);
        var pt = (PrototypeDefinition) doParseType(
                "func %s=();".formatted(name), name);
        Assertions.assertEquals(name, pt.symbol());
        Assertions.assertTrue(pt.generic().isEmpty());
    }

}
