package org.cossbow.feng.parser;

import org.cossbow.feng.ast.Symbol;
import org.cossbow.feng.ast.TypeDomain;
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
            "func Foo`%s`();",
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
    public void testSimpleType() {
        for (var fmt : simpleGlobalDefineFmt) {
            for (int size = 1; size <= 8; size++) {
                var params = anyNames(RandTypeName, 8, size);
                var code = fmt.formatted(idList(params));
                var def = doParseFirstDef(code);
                checkIds(params, def.generic().params());
            }
        }
    }

    @Test
    public void testSimpleClassMethod() {
        for (int size = 1; size <= 8; size++) {
            var params = anyNames(RandTypeName, 8, size);
            var code = "class A{func foo`%s`(){}}".formatted(idList(params));
            var def = (ClassDefinition) doParseType(code, "A");
            var m = def.methods().get(identifier("foo"));
            checkIds(params, m.func().generic().params());
        }
    }

    @Test
    public void testSimpleInterfaceMethod() {
        for (int size = 1; size <= 8; size++) {
            var params = anyNames(RandTypeName, 8, size);
            var code = "interface A{foo`%s`();}".formatted(idList(params));
            var def = (InterfaceDefinition) doParseType(code, "A");
            var method = def.methods().get(identifier("foo"));
            checkIds(params, method.generic().params());
        }
    }

    @Test
    public void testTypeConstraint1() {
        for (var fmt : simpleGlobalDefineFmt) {
            var type = randTypeName(2);
            var cov = symbol(randTypeName(8));
            var code = fmt.formatted(type + " " + cov);
            var param = doParseFirstDef(code).generic().params().get(type);
            Assertions.assertEquals(type, param.name());
            Assertions.assertEquals(cov, getSimpleTypeParam(param.constraint().must()));
        }
    }

    @Test
    public void testTypeConstraint2() {
        for (var fmt : simpleGlobalDefineFmt) {
            for (var d : TypeDomain.values()) {
                if (d == TypeDomain.PRIMITIVE) continue;
                var type = randTypeName(2);
                var code = fmt.formatted(type + " " + d.name);
                var param = doParseFirstDef(code)
                        .generic().params().get(type);
                Assertions.assertEquals(type, param.name());
                var dtc = (DomainTypeConstraint) param
                        .constraint().must();
                Assertions.assertEquals(d, dtc.domain());
            }
        }
    }

    @Test
    public void testTypeConstraintExpr() {
        for (var op : typeOperatorSymbol.entrySet()) {
            for (var fmt : simpleGlobalDefineFmt) {
                var type = randTypeName(2);
                var a = symbol(randTypeName(8));
                var b = symbol(randTypeName(8));
                var code = fmt.formatted(type + " " + a + op.getValue() + b);
                var param = doParseFirstDef(code).generic().params().get(type);
                Assertions.assertEquals(type, param.name());
                var expr = (BinaryTypeConstraint) param.constraint().must();
                Assertions.assertSame(op.getKey(), expr.operator());
                Assertions.assertEquals(a, getSimpleTypeParam(expr.left()));
                Assertions.assertEquals(b, getSimpleTypeParam(expr.right()));
            }
        }
    }

    @Test
    public void testConstraintPriority1() {
        for (var fmt : simpleGlobalDefineFmt) {
            var type = randTypeName(2);
            var a = symbol(randTypeName(8));
            var b = symbol(randTypeName(8));
            var c = symbol(randTypeName(8));
            var code = fmt.formatted("%s %s & %s | %s".formatted(type, a, b, c));
            var param = doParseFirstDef(code).generic().params().get(type);
            Assertions.assertEquals(type, param.name());
            var expr = (BinaryTypeConstraint) param.constraint().must();
            Assertions.assertSame(TypeOperator.OR, expr.operator());
            var left = (BinaryTypeConstraint) expr.left();
            Assertions.assertEquals(a, getSimpleTypeParam(left.left()));
            Assertions.assertEquals(b, getSimpleTypeParam(left.right()));
            Assertions.assertEquals(c, getSimpleTypeParam(expr.right()));
        }
    }

    @Test
    public void testConstraintPriority2() {
        for (var fmt : simpleGlobalDefineFmt) {
            var type = randTypeName(2);
            var a = symbol(randTypeName(8));
            var b = symbol(randTypeName(8));
            var c = symbol(randTypeName(8));
            var code = fmt.formatted("%s %s | %s & %s".formatted(type, a, b, c));
            var param = doParseFirstDef(code).generic().params().get(type);
            Assertions.assertEquals(type, param.name());
            var expr = (BinaryTypeConstraint) param.constraint().must();
            Assertions.assertSame(TypeOperator.OR, expr.operator());
            Assertions.assertEquals(a, getSimpleTypeParam(expr.left()));
            var right = (BinaryTypeConstraint) expr.right();
            Assertions.assertEquals(b, getSimpleTypeParam(right.left()));
            Assertions.assertEquals(c, getSimpleTypeParam(right.right()));
        }
    }

    private Symbol getSimpleTypeParam(TypeConstraint e) {
        var dt = ((DefinedTypeConstraint) e).definedType();
        Assertions.assertTrue(dt.generic().isEmpty());
        return dt.symbol();
    }

}
