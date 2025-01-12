package org.cossbow.feng.parser;

import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.gen.*;
import org.cossbow.feng.ast.oop.ClassDefinition;
import org.cossbow.feng.ast.oop.InterfaceDefinition;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

public class GenericParseTest extends BaseParseTest {

    static final List<String> simpleGlobalDefineFmt = List.of(
            "func foo`%s`(){}",
            "func foo`%s`();",
            "class Foo`%s`{}",
            "interface Foo`%s`{}",
            "struct Foo`%s`{}",
            "union Foo`%s`{}"
    );
    static final Map<TypeOperator, String> typeOperatorSymbol = Map.of(
            TypeOperator.AND, "&",
            TypeOperator.OR, "|"
    );

    @Test
    public void testSimpleTypeParameter() {
        for (var fmt : simpleGlobalDefineFmt) {
            for (int size = 1; size <= 8; size++) {
                var params = anyNames(RandTypeName, 8, size);
                var code = fmt.formatted(idList(params));
                var def = doParseDefinition(code);
                checkIds(params, def.generic().params(), TypeParameter::name);
            }
        }
    }

    @Test
    public void testSimpleClassMethodParameter() {
        for (int size = 1; size <= 8; size++) {
            var params = anyNames(RandTypeName, 8, size);
            var code = "class A{func foo`%s`(){}}".formatted(idList(params));
            var def = (ClassDefinition) doParseDefinition(code);
            var method = def.methods().getFirst();
            checkIds(params, method.generic().params(), TypeParameter::name);
        }
    }

    @Test
    public void testSimpleInterfaceMethodParameter() {
        for (int size = 1; size <= 8; size++) {
            var params = anyNames(RandTypeName, 8, size);
            var code = "interface A{foo`%s`();}".formatted(idList(params));
            var def = (InterfaceDefinition) doParseDefinition(code);
            var method = def.methods().getFirst();
            checkIds(params, method.generic().params(), TypeParameter::name);
        }
    }

    @Test
    public void testSecondTypeParameter() {
        for (var fmt : simpleGlobalDefineFmt) {
            var type = randTypeName(2);
            var cov = randTypeName(8);
            var code = fmt.formatted(type + ":" + cov);
            var param = doParseDefinition(code).generic().params().getFirst();
            Assertions.assertEquals(type, param.name());
            Assertions.assertEquals(cov, getSimpleTypeParam(param.constraint().orElseThrow()));
        }
    }

    @Test
    public void testTypeParameterExpr() {
        for (var op : typeOperatorSymbol.entrySet()) {
            for (var fmt : simpleGlobalDefineFmt) {
                var type = randTypeName(2);
                var a = randTypeName(8);
                var b = randTypeName(8);
                var code = fmt.formatted(type + ":" + a + op.getValue() + b);
                var param = doParseDefinition(code).generic().params().getFirst();
                Assertions.assertEquals(type, param.name());
                var expr = (BinaryTypeExpression) param.constraint().orElseThrow();
                Assertions.assertSame(op.getKey(), expr.operator());
                Assertions.assertEquals(a, getSimpleTypeParam(expr.left()));
                Assertions.assertEquals(b, getSimpleTypeParam(expr.right()));
            }
        }
    }

    @Test
    public void testPriority1() {
        for (var fmt : simpleGlobalDefineFmt) {
            var type = randTypeName(2);
            var a = randTypeName(8);
            var b = randTypeName(8);
            var c = randTypeName(8);
            var code = fmt.formatted("%s: %s & %s | %s".formatted(type, a, b, c));
            var param = doParseDefinition(code).generic().params().getFirst();
            Assertions.assertEquals(type, param.name());
            var expr = (BinaryTypeExpression) param.constraint().orElseThrow();
            Assertions.assertSame(TypeOperator.OR, expr.operator());
            var left = (BinaryTypeExpression) expr.left();
            Assertions.assertEquals(a, getSimpleTypeParam(left.left()));
            Assertions.assertEquals(b, getSimpleTypeParam(left.right()));
            Assertions.assertEquals(c, getSimpleTypeParam(expr.right()));
        }
    }

    @Test
    public void testPriority2() {
        for (var fmt : simpleGlobalDefineFmt) {
            var type = randTypeName(2);
            var a = randTypeName(8);
            var b = randTypeName(8);
            var c = randTypeName(8);
            var code = fmt.formatted("%s: %s | %s & %s".formatted(type, a, b, c));
            var param = doParseDefinition(code).generic().params().getFirst();
            Assertions.assertEquals(type, param.name());
            var expr = (BinaryTypeExpression) param.constraint().orElseThrow();
            Assertions.assertSame(TypeOperator.OR, expr.operator());
            Assertions.assertEquals(a, getSimpleTypeParam(expr.left()));
            var right = (BinaryTypeExpression) expr.right();
            Assertions.assertEquals(b, getSimpleTypeParam(right.left()));
            Assertions.assertEquals(c, getSimpleTypeParam(right.right()));
        }
    }

    private Identifier getSimpleTypeParam(TypeExpression e) {
        var dt = ((PrimaryTypeExpression) e).definedType();
        Assertions.assertTrue(dt.generic().isEmpty());
        return dt.name();
    }

}
