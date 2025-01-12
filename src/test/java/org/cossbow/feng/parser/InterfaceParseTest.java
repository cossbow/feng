package org.cossbow.feng.parser;


import org.cossbow.feng.ast.oop.InterfaceDefinition;
import org.cossbow.feng.ast.oop.InterfaceMethod;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class InterfaceParseTest extends BaseParseTest {


    @Test
    public void testMethod1() {
        var typeName = randTypeName(32);
        var methodName = randVarFuncName(12);
        var code = "interface %s { %s();}".formatted(typeName, methodName);
        var def = (InterfaceDefinition) doParseDefinition(code);
        Assertions.assertEquals(typeName, def.name().orElseThrow());
        Assertions.assertEquals(1, def.methods().size());
        var method = def.methods().getFirst();
        Assertions.assertEquals(methodName, method.name().orElseThrow());
    }

    @Test
    public void testMethod2() {
        var typeName = randTypeName(32);
        var methodName = randVarFuncName(12);
        var code = "interface %s { %s(int);}".formatted(typeName, methodName);
        var def = (InterfaceDefinition) doParseDefinition(code);
        Assertions.assertEquals(typeName, def.name().orElseThrow());
        Assertions.assertEquals(1, def.methods().size());
        var method = (InterfaceMethod) def.methods().getFirst();
        Assertions.assertEquals(methodName, method.name().orElseThrow());
    }

    @Test
    public void testMethod3() {
        var typeName = randTypeName(32);
        var methodName = randVarFuncName(12);
        var code = "interface %s { %s()int;}".formatted(typeName, methodName);
        var def = (InterfaceDefinition) doParseDefinition(code);
        Assertions.assertEquals(typeName, def.name().orElseThrow());
        Assertions.assertEquals(1, def.methods().size());
        var method = def.methods().getFirst();
        Assertions.assertEquals(methodName, method.name().orElseThrow());
    }

    @Test
    public void testPart1() {
        var typeName = randTypeName(32);
        var methodName = randVarFuncName(12);
        var code = "interface %s { %s;}".formatted(typeName, methodName);
        var def = (InterfaceDefinition) doParseDefinition(code);
        Assertions.assertEquals(typeName, def.name().orElseThrow());
        Assertions.assertEquals(1, def.parts().size());
        var part = def.parts().getFirst();
        Assertions.assertEquals(methodName, part.name());
    }

    @Test
    public void testGeneric() {
        var typeName = randTypeName(32);
        var methodName = randVarFuncName(12);
        var typeParams = anyNames(RandTypeName, 12, 8);
        var code = "interface %s`%s` { %s;}".formatted(typeName, idList(typeParams), methodName);
        var def = (InterfaceDefinition) doParseDefinition(code);
        Assertions.assertEquals(typeName, def.name().orElseThrow());

        var part = def.parts().getFirst();
        Assertions.assertEquals(methodName, part.name());

        var gp = def.generic().params();
        Assertions.assertEquals(typeParams.size(), gp.size());
        for (int i = 0; i < typeParams.size(); i++) {
            Assertions.assertEquals(typeParams.get(i), gp.get(i).name());
        }
    }

    @Test
    public void testGenericMethod() {
        var typeName = randTypeName(32);
        var methodName = randVarFuncName(12);
        var typeParams = anyNames(RandTypeName, 12, 8);
        var code = "interface %s { %s`%s`(); }".formatted(typeName, methodName, idList(typeParams));
        var def = (InterfaceDefinition) doParseDefinition(code);
        Assertions.assertEquals(typeName, def.name().orElseThrow());
        Assertions.assertEquals(1, def.methods().size());
        var part = def.methods().getFirst();
        var params = part.generic().params();
        Assertions.assertEquals(typeParams.size(), params.size());
        for (int i = 0; i < typeParams.size(); i++) {
            Assertions.assertEquals(typeParams.get(i), params.get(i).name());
        }
    }

    @Test
    public void testGenericPart() {
        var typeName = randTypeName(32);
        var methodName = randVarFuncName(12);
        var typeParams = anyNames(RandTypeName, 12, 8);
        var code = "interface %s { %s`%s`;}".formatted(typeName, methodName, idList(typeParams));
        var def = (InterfaceDefinition) doParseDefinition(code);
        Assertions.assertEquals(typeName, def.name().orElseThrow());
        var part = def.parts().getFirst();
        Assertions.assertEquals(methodName, part.name());
        var gp = part.generic().arguments();
        Assertions.assertEquals(typeParams.size(), gp.size());
        for (int i = 0; i < typeParams.size(); i++) {
            Assertions.assertEquals(typeParams.get(i), typeName(gp.get(i)));
        }
    }

}
