package org.cossbow.feng.parser;

import org.cossbow.feng.ast.proc.FunctionDefinition;
import org.cossbow.feng.ast.proc.PrototypeDefinition;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class FunctionParseTest extends BaseParseTest {

    @Test
    public void testFunction() {
        var name = randVarFuncName(16);
        var fn = (FunctionDefinition) doParseDefinition("func %s(){}".formatted(name));
        Assertions.assertEquals(name, fn.name().orElseThrow());
        Assertions.assertTrue(fn.generic().isEmpty());
    }

    @Test
    public void testFunctionGeneric() {
        var name = randVarFuncName(16);
        var typeParams = anyNames(RandTypeName, 12, 8);
        var code = "func %s`%s`(){}".formatted(name, idList(typeParams));
        var fn = (FunctionDefinition) doParseDefinition(code);
        Assertions.assertEquals(name, fn.name().orElseThrow());
        var genParams = fn.generic().params();
        Assertions.assertEquals(typeParams.size(), genParams.size());
        for (int i = 0; i < typeParams.size(); i++) {
            Assertions.assertEquals(typeParams.get(i), genParams.get(i).name());
        }
    }

    @Test
    public void testPrototype() {
        var name = randVarFuncName(16);
        var pt = (PrototypeDefinition) doParseDefinition("func %s();".formatted(name));
        Assertions.assertEquals(name, pt.name().orElseThrow());
        Assertions.assertTrue(pt.generic().isEmpty());
    }

    @Test
    public void testPrototypeGeneric() {
        var name = randVarFuncName(16);
        var typeParams = anyNames(RandTypeName, 12, 8);
        var code = "func %s`%s`();".formatted(name, idList(typeParams));
        var def = (PrototypeDefinition) doParseDefinition(code);
        Assertions.assertEquals(name, def.name().orElseThrow());
        var genParams = def.generic().params();
        Assertions.assertEquals(typeParams.size(), genParams.size());
        for (int i = 0; i < typeParams.size(); i++) {
            Assertions.assertEquals(typeParams.get(i), genParams.get(i).name());
        }
    }

}
