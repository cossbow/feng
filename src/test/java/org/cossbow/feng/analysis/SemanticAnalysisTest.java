package org.cossbow.feng.analysis;

import org.cossbow.feng.ast.BinaryOperator;
import org.cossbow.feng.ast.TypeDomain;
import org.cossbow.feng.ast.dcl.Primitive;
import org.cossbow.feng.err.SemanticException;
import org.cossbow.feng.parser.BaseParseTest;
import org.cossbow.feng.visit.GlobalSymbolContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

public class SemanticAnalysisTest {

    void checkTrue(String code) {
        var src = BaseParseTest.doParseFile(code);
        var ctx = new GlobalSymbolContext(src.table());
        new SemanticAnalysis(ctx).visit(src);
    }

    void checkFalse(String code) {
        try {
            checkTrue(code);
            Assertions.fail("Expect error: " + code);
        } catch (SemanticException e) {
            System.out.println("checked error: " + e.getMessage());
        }
    }

    @Test
    public void testClass0() {
        checkTrue("class A {}");
    }

    @Test
    public void testClassInherit1() {
        checkTrue("class B {} class A : B {}");
    }

    @Test
    public void testClassInherit2() {
        checkFalse("class A : B {}");
    }

    List<TypeDomain> getDomains(TypeDomain exclude) {
        return Arrays.stream(TypeDomain.values())
                .filter(d -> d.derived)
                .filter(d -> d != exclude)
                .filter(d -> d != TypeDomain.ENUM)
                .filter(d -> d != TypeDomain.ATTRIBUTE)
                .toList();
    }

    @Test
    public void testClassInherit3() {
        for (var domain : getDomains(TypeDomain.CLASS))
            checkFalse(domain + " B {} class A : B {}");
        checkFalse("enum B {WAIT,} class A : B {}");
    }

    @Test
    public void testClassImpl1() {
        checkTrue("interface I {} class A (I) {}");
    }

    @Test
    public void testClassImpl2() {
        checkFalse("class A (I) {}");
    }

    @Test
    public void testClassImpl3() {
        for (var domain : getDomains(TypeDomain.INTERFACE))
            checkFalse(domain + " I {} class A (I) {}");
        checkFalse("enum I {WAIT,} class A (I) {}");
    }


    @Test
    public void testClassField1() {
        checkTrue("class A { var id int; }");
    }

    @Test
    public void testClassField2() {
        checkFalse("class A { var id ID; }");
    }

    @Test
    public void testClassField3() {
        checkTrue("class ID{} class A { var id ID; }");
    }

    @Test
    public void testClassField4() {
        checkTrue("class ID{} class A { var id ID; func get() ID { return id; } }");
    }

    @Test
    public void testClassField5() {
        checkFalse("class ID{} class A { var id int; func get() ID { return id; } }");
    }

    @Test
    public void testClassField6() {
        checkTrue("class ID{} class A { var id ID; func set(id ID)  { this.id = id; } }");
    }

    @Test
    public void testClassField7() {
        checkFalse("class ID{} class A { var id int; func set(id ID)  { this.id = id; } }");
    }

    @Test
    public void testClassField8() {
        checkTrue("var id int = 10; class A { var id int; func get() int { return id; } }");
    }

    @Test
    public void testClassMethod1() {
        checkTrue("class A { func at() {} func test() { at(); } }");
    }

    @Test
    public void testClassMethod2() {
        checkFalse("class A { func test() { at(); } }");
    }

    @Test
    public void testClassMethod3() {
        checkTrue("func at() {} \n class A { func at() {} \n func test() { at(); } }");
    }

    @Test
    public void testClassMethod4() {
        checkTrue("func at() {} \n class A { func at() {} \n func test() { this.at(); } }");
    }

    // assign

    @Test
    public void testAssignValue1() {
        checkTrue("func f(v int) { var i int; i = int(v); }");
        checkFalse("func f(v int) { var i int; j = int(v); }");
        checkFalse("func f() { var i int = i; }");
    }

