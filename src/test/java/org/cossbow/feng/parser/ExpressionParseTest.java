package org.cossbow.feng.parser;

import org.cossbow.feng.ast.BinaryOperator;
import org.cossbow.feng.ast.UnaryOperator;
import org.cossbow.feng.ast.dcl.DerivedTypeDeclarer;
import org.cossbow.feng.ast.dcl.NewArrayType;
import org.cossbow.feng.ast.dcl.NewDerivedType;
import org.cossbow.feng.ast.expr.*;
import org.cossbow.feng.ast.lit.*;
import org.cossbow.feng.ast.stmt.AssignmentsStatement;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import static org.cossbow.feng.ast.BinaryOperator.*;
import static org.cossbow.feng.ast.dcl.ReferKind.STRONG;

public class ExpressionParseTest extends BaseParseTest {

    static final BinaryOperator[][] binaryPriorities = {
            {POW},                                      // priority=2
            {MUL, DIV, MOD},                            // priority=3
            {ADD, SUB},                                 // priority=4
            {LSHIFT, RSHIFT},                           // priority=5
            {BITAND},                                   // priority=6
            {BITXOR},                                   // priority=7
            {BITOR},                                    // priority=8
            {LT, LE, EQ, NE, GT, GE},                   // priority=9
            {AND},                                      // priority=10
            {OR},                                       // priority=11
    };

    @Test
    public void testLiteral() {
        var expr = this.<LiteralExpression>parseExpr("true");
        var bool = (BoolLiteral) expr.literal();
        Assertions.assertTrue(bool.value());
    }

    //
    // new

    @Test
    public void testNew() {
        var typeName = randTypeSymbol(32);
        var expr = this.<NewExpression>parseExpr("new(%s)".formatted(typeName));
        var defType = ((NewDerivedType) expr.type()).type();
        Assertions.assertTrue(defType.generic().isEmpty());
        Assertions.assertEquals(typeName, defType.symbol());
    }

    @Test
    public void testNewGeneric() {
        var typeName = symbol(randTypeName(32));
        for (int i = 1; i <= 8; i++) {
            var typeParams = anyNames(RandTypeSymbol, 8, i);
            var code = "new(%s`%s`)".formatted(typeName, idList(typeParams));
            var expr = this.<NewExpression>parseExpr(code);
            var defType = ((NewDerivedType) expr.type()).type();
            Assertions.assertEquals(typeName, defType.symbol());
            checkIds(typeParams, defType.generic().arguments(),
                    BaseParseTest::typeName);
        }
    }

    @Test
    public void testNewInit() {
        var typeName = randTypeSymbol(32);
        var code = "new(%s,{Id=1})".formatted(typeName);
        var expr = this.<NewExpression>parseExpr(code);
        var defType = ((NewDerivedType) expr.type()).type();
        Assertions.assertEquals(typeName, defType.symbol());
        Assertions.assertInstanceOf(ObjectExpression.class, expr.arg().must());
    }

    @Test
    public void testNewArrayInit() {
        var typeName = randTypeSymbol(32);
        var len = ThreadLocalRandom.current().nextInt(1, Integer.MAX_VALUE);
        var code = "new([%d]%s, [1,2])".formatted(len, typeName);
        var expr = this.<NewExpression>parseExpr(code);
        var arr = (NewArrayType) expr.type();
        Assertions.assertEquals(typeName, typeName(arr.element()));
        Assertions.assertEquals(BigInteger.valueOf(len), integer(arr.length()).value());
        Assertions.assertInstanceOf(ArrayExpression.class, expr.arg().must());
    }

    @Test
    public void testAssert() {
        var name = randVarSymbol(8);
        var typeName = randTypeSymbol(16);
        var expr = (AssertExpression) parseExpr("%s?(*%s)".formatted(name, typeName));
        Assertions.assertEquals(name, varName(expr.subject()));
        var type = (DerivedTypeDeclarer) expr.type();
        Assertions.assertEquals(typeName, type.definedType().symbol());
        Assertions.assertTrue(type.definedType().generic().isEmpty());
        var ref = type.refer().get();
        Assertions.assertSame(STRONG, ref.kind());
        Assertions.assertFalse(ref.required());
    }

    //
    // reference

    @Test
    public void testVariable() {
        var name = randVarSymbol(12);
        var expr = (ReferExpression) parseExpr(name + "");
        Assertions.assertEquals(name, expr.symbol());
    }

