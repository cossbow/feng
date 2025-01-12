package org.cossbow.feng.parser;


import org.cossbow.feng.Pair;
import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.dcl.DefinedTypeDeclarer;
import org.cossbow.feng.ast.proc.FunctionDefinition;
import org.cossbow.feng.ast.proc.Procedure;
import org.cossbow.feng.ast.proc.Prototype;
import org.cossbow.feng.ast.stmt.BlockStatement;
import org.cossbow.feng.ast.stmt.CallStatement;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.stream.Collectors;

public class ProcedureParseTest extends BaseParseTest {

    Procedure parseProcedure(String procedure) {
        var code = "func test" + procedure;
        var func = (FunctionDefinition) doParseDefinition(code);
        return func.procedure();
    }

    Prototype parsePrototype(String prototype) {
        var proc = parseProcedure(prototype + "{}");
        return proc.prototype();
    }

    BlockStatement parseBody(String body) {
        var proc = parseProcedure("()" + body);
        return proc.body();
    }

    @Test
    public void testPrototype() {
        {
            var code = "()";
            var prototype = parsePrototype(code);
            Assertions.assertTrue(prototype.parameters().isEmpty());
            Assertions.assertTrue(prototype.returnSet().isEmpty());
        }
        {
            var code = "(int)";
            var prototype = parsePrototype(code);
            Assertions.assertEquals(1, prototype.parameters().size());
            Assertions.assertTrue(prototype.returnSet().isEmpty());
        }
        {
            var code = "(i,j int)";
            var prototype = parsePrototype(code);
            Assertions.assertEquals(2, prototype.parameters().size());
            Assertions.assertTrue(prototype.returnSet().isEmpty());
        }
        {
            var code = "() int ";
            var prototype = parsePrototype(code);
            Assertions.assertTrue(prototype.parameters().isEmpty());
            Assertions.assertEquals(1, prototype.returnSet().size());
        }
        {
            var code = "() (int,float) ";
            var prototype = parsePrototype(code);
            Assertions.assertTrue(prototype.parameters().isEmpty());
            Assertions.assertEquals(2, prototype.returnSet().size());
        }
    }

    @Test
    public void testPrototypeNamedParameter() {
        for (int n = 1; n <= 8; n++) {
            var expectParams = new ArrayList<Pair<Identifier, Identifier>>();
            var paramsSet = new ArrayList<String>();
            for (int s = 1; s <= n; s++) {
                var names = anyNames(RandVarFuncName, 12, s);
                var type = randTypeName(8);
                paramsSet.add(idList(names) + " " + type);
                for (var name : names) expectParams.add(Pair.of(name, type));
            }
            var code = "(%s)".formatted(String.join(",", paramsSet));
            var prototype = parsePrototype(code);
            var params = prototype.parameters();
            Assertions.assertEquals(expectParams.size(), params.size());
            for (int i = 0; i < expectParams.size(); i++) {
                var expect = expectParams.get(i);
                var variable = params.get(i).variable();
                Assertions.assertTrue(variable.isPresent());
                Assertions.assertEquals(expect.a(), variable.get().name());
                var vtd = (DefinedTypeDeclarer) params.get(i).type();
                Assertions.assertEquals(expect.b(), vtd.definedType().name());
                Assertions.assertFalse(vtd.pointer());
                Assertions.assertFalse(vtd.phantom());
                Assertions.assertFalse(vtd.optional());
            }
            Assertions.assertTrue(prototype.returnSet().isEmpty());
        }
    }

    @Test
    public void testPrototypeUnnamedParameter() {
        for (int size = 1; size <= 16; size++) {
            var expectTypes = anyNames(RandTypeName, 12, size);
            var prototype = parsePrototype("(%s)".formatted(idList(expectTypes)));
            var params = prototype.parameters();
            Assertions.assertEquals(expectTypes.size(), params.size());
            for (int i = 0; i < size; i++) {
                Assertions.assertEquals(expectTypes.get(i), typeName(params.get(i).type()));
            }
        }
    }

    @Test
    public void testPrototypeReturn() {
        {
            var prototype = parsePrototype("()");
            var types = prototype.returnSet();
            Assertions.assertTrue(types.isEmpty());
        }
        for (int n = 1; n <= 10; n++) {
            var defTypes = anyNames(RandTypeName, 12, n);
            var prototype = parsePrototype("()(%s)".formatted(idList(defTypes)));
            var types = prototype.returnSet();
            Assertions.assertEquals(n, types.size());
            for (int i = 0; i < types.size(); i++) {
                Assertions.assertEquals(defTypes.get(i), typeName(types.get(i)));
            }
        }
    }

    @Test
    public void testBody() {
        {
            var body = parseBody("{}");
            Assertions.assertEquals(0, body.list().size());
        }
        for (int size = 1; size <= 100; size++) {
            var callees = anyNames(RandVarFuncName, 10, size);
            var code = callees.stream().map(c -> c + "();").collect(Collectors.joining());
            var body = parseBody("{" + code + "}");
            var list = body.list();
            Assertions.assertEquals(callees.size(), list.size());
            for (int i = 0; i < size; i++) {
                var call = (CallStatement) list.get(i);
                Assertions.assertEquals(callees.get(i), varName(call.call().callee()));
            }
        }
    }
}