    @Test
    public void testAssignValue2() {
        checkFalse("func f(v float) { var i int; i = v; }");
    }

    @Test
    public void testAssignValue3() {
        checkTrue("func f(v float) { var i int; i = int(v); }");
    }

    @Test
    public void testAssignValue4() {
        checkFalse("func f(v bool) { var i int; i = int(v); }");
    }

    @Test
    public void testAssignValue5() {
        checkFalse("class ID{} func f(v ID) { var i int; i = int(v); }");
    }

    @Test
    public void testAssignValue6() {
        checkFalse("class ID{} func f(v int) { var i ID; i = ID(v); }");
    }

    @Test
    public void testAssignValue7() {
        checkTrue("class ID{} func f(v ID) { var i ID; i = v; }");
    }

    @Test
    public void testAssignValue8() {
        checkFalse("class A{} class B{} func f(v A) { var i B; i = v; }");
    }

    @Test
    public void testAssignValue9() {
        checkFalse("class A{} struct B{} func f(v A) { var i B; i = v; }");
        checkFalse("class A{} union B{} func f(v A) { var i B; i = v; }");
        checkFalse("class A{} enum B{S,} func f(v A) { var i B; i = v; }");
    }

    @Test
    public void testAssignValue10() {
        var def = "class A{ var a *A; }";
        checkTrue(def + "func f() { var a *A =new(A); a.a = a; }");
        checkFalse(def + "func f() { var a *A =new(A, {a=a}); }");
    }

    //

    private List<Primitive> ofKind(Primitive.Kind... kinds) {
        return Arrays.stream(Primitive.values())
                .filter(p -> {
                    for (Primitive.Kind k : kinds)
                        if (p.kind == k) return true;
                    return false;
                }).toList();
    }

    private List<BinaryOperator> ofKind(BinaryOperator.Kind kind) {
        return Arrays.stream(BinaryOperator.values())
                .filter(o -> o.kind == kind)
                .toList();
    }

    @Test
    public void testBinaryExpression1() {
        for (var bo : ofKind(BinaryOperator.Kind.MATH)) {
            for (var p : ofKind(Primitive.Kind.INTEGER, Primitive.Kind.FLOAT)) {
                var s = BaseParseTest.operatorSymbols.get(bo);
                var c = "func f(a,b %s) { var i %s; i = a %s b; }"
                        .formatted(p.code, p.code, s);
                checkTrue(c);
            }
        }
    }

    @Test
    public void testBinaryExpression2() {
        for (var bo : ofKind(BinaryOperator.Kind.BIT)) {
            for (var p : ofKind(Primitive.Kind.INTEGER)) {
                var s = BaseParseTest.operatorSymbols.get(bo);
                var c = "func f(a,b %s) { var i %s; i = a %s b; }"
                        .formatted(p, p, s);
                checkTrue(c);
            }
        }
        checkTrue("func f(a,b bool) { var i bool; i = a & b; }");
        checkTrue("func f(a,b bool) { var i bool; i = a | b; }");
    }

    @Test
    public void testBinaryExpression3() {
        for (var bo : ofKind(BinaryOperator.Kind.REL)) {
            for (var p : ofKind(Primitive.Kind.INTEGER, Primitive.Kind.FLOAT)) {
                var s = BaseParseTest.operatorSymbols.get(bo);
                var c = "func f(a,b %s) { var i bool; i = a %s b; }"
                        .formatted(p, s);
                checkTrue(c);
            }
        }
        checkTrue("func f(a,b bool) { var i bool; i = a == b; }");
        checkTrue("func f(a,b bool) { var i bool; i = a != b; }");
        checkFalse("func f(a,b bool) { var i bool; i = a < b; }");
        checkFalse("func f(a,b bool) { var i bool; i = a <= b; }");
        checkFalse("func f(a,b bool) { var i bool; i = a > b; }");
        checkFalse("func f(a,b bool) { var i bool; i = a >= b; }");
    }