    //
    // indexOf

    @Test
    public void testIndexOfArrayExpr() {
        var rand = ThreadLocalRandom.current();
        var values = rand.ints(10, 0, Integer.MAX_VALUE).boxed().toList();
        var idx = randInt(0, values.size());
        var litStr = values.stream().map(Objects::toString).collect(Collectors.joining(","));
        var code = "[%s][%d]".formatted(litStr, idx);
        var expr = (IndexOfExpression) parseExpr(code);
        var arr = (ArrayExpression) expr.subject();
        Assertions.assertEquals(values, arr.elements().stream()
                .map(e -> integer(e).value().intValueExact()).toList());
        Assertions.assertEquals(idx, integer(expr.index()).value());
    }

    @Test
    public void testIndexOfPairsExpr() {
        var pf = randVarSymbol(12);
        var pv = randVarSymbol(12);
        var idx = randInt(0, Integer.MAX_VALUE);
        var code = "{%s:%s}[%d]".formatted(pf, pv, idx);
        var expr = (IndexOfExpression) parseExpr(code);
        var pair = ((PairsExpression) expr.subject()).pairs().getFirst();
        Assertions.assertEquals(pf, varName(pair.key()));
        Assertions.assertEquals(pv, varName(pair.value()));
        Assertions.assertEquals(idx, integer(expr.index()).value());
    }

    @Test
    public void testIndexOfByName() {
        var name = randVarSymbol(12);
        var index = randVarSymbol(6);
        var expr = (IndexOfExpression) parseExpr("%s[%s]".formatted(name, index));
        Assertions.assertEquals(name, varName(expr.subject()));
        Assertions.assertEquals(index, varName(expr.index()));
    }

    @Test
    public void testIndexOfNewArray() {
        var size = randVarSymbol(12);
        var type = randTypeSymbol(12);
        var index = randVarSymbol(12);
        var expr = (IndexOfExpression) parseExpr("new([%s]%s)[%s]".formatted(size, type, index));

        var nt = (NewArrayType) ((NewExpression) expr.subject()).type();
        Assertions.assertEquals(size, varName(nt.length()));
        var dt = (DerivedTypeDeclarer) nt.element();
        Assertions.assertEquals(type, dt.definedType().symbol());

        Assertions.assertEquals(index, varName(expr.index()));
    }

    @Test
    public void testIndexOfIndexOf() {
        var name = randVarSymbol(12);
        var index1 = randVarSymbol(6);
        var index2 = randVarSymbol(6);
        var expr = (IndexOfExpression) parseExpr("%s[%s][%s]".formatted(name, index1, index2));

        var left = (IndexOfExpression) expr.subject();
        Assertions.assertEquals(name, varName(left.subject()));
        Assertions.assertEquals(index1, varName(left.index()));

        Assertions.assertEquals(index2, varName(expr.index()));
    }

    @Test
    public void testIndexOfMemberOf() {
        var name = randVarSymbol(12);
        var field = randVarName(6);
        var index = randVarSymbol(6);
        var expr = (IndexOfExpression) parseExpr("%s.%s[%s]".formatted(name, field, index));

        var left = (MemberOfExpression) expr.subject();
        Assertions.assertEquals(name, varName(left.subject()));
        Assertions.assertEquals(field, left.member());

        Assertions.assertEquals(index, varName(expr.index()));
    }

    @Test
    public void testIndexOfReturnedArray() {
        var name = randVarSymbol(12);
        var index = randVarSymbol(6);
        var expr = (IndexOfExpression) parseExpr("%s()[%s]".formatted(name, index));

        var left = (CallExpression) expr.subject();
        Assertions.assertEquals(name, varName(left.callee()));

        Assertions.assertEquals(index, varName(expr.index()));
    }


    //
    // memberOf

    @Test
    public void testMemberOfObjectExpr() {
        var pf = randVarName(12);
        var pv = randVarSymbol(12);
        var field = randVarName(8);
        var expr = (MemberOfExpression) parseExpr("{%s=%s}.%s".formatted(pf, pv, field));
        var entries = ((ObjectExpression) expr.subject()).entries();
        Assertions.assertEquals(pv, varName(entries.get(pf)));
        Assertions.assertEquals(field, expr.member());
    }

    @Test
    public void testMemberOfByName() {
        var name = randVarSymbol(12);
        var field = randVarName(8);
        var expr = (MemberOfExpression) parseExpr(name + "." + field);
        Assertions.assertEquals(name, varName(expr.subject()));
        Assertions.assertEquals(field, expr.member());
    }

