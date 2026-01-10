package org.cossbow.feng.parser;


import org.cossbow.feng.ast.oop.InterfaceDefinition;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class InterfaceParseTest extends BaseParseTest {


    @Test
    public void testMethod1() {
        var typeName = randTypeName(32);
        var methodName = randVarName(12);
        var code = "interface %s { %s();}".formatted(typeName, methodName);
        var def = (InterfaceDefinition) doParseFirstDef(code);
        Assertions.assertEquals(typeName, def.name());
        Assertions.assertEquals(1, def.methods().size());
        var method = def.methods().get(methodName);
        Assertions.assertEquals(methodName, method.name());
    }

    @Test
    public void testMethod2() {
        var typeName = randTypeName(32);
        var methodName = randVarName(12);
        var code = "interface %s { %s(int);}".formatted(typeName, methodName);
        var def = (InterfaceDefinition) doParseFirstDef(code);
        Assertions.assertEquals(typeName, def.name());
        Assertions.assertEquals(1, def.methods().size());
        var method = def.methods().get(methodName);
        Assertions.assertEquals(methodName, method.name());
    }

    @Test
    public void testMethod3() {
        var typeName = randTypeName(32);
        var methodName = randVarName(12);
        var code = "interface %s { %s()int;}".formatted(typeName, methodName);
        var def = (InterfaceDefinition) doParseFirstDef(code);
        Assertions.assertEquals(typeName, def.name());
        Assertions.assertEquals(1, def.methods().size());
        var method = def.methods().get(methodName);
        Assertions.assertEquals(methodName, method.name());
    }

    @Test
    public void testPart1() {
        var typeName = randTypeName(32);
        var partName = symbol(randVarName(12));
        var code = "interface %s { %s;}".formatted(typeName, partName);
        var def = (InterfaceDefinition) doParseFirstDef(code);
        Assertions.assertEquals(typeName, def.name());
        Assertions.assertEquals(1, def.parts().size());
        var part = def.parts().getValue(0);
        Assertions.assertEquals(partName, part.symbol());
    }

}