    @Test
    public void testBinaryExpression4() {
        for (Primitive p : ofKind(Primitive.Kind.INTEGER, Primitive.Kind.FLOAT)) {
            checkFalse("func f(a,b %s) { var i %s; i = a && b; }".formatted(p, p));
            checkFalse("func f(a,b %s) { var i %s; i = a || b; }".formatted(p, p));
        }
        checkTrue("func f(a,b bool) { var i bool; i = a && b; }");
        checkTrue("func f(a,b bool) { var i bool; i = a || b; }");
    }

    @Test
    public void testUnaryExpression1() {
        checkTrue("func f(a bool) { var i bool; i = !a; }");
        for (var p : ofKind(Primitive.Kind.INTEGER)) {
            var c = "func f(a %s) { var i %s; i = !a; }";
            checkTrue(c.formatted(p, p));
        }
    }

    @Test
    public void testUnaryExpression2() {
        for (var p : ofKind(Primitive.Kind.INTEGER, Primitive.Kind.FLOAT)) {
            checkTrue("func f(a %s) { var i %s; i = +a; }".formatted(p, p));
            checkTrue("func f(a %s) { var i %s; i = -a; }".formatted(p, p));
        }
    }

    //

    @Test
    public void testAssignTuple1() {
        checkTrue("func f() { var i,j int = 1,2; i,j = j,i; }");
        checkTrue("func f(a,b int) { var i,j = 1,2; i,j = a-b,a+b; }");
        checkTrue("func f(a,b int) { var i int; var j bool; i,j = a-b, a<b; }");
    }

    @Test
    public void testAssignTuple2() {
        checkFalse("func f() { var i,j int = 1; }");
        checkFalse("func f() { var i,j int = 1,2,3; }");
        checkFalse("func f() { var i,j int = 1,2.5; }");
        checkFalse("func f() { var i,j int = false,2; }");
    }

    //

    @Test
    public void testConstValue1() {
        checkFalse("func f(v int) { const i int; i = v; }");
    }

    @Test
    public void testConstValue2() {
        checkFalse("func f(v int) { v = 0; }");
    }

    @Test
    public void testConstValue3() {
        checkFalse("func f(v [4]int) { v[0] = 0; }");
    }

    @Test
    public void testConstValue4() {
        checkFalse("class Dev { const id int; func f() { id = 0; } }");
    }

    @Test
    public void testConstValue5() {
        checkFalse("class Dev { var id int; } func f(d Dev) { d.id = 0; }");
    }

    @Test
    public void testConstValue6() {
        checkFalse("class Dev { var id int; } class Disk { var dev Dev; } func f(d Disk) { d.dev.id = 0; }");
    }

    @Test
    public void testConstValue7() {
        checkFalse("class Dev { var id int; } func f(d [4]Dev) { d[0].id = 0; }");
    }

    //

    @Test
    public void testReferrable1() {
        checkTrue("class ID{} func f() { var i *ID; i = new(ID); }");
    }

    @Test
    public void testReferrable2() {
        checkTrue("class ID{} func f(v *ID) { var i *ID; i = v; }");
    }

    @Test
    public void testReferrable3() {
        checkTrue("interface ID{} func f(v *ID) { var i *ID; i = v; }");
    }

    @Test
    public void testReferrable4() {
        checkTrue("func f(v *ram) { var i *ram`int`; i = v; }");
    }

    @Test
    public void testReferrable5() {
        checkFalse("func f(v *bool) {}");
        checkFalse("struct ID{} func f(v *ID) {}");
        checkFalse("union ID{} func f(v *ID) {}");
        checkFalse("enum ID{A,} func f(v *ID) {}");
        checkFalse("func ID(); func f(v *ID) {}");
    }

    @Test
    public void testTransferable1() {
        var def = "class A{} class B:A{} ";
        checkTrue(def + "func f(b *B) { var a *A = b; }");
        checkFalse(def + "func f(b *A) { var a *B = b; }");
    }