    @Test
    public void testMemberOfField() {
        var name = randVarSymbol(12);
        var field1 = randVarName(8);
        var field2 = randVarName(6);
        var expr = (MemberOfExpression) parseExpr("%s.%s.%s".formatted(name, field1, field2));

        var left = (MemberOfExpression) expr.subject();
        Assertions.assertEquals(name, varName(left.subject()));
        Assertions.assertEquals(field1, left.member());

        Assertions.assertEquals(field2, expr.member());
    }

    @Test
    public void testMemberOfIndexOf() {
        var name = randVarSymbol(12);
        var index = randVarSymbol(6);
        var field = randVarName(8);
        var expr = (MemberOfExpression) parseExpr("%s[%s].%s".formatted(name, index, field));

        var left = (IndexOfExpression) expr.subject();
        Assertions.assertEquals(name, varName(left.subject()));
        Assertions.assertEquals(index, varName(left.index()));

        Assertions.assertEquals(field, expr.member());
    }

    @Test
    public void testMemberOfReturnedObj() {
        var name = randVarSymbol(12);
        var field = randVarName(8);
        var expr = (MemberOfExpression) parseExpr(name + "()." + field);

        var left = (CallExpression) expr.subject();
        Assertions.assertEquals(name, varName(left.callee()));

        Assertions.assertEquals(field, expr.member());
    }

    @Test
    public void testMemberOfNewObj() {
        var type = randTypeSymbol(16);
        var field = randVarName(8);
        var expr = (MemberOfExpression) parseExpr("new(%s).%s".formatted(type, field));

        var left = (NewExpression) expr.subject();
        var defType = ((NewDerivedType) left.type()).type();
        Assertions.assertEquals(type, defType.symbol());

        Assertions.assertEquals(field, expr.member());
    }

    //
    // argument set

    @Test
    public void testArgSetByName() {
        var name = randVarSymbol(12);
        var expr = (CallExpression) parseExpr(name + "()");
        Assertions.assertEquals(name, varName(expr.callee()));
    }

    @Test
    public void testArgSetOfMethod() {
        var name = randVarSymbol(12);
        var field = randVarName(12);
        var expr = (CallExpression) parseExpr(name + "." + field + "()");
        var left = (MemberOfExpression) expr.callee();
        Assertions.assertEquals(name, varName(left.subject()));
        Assertions.assertEquals(field, left.member());
    }

    @Test
    public void testArgSetOfIndex() {
        var name = randVarSymbol(12);
        var index = randVarSymbol(8);
        var expr = (CallExpression) parseExpr(name + "[" + index + "]()");
        var left = (IndexOfExpression) expr.callee();
        Assertions.assertEquals(name, varName(left.subject()));
        Assertions.assertEquals(index, varName(left.index()));
    }

    @Test
    public void testArgSetReturnedClosure() {
        var name = randVarSymbol(12);
        var expr = (CallExpression) parseExpr(name + "()()");
        var left = (CallExpression) expr.callee();
        Assertions.assertEquals(name, varName(left.callee()));
    }

    //
    // unary

    @SuppressWarnings("unchecked")
    <E extends Expression> E parseExpr(String expr) {
        var stmt = (AssignmentsStatement) doParseLocal("a = " + expr + ";");
        return (E) first(stmt.tuple());
    }

    @Test
    public void testUnaryLiteral() {
        var rand = ThreadLocalRandom.current();
        for (var op : UnaryOperator.values()) {
            var i = rand.nextInt(0, Integer.MAX_VALUE);
            var expr = (UnaryExpression) parseExpr(operator(op) + i);
            Assertions.assertSame(op, expr.operator());
            Assertions.assertEquals(BigInteger.valueOf(i), integer(expr.operand()).value());
        }
    }

    @Test
    public void testUnaryVariable() {
        for (var op : UnaryOperator.values()) {
            var name = randVarSymbol(8);
            var expr = (UnaryExpression) parseExpr(operator(op) + name);
            Assertions.assertSame(op, expr.operator());
            Assertions.assertEquals(name, varName(expr.operand()));
        }
    }

