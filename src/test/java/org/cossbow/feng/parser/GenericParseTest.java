package org.cossbow.feng.parser;

import org.cossbow.feng.ast.Symbol;
import org.cossbow.feng.ast.TypeDomain;
import org.cossbow.feng.ast.gen.*;
import org.cossbow.feng.ast.oop.ClassDefinition;
import org.cossbow.feng.ast.oop.InterfaceDefinition;
import org.cossbow.feng.util.Groups;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

public class GenericParseTest extends BaseParseTest {

    static final List<Groups.G2<String, Boolean>> simpleGlobalDefineFmt = List.of(
            Groups.g2("func foo`%s`(){}", false),
            Groups.g2("func Foo`%s`();", true),
            Groups.g2("class Foo`%s`{}", true),
            Groups.g2("interface Foo`%s`{}", true),
            Groups.g2("struct Foo`%s`{}", true),
            Groups.g2("union Foo`%s`{}", true)
    );
    static final Map<TypeOperator, String> typeOperatorSymbol = Map.of(
            TypeOperator.AND, "&",
            TypeOperator.OR, "|"
    );

    private TypeParameters doParse(Groups.G2<String, Boolean> g2, String args) {
        var code = g2.a().formatted(args);
        var def = g2.b() ? doParseType(code, "Foo")
                : doParseFunc(code, "foo");
        return def.generic();
    }

    @Test
    public void testSimpleType() {
        for (var g2 : simpleGlobalDefineFmt) {
            for (int size = 1; size <= 8; size++) {
                var params = anyNames(RandTypeName, 8, size);
                var def = doParse(g2, idList(params));
                checkIds(params, def.params());
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
            checkIds(params, m.generic().params());
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
        for (var g2 : simpleGlobalDefineFmt) {
            var type = randTypeName(2);
            var cov = symbol(randTypeName(8));
            var param = doParse(g2, type + " " + cov)
                    .params().get(type);
            Assertions.assertEquals(type, param.name());
            Assertions.assertEquals(cov, getSimpleTypeParam(param.constraint().must()));
        }
    }

    @Test
    public void testTypeConstraint2() {
        for (var g2 : simpleGlobalDefineFmt) {
            for (var d : TypeDomain.values()) {
                if (d.builtin) continue;
                var type = randTypeName(2);
                var param = doParse(g2, type + " " + d.name)
                        .params().get(type);
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
            for (var g2 : simpleGlobalDefineFmt) {
                var type = randTypeName(2);
                var a = symbol(randTypeName(8));
                var b = symbol(randTypeName(8));
                var param = doParse(g2, type + " " + a + op.getValue() + b)
                        .params().get(type);
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
        for (var g2 : simpleGlobalDefineFmt) {
            var type = randTypeName(2);
            var a = symbol(randTypeName(8));
            var b = symbol(randTypeName(8));
            var c = symbol(randTypeName(8));
            var p = "%s %s & %s | %s".formatted(type, a, b, c);
            var param = doParse(g2, p).params().get(type);
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
        for (var g2 : simpleGlobalDefineFmt) {
            var type = randTypeName(2);
            var a = symbol(randTypeName(8));
            var b = symbol(randTypeName(8));
            var c = symbol(randTypeName(8));
            var p = "%s %s | %s & %s".formatted(type, a, b, c);
            var param = doParse(g2, p).params().get(type);
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
        var dt = (DerivedType) ((DefinedTypeConstraint) e).definedType();
        Assertions.assertTrue(dt.generic().isEmpty());
        return dt.symbol();
    }

}