    @Test
    public void testTransferable2() {
        var def = "class A{} class B:A{} class C:B{} ";
        checkTrue(def + "func f(b *C) { var a *A = b; }");
        checkTrue(def + "func f(b *C) { var a *A = b; }");
    }

    @Test
    public void testTransferable3() {
        var def = "interface I{} class A(I){} ";
        checkTrue(def + "func f(b *A) { var a *I = b; }");
        checkFalse(def + "func f(b *I) { var a *A = b; }");
    }

    @Test
    public void testTransferable4() {
        var def = "interface I{} interface J{I;} class A(J){} ";
        checkTrue(def + "func f(b *A) { var a *I = b; }");
        checkFalse(def + "func f(b *I) { var a *A = b; }");
    }

    @Test
    public void testTransferable5() {
        var def = "interface I{} interface J{I;} ";
        checkTrue(def + "func f(b *J) { var a *I = b; }");
        checkFalse(def + "func f(b *I) { var a *J = b; }");
    }

    @Test
    public void testTransferable6() {
        var def = "interface I{} interface J{I;} interface K{J;}";
        checkTrue(def + "func f(b *K) { var a *I = b; }");
        checkFalse(def + "func f(b *I) { var a *K = b; }");
    }

    //

    @Test
    public void testDeclareArray1() {
        checkTrue("func t() { var a = [1,2,3]; }");
    }

    @Test
    public void testDeclareArray2() {
        checkTrue("func t() { var a [4]int = [1,2,3]; }");
        checkTrue("func t() { var a [4]int = [1,2,3,4]; }");
    }

    @Test
    public void testDeclareArray3() {
        checkFalse("func t() { var a [4]int = [1,2,3,4,5]; }");
    }

    @Test
    public void testDeclareArray4() {
        checkFalse("func t() { var a [*]int = [1,2,3]; }");
    }

    @Test
    public void testDeclareArray5() {
        checkFalse("func t() { var a [4]int = new([4]int); }");
    }

    @Test
    public void testDeclareArray6() {
        checkTrue("func t() { var a [*]int = new([4]int, [1,2,3]); }");
    }

    //

    @Test
    public void testGlobalVar1() {
        checkTrue("var a int;");
    }

    //

    @Test
    public void testMemMap1() {
        checkTrue("struct ID{} func f(v *ram) { var i *ram`ID`; i = v; }");
    }

    @Test
    public void testMemMap2() {
        checkFalse("class ID{} func f(v *ram) { var i *ram`ID`; i = v; }");
    }

    @Test
    public void testMemTransfer1() {
        checkTrue("func f(v *ram`uint`) { var i *rom`float` = v; }");
    }

    @Test
    public void testMemTransfer2() {
        checkFalse("func f(v *rom`uint`) { var i *ram`float` = v; }");
    }

    // struct

    @Test
    public void testStruct1() {
    }

    @Test
    public void testStructField1() {
        var def = "struct A {} union B {}";
        checkTrue(def + "struct R{ a int; b [16]int64; c [0]float; d A; e B; f[2]A; }");
    }

    @Test
    public void testStructField2() {
        checkFalse("struct R{ a bool; }");
        checkFalse("struct R{ a ram; }");
        checkFalse("struct R{ a rom; }");
        var def = "class A{} interface B{} enum C{S,} attribute D{} func E();";
        checkFalse(def + "struct R{ a A; }");
        checkFalse(def + "struct R{ a B; }");
        checkFalse(def + "struct R{ a C; }");
        checkFalse(def + "struct R{ a D; }");
        checkFalse(def + "struct R{ a E; }");
        checkFalse(def + "struct R{ a [2]A; }");
    }

    @Test
    public void testStructField3() {
        var def = "struct A {} union B {} var size = 2; ";
        checkFalse(def + "struct R{ a [-1]A; }");
        checkFalse(def + "struct R{ a [size]A; }");
        // 下面是语法错误
//        checkFalse(def + "struct R{ a *A; }");
//        checkFalse(def + "struct R{ a &A; }");
//        checkFalse(def + "struct R{ a []A; }");
    }

    // class

}