    @Test
    public void testUnaryIndexOf() {
        for (var op : UnaryOperator.values()) {
            var name = randVarSymbol(8);
            var index = randVarSymbol(4);
            var unary = (UnaryExpression) parseExpr(operator(op) + name + "[" + index + "]");
            Assertions.assertSame(op, unary.operator());
            var indexOf = (IndexOfExpression) unary.operand();
            Assertions.assertEquals(name, varName(indexOf.subject()));
            Assertions.assertEquals(index, varName(indexOf.index()));
        }
    }

    @Test
    public void testUnaryAssert() {
        for (var op : UnaryOperator.values()) {
            var name = randVarSymbol(8);
            var type = randTypeSymbol(12);
            var unary = (UnaryExpression) parseExpr(operator(op) + name + "?(" + type + ")");
            Assertions.assertSame(op, unary.operator());
            var ass = (AssertExpression) unary.operand();
            Assertions.assertEquals(name, varName(ass.subject()));
            Assertions.assertEquals(type, typeName(ass.type()));
        }
    }

    @Test
    public void testUnaryMemberOf() {
        for (var op : UnaryOperator.values()) {
            var name = randVarSymbol(8);
            var field = randVarName(4);
            var unary = (UnaryExpression) parseExpr(operator(op) + name + "." + field);
            Assertions.assertSame(op, unary.operator());
            var indexOf = (MemberOfExpression) unary.operand();
            Assertions.assertEquals(name, varName(indexOf.subject()));
            Assertions.assertEquals(field, indexOf.member());
        }
    }

    @Test
    public void testUnaryArgSet() {
        for (var op : UnaryOperator.values()) {
            var name = randVarSymbol(8);
            var unary = (UnaryExpression) parseExpr(operator(op) + name + "()");
            Assertions.assertSame(op, unary.operator());
            var argsExpr = (CallExpression) unary.operand();
            Assertions.assertEquals(name, varName(argsExpr.callee()));
        }
    }

    //
    // binary

    @Test
    public void testUnaryPriorities() {
        var operators = UnaryOperator.values();
        for (var left : operators) {
            for (var right : operators) {
                var name = randVarSymbol(8);
                var code = "%s %s %s".formatted(operator(left), operator(right), name);
                var low = (UnaryExpression) parseExpr(code);
                Assertions.assertSame(left, low.operator());
                var high = (UnaryExpression) low.operand();
                Assertions.assertSame(right, high.operator());
                Assertions.assertEquals(name, varName(high.operand()));
            }
        }
    }

    @Test
    public void testUnaryBinary() {
        for (var bop : values()) {
            for (var uop : UnaryOperator.values()) {
                var a = randVarSymbol(6);
                var b = randVarSymbol(6);
                var code = a + operator(bop) + operator(uop) + b;
                var binary = (BinaryExpression) parseExpr(code);
                Assertions.assertEquals(a, varName(binary.left()));
                Assertions.assertSame(bop, binary.operator());
                var unary = (UnaryExpression) binary.right();
                Assertions.assertSame(uop, unary.operator());
                Assertions.assertEquals(b, varName(unary.operand()));
            }
        }
    }

    @Test
    public void testPowerAssociativity() {
        var a = randVarSymbol(6);
        var b = randVarSymbol(6);
        var c = randVarSymbol(6);
        var code = a + "^" + b + "^" + c;
        var expr = (BinaryExpression) parseExpr(code);
        Assertions.assertSame(POW, expr.operator());
        Assertions.assertEquals(a, varName(expr.left()));
        var right = (BinaryExpression) expr.right();
        Assertions.assertSame(POW, right.operator());
        Assertions.assertEquals(b, varName(right.left()));
        Assertions.assertEquals(c, varName(right.right()));
    }

    @Test
    public void testBinaryAssociativity() {
        // exception: power operate is right associativity
        // normal: left associativity
        for (var i = 1; i < binaryPriorities.length; i++) {
            for (var l : binaryPriorities[i]) {
                for (var r : binaryPriorities[i]) {
                    var a = randVarSymbol(6);
                    var b = randVarSymbol(6);
                    var c = randVarSymbol(6);
                    var code = a + operator(l) + b + operator(r) + c;
                    var expr = (BinaryExpression) parseExpr(code);
                    Assertions.assertSame(r, expr.operator());
                    Assertions.assertEquals(c, varName(expr.right()));
                    var left = (BinaryExpression) expr.left();
                    Assertions.assertSame(l, left.operator());
                    Assertions.assertEquals(a, varName(left.left()));
                    Assertions.assertEquals(b, varName(left.right()));
                }
            }
        }
    }

    @Test
    public void testUnaryPowerPriority() {
        for (var uop : UnaryOperator.values()) {
            var a = randVarSymbol(6);
            var b = randVarSymbol(6);
            var unary = (UnaryExpression) parseExpr(operator(uop) + a + operator(POW) + b);
            Assertions.assertSame(uop, unary.operator());
            var pow = (BinaryExpression) unary.operand();
            Assertions.assertSame(POW, pow.operator());
            Assertions.assertEquals(a, varName(pow.left()));
            Assertions.assertEquals(b, varName(pow.right()));
        }
    }

    @Test
    public void testPowerUnaryPriority() {
        for (var uop : UnaryOperator.values()) {
            var a = randVarSymbol(6);
            var b = randVarSymbol(6);
            var pow = (BinaryExpression) parseExpr(a + operator(POW) + operator(uop) + b);
            Assertions.assertSame(POW, pow.operator());
            Assertions.assertEquals(a, varName(pow.left()));
            var unary = (UnaryExpression) pow.right();
            Assertions.assertSame(uop, unary.operator());
            Assertions.assertEquals(b, varName(unary.operand()));
        }
    }


    public void testBinaryWithPriority(BiConsumer<BinaryOperator, BinaryOperator> t) {
        for (int i = 1; i < binaryPriorities.length; i++) {
            var highOperators = binaryPriorities[i - 1];
            var lowOperators = binaryPriorities[i];
            for (var hop : highOperators) {
                for (var lop : lowOperators) {
                    t.accept(hop, lop);
                }
            }
        }
    }

    @Test
    public void testBinaryLiteral() {
        var rand = ThreadLocalRandom.current();
        testBinaryWithPriority((hop, lop) -> {
            var a = rand.nextInt(0, Integer.MAX_VALUE);
            var b = rand.nextInt(0, Integer.MAX_VALUE);
            var c = rand.nextInt(0, Integer.MAX_VALUE);
            var code = "%s %s %s %s %s".formatted(a, operator(lop), b, operator(hop), c);
            var low = (BinaryExpression) parseExpr(code);
            Assertions.assertSame(lop, low.operator());
            Assertions.assertEquals(BigInteger.valueOf(a), integer(low.left()).value());
            var high = (BinaryExpression) low.right();
            Assertions.assertSame(hop, high.operator());
            Assertions.assertEquals(BigInteger.valueOf(b), integer(high.left()).value());
            Assertions.assertEquals(BigInteger.valueOf(c), integer(high.right()).value());
        });
    }

    @Test
    public void testBinaryVariable() {
        testBinaryWithPriority((hop, lop) -> {
            var a = randVarSymbol(4);
            var b = randVarSymbol(4);
            var c = randVarSymbol(4);
            var code = "%s %s %s %s %s".formatted(a, operator(lop), b, operator(hop), c);
            var low = (BinaryExpression) parseExpr(code);
            Assertions.assertSame(lop, low.operator());
            Assertions.assertEquals(a, varName(low.left()));
            var high = (BinaryExpression) low.right();
            Assertions.assertSame(hop, high.operator());
            Assertions.assertEquals(b, varName(high.left()));
            Assertions.assertEquals(c, varName(high.right()));
        });
    }

    @Test
    public void testBinaryIndexOf() {
        testBinaryWithPriority((hop, lop) -> {
            var a = randVarSymbol(8);
            var i = randVarSymbol(4);
            var b = randVarSymbol(8);
            var j = randVarSymbol(4);
            var c = randVarSymbol(8);
            var k = randVarSymbol(4);
            var code = "%s[%s] %s %s[%s] %s %s[%s]".formatted(a, i, operator(lop),
                    b, j, operator(hop), c, k);
            var low = (BinaryExpression) parseExpr(code);
            Assertions.assertSame(lop, low.operator());
            var ll = (IndexOfExpression) low.left();
            Assertions.assertEquals(a, varName(ll.subject()), code);
            Assertions.assertEquals(i, varName(ll.index()), code);

            var high = (BinaryExpression) low.right();
            Assertions.assertSame(hop, high.operator());
            var hl = (IndexOfExpression) high.left();
            Assertions.assertEquals(b, varName(hl.subject()), code);
            Assertions.assertEquals(j, varName(hl.index()), code);

            var hr = (IndexOfExpression) high.right();
            Assertions.assertEquals(c, varName(hr.subject()), code);
            Assertions.assertEquals(k, varName(hr.index()), code);
        });
    }

    @Test
    public void testBinaryMemberOf() {
        testBinaryWithPriority((hop, lop) -> {
            var a = randVarSymbol(8);
            var i = randVarName(4);
            var b = randVarSymbol(8);
            var j = randVarName(4);
            var c = randVarSymbol(8);
            var k = randVarName(4);
            var code = "%s.%s %s %s.%s %s %s.%s".formatted(a, i, operator(lop),
                    b, j, operator(hop), c, k);
            var low = (BinaryExpression) parseExpr(code);
            Assertions.assertSame(lop, low.operator());
            var ll = (MemberOfExpression) low.left();
            Assertions.assertEquals(a, varName(ll.subject()), code);
            Assertions.assertEquals(i, ll.member(), code);

            var high = (BinaryExpression) low.right();
            Assertions.assertSame(hop, high.operator());
            var hl = (MemberOfExpression) high.left();
            Assertions.assertEquals(b, varName(hl.subject()), code);
            Assertions.assertEquals(j, hl.member(), code);

            var hr = (MemberOfExpression) high.right();
            Assertions.assertEquals(c, varName(hr.subject()), code);
            Assertions.assertEquals(k, hr.member(), code);
        });
    }

    @Test
    public void testBinaryArgSet() {
        testBinaryWithPriority((hop, lop) -> {
            var a = randVarSymbol(4);
            var b = randVarSymbol(4);
            var c = randVarSymbol(4);
            var code = "%s() %s %s() %s %s()".formatted(a, operator(lop), b, operator(hop), c);
            var low = (BinaryExpression) parseExpr(code);
            Assertions.assertSame(lop, low.operator());
            Assertions.assertEquals(a,
                    varName(((CallExpression) low.left()).callee()));
            var high = (BinaryExpression) low.right();
            Assertions.assertSame(hop, high.operator());
            Assertions.assertEquals(b,
                    varName(((CallExpression) high.left()).callee()));
            Assertions.assertEquals(c,
                    varName(((CallExpression) high.right()).callee()));
        });
    }

    // object


    @Test
    public void testObject() {
        var oe = (ObjectExpression) parseExpr("{id=5,fc=1.2,nm=\"gg\",ok=true,ex=nil,li=[1],ch={sid=8},t={a1:b1}}");

        Assertions.assertEquals(8, oe.entries().size());
        var entries = oe.entries();

        Assertions.assertInstanceOf(LiteralExpression.class, entries.get(identifier("id")));
        Assertions.assertInstanceOf(LiteralExpression.class, entries.get(identifier("fc")));
        Assertions.assertInstanceOf(LiteralExpression.class, entries.get(identifier("nm")));
        Assertions.assertInstanceOf(LiteralExpression.class, entries.get(identifier("ok")));
        Assertions.assertInstanceOf(LiteralExpression.class, entries.get(identifier("ex")));
        Assertions.assertInstanceOf(ArrayExpression.class, entries.get(identifier("li")));
        Assertions.assertInstanceOf(ObjectExpression.class, entries.get(identifier("ch")));
        Assertions.assertInstanceOf(PairsExpression.class, entries.get(identifier("t")));
    }

    // array

    @Test
    public void testArray() {
        var ae = (ArrayExpression) parseExpr("[15,1.5,\"yy\",false,nil,{id=0},[3],{a2:b2},-9,2+5]");

        Assertions.assertEquals(10, ae.elements().size());
        var els = ae.elements();

        Assertions.assertInstanceOf(IntegerLiteral.class, ((LiteralExpression) els.get(0)).literal());
        Assertions.assertInstanceOf(FloatLiteral.class, ((LiteralExpression) els.get(1)).literal());
        Assertions.assertInstanceOf(StringLiteral.class, ((LiteralExpression) els.get(2)).literal());
        Assertions.assertInstanceOf(BoolLiteral.class, ((LiteralExpression) els.get(3)).literal());
        Assertions.assertInstanceOf(NilLiteral.class, ((LiteralExpression) els.get(4)).literal());
        Assertions.assertInstanceOf(ObjectExpression.class, (els.get(5)));
        Assertions.assertInstanceOf(ArrayExpression.class, (els.get(6)));
        Assertions.assertInstanceOf(PairsExpression.class, (els.get(7)));
        Assertions.assertInstanceOf(UnaryExpression.class, (els.get(8)));
        Assertions.assertInstanceOf(BinaryExpression.class, (els.get(9)));
    }

}
