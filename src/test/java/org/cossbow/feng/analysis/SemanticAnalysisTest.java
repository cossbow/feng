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
        System.out.println("[testing]>>>");
        System.out.println(code);
        System.out.println("<<<");
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
    public void testClass1() {
        checkTrue("class A {}");
        checkTrue("class A {} func f(a0 A, a1 *A){ var a2 A = {}; var a3 *A = new(A); }");
    }

    @Test
    public void testClass2() {
        var def = "class A { var code int; } ";
        checkTrue(def + "func f(){ var a A = {code=100}; }");
        checkTrue(def + "func f(){ var a *A = new(A,{code=100}); }");
    }

    @Test
    public void testClass3() {
        var def = "class A { const code int; } ";
        checkTrue(def + "func f(){ var a A = {code=100}; }");
        checkTrue(def + "func f(){ var a *A = new(A,{code=100}); }");
    }

    @Test
    public void testClass4() {
        var def = "class A { var code int; const type int8; } ";
        checkFalse(def + "func f(){ var a A = { code=100 }; }");
        checkFalse(def + "func f(){ var a *A = new(A,{code=100}); }");
    }

    @Test
    public void testClass5() {
        var def = "class A { const type int8; var code int; } ";
        checkFalse(def + "func f(){ var a A = {code=100}; }");
        checkFalse(def + "func f(){ var a *A = new(A,{code=100}); }");
    }

    @Test
    public void testClass6() {
        var def = "class A { const type int8; var code int; } ";
        checkFalse(def + "func f(a A){ a.type=0; }");
        checkFalse(def + "func f(a A){ a.code=100; }");
    }

    //

    List<TypeDomain> getDomains(TypeDomain exclude) {
        return Arrays.stream(TypeDomain.values())
                .filter(d -> d.derived)
                .filter(d -> d != exclude)
                .filter(d -> d != TypeDomain.ENUM)
                .filter(d -> d != TypeDomain.ATTRIBUTE)
                .toList();
    }

    @Test
    public void testClassInherit1() {
        checkTrue("class B {} class A : B {}");
        checkFalse("class A : B {}");
        for (var domain : getDomains(TypeDomain.CLASS))
            checkFalse(domain + " B {} class A : B {}");
        checkFalse("enum B {WAIT,} class A : B {}");
    }

    @Test
    public void testClassInherit2() {
        checkTrue("class A{ var id int; } class B:A{ var cn int; }");
        checkFalse("class A{ var id int; } class B:A{ var id int; }");
        checkFalse("class A{ var id int; } class B:A{ var id float; }");
    }

    @Test
    public void testClassInherit3() {
        checkTrue("class A{ var id int; } class B:A{} func f(b *B) { b.id = 0; }");
        checkTrue("class A{ func go() {} } class B:A{} func f(b *B) { b.go(); }");
    }

    @Test
    public void testClassOverride1() {
        var def = "class A{ func f() int { return 0; } } ";
        checkTrue(def + "class B:A{ func f() int { return 0; } }");
        checkFalse(def + "class B:A{ func f() int8 { return 0; } }");
    }

    @Test
    public void testClassOverride2() {
        var def = "class A{ func f() *A { return nil; } } ";
        checkTrue(def + "class B:A{ func f() *A { return nil; } }");
        checkTrue(def + "class B:A{ func f() *B { return nil; } }");
        checkFalse(def + "class B:A{ func f() int { return 0; } }");
    }

    @Test
    public void testClassOverride3() {
        var def = "class A{ func f(s int) {} } ";
        checkTrue(def + "class B:A{ func f(s int) {} }");
        checkFalse(def + "class B:A{ func f(s int8) {} }");
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
        checkFalse("class A { var id ID; }");
    }

    @Test
    public void testClassField2() {
        var def = "class ID{} ";
        checkTrue(def + "class A { var id ID; }");
        checkTrue(def + "class A { var id ID; func get() ID { return id; } }");
        checkFalse(def + "class A { var id int; func get() ID { return id; } }");
        checkTrue(def + "class A { var id ID; func set(id ID)  { this.id = id; } }");
        checkFalse(def + "class A { var id int; func set(id ID)  { this.id = id; } }");
    }

    @Test
    public void testClassField3() {
        checkTrue("var id int; class A { var id int; func get() int { return id; } }");
    }

    @Test
    public void testClassField4() {
        checkFalse("class A { var id int; } func set(a A) { a.id = 0; }");
        checkTrue("class A { var id int; } func set(a *A) { a.id = 0; }");
        checkFalse("class A { const id int; } func set(a *A) { a.id = 0; }");
    }

    @Test
    public void testClassField5() {
        checkTrue("class A { var ch [*]A; func f() { ch = new([4]A); } }");
        checkTrue("class A { var ch [*]*A; func f() { ch = new([4]*A); } }");
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
        checkTrue("func f(v uint16) { var i int32; i = int32(v); }");
        checkTrue("func f(v float) { var i int; i = int(v); }");
        checkTrue("func f() { var i int16 = int16(123); }");
        checkTrue("func f(v float) { var i int16 = 123; }");
    }

    @Test
    public void testAssignValue4() {
        checkFalse("func f(v bool) { var i uint16; i = uint16(v); }");
        checkFalse("func f(v bool) { var i int; i = float32(v); }");
        checkFalse("func f(v uint) { var i bool; i = bool(v); }");
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

    @Test
    public void testBinaryExpression1() {
        for (var bo : BinaryOperator.SetMath) {
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
        for (var bo : BinaryOperator.SetBits) {
            for (var p : ofKind(Primitive.Kind.INTEGER)) {
                var s = BaseParseTest.operatorSymbols.get(bo);
                var c = "func f(a,b %s) { var i %s; i = a %s b; }"
                        .formatted(p, p, s);
                checkTrue(c);
            }
        }
    }

    @Test
    public void testBinaryExpression3() {
        for (var bo : BinaryOperator.SetRel) {
            for (var p : ofKind(Primitive.Kind.INTEGER, Primitive.Kind.FLOAT)) {
                var s = BaseParseTest.operatorSymbols.get(bo);
                var c = "func f(a,b %s) { var i bool; i = a %s b; }"
                        .formatted(p, s);
                checkTrue(c);
            }
        }
    }

    @Test
    public void testBinaryExpression4() {
        for (var bo : BinaryOperator.SetLogic) {
            for (var p : ofKind(Primitive.Kind.BOOL)) {
                var s = BaseParseTest.operatorSymbols.get(bo);
                var c = "func f(a,b %s) { var i bool; i = a %s b; }"
                        .formatted(p, s);
                checkTrue(c);
            }
        }
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
        checkTrue("func f() { var i,j int = 1,2; }");
        checkTrue("func f() { var i,j int; i,j = 1,2; }");
        checkTrue("func f(a,b int) { var i,j int; i,j = a-b,a+b; }");
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
        checkTrue("class ID{} func f() { var i *ID; i = nil; }");
        checkTrue("class ID{} func f() { var i *ID; i = new(ID); }");
        checkTrue("class ID{} func f(v *ID) { var i *ID; i = v; }");
        checkTrue("interface ID{} func f(v *ID) { var i *ID; i = v; }");
        checkTrue("interface ID{} func f(v *ID) { var i *ID; i = nil; }");
    }

    @Test
    public void testReferrable2() {
        checkTrue("class ID{} func f(v *ID) *ID { return nil; }");
        checkTrue("class ID{} func f(v *ID) *ID { return new(ID); }");
        checkTrue("class ID{} func f(v *ID) *ID { return v; }");
        checkTrue("interface ID{} func f(v *ID) *ID { return v; }");
        checkTrue("interface ID{} func f(v *ID) *ID { return nil; }");
    }

    @Test
    public void testReferrable3() {
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
    public void testArray1() {
        checkTrue("func t() { var a = [1,2,3]; }");
        checkFalse("func t() { var a = []; }");
    }

    @Test
    public void testArray2() {
        checkTrue("func t() { var a [2]int = []; }");
        checkFalse("func t() { var a [*]int = []; }");
    }

    @Test
    public void testArray3() {
        checkFalse("func t() { var a [2]int = nil; }");
        checkTrue("func t() { var a [*]int = nil; }");
    }

    @Test
    public void testArray4() {
        checkTrue("func t() { var a [4]int = [1,2,3]; }");
        checkTrue("func t() { var a [4]int = [1,2,3,4]; }");
        checkFalse("func t() { var a [4]int = [1,2,3,4,5]; }");
    }

    @Test
    public void testArray5() {
        checkTrue("func t() { var a [*]int = new([8]int); }");
        checkTrue("func t() { var a [*]int = new([4]int, [1,2,3]); }");
        checkTrue("func t() { var a [*]int; a = new([8]int); }");
        checkTrue("func t() { var a [*]int; a = new([4]int, [1,2,3]); }");
    }

    @Test
    public void testArray6() {
        checkFalse("func t() { var a [4]int = new([4]int); }");
        checkFalse("func t() { var a [*]int = [1,2,3]; }");
        checkFalse("func t() { var a [4]int; a = new([4]int); }");
        checkFalse("func t() { var a [*]int; a = [1,2,3]; }");
    }

    @Test
    public void testArray7() {
        checkTrue("func t(s int) { var a [*]int = new([s]int); }");
        checkTrue("func t(s int) { var l = s; var a [*]int = new([l]int); }");
        checkFalse("func t() { var a [*]int = [1,2,3]; }");
        checkFalse("func t() { var a [4]int; a = new([4]int); }");
        checkFalse("func t() { var a [*]int; a = [1,2,3]; }");
    }

    @Test
    public void testArray8() {
        checkTrue("func t(s [*]int) { s[0] = s[1]; }");
        checkTrue("func t(s [*]int, i int) { s[i] = s[1]; }");
        checkFalse("func t(s int) { s[0] = s[1]; }");
        checkFalse("func t(s [4]int) { s[0] = s[1]; }");
        checkFalse("func t(s [*]int) { s[true] = 1; }");
    }

    @Test
    public void testDeclareMultiArray1() {
        checkTrue("func t() { var a [2][4]int = []; }");
        checkTrue("func t() { var a [2][4]int = [[]]; }");
        checkTrue("func t() { var a [2][4]int = [[1,2]]; }");
        checkTrue("func t() { var a [2][4]int = [[1,2,3,4]]; }");
        checkFalse("func t() { var a [2][4]int = [[1,2,3,4,5]]; }");
        checkFalse("func t() { var a [2][4]int = [[1,2],[2],[]]; }");
    }

    @Test
    public void testDeclareMultiArray2() {
        checkTrue("func t(v [4]int) { var a [2][4]int = [v]; }");
        checkFalse("func t(v [2]int) { var a [2][4]int = [v]; }");
        checkFalse("func t(v [*]int) { var a [2][4]int = [v]; }");
    }

    @Test
    public void testDeclareMultiArray3() {
        checkTrue("func t(v [*]int) { var a [2][*]int = [v]; }");
        checkFalse("func t(v [4]int) { var a [2][*]int = [v]; }");
        checkFalse("func t(v [4]int) { var a [*][4]int = [v]; }");
        checkTrue("func t(v [4]int) { var a [*][4]int = new([6][4]int, [v]); }");
    }

    //

    @Test
    public void testGlobalVar1() {
        checkTrue("var a int;");
        checkTrue("var a int = 1;");
        checkTrue("var a = 1;");
        checkFalse("const a int;");
        checkTrue("const a int = 1;");
        checkTrue("const a = 1;");
    }

    @Test
    public void testGlobalVar2() {
        checkTrue("var a int;");
        checkTrue("var a int = 1;");
        checkTrue("var a = 1;");
        checkFalse("var a float = 1;");
    }

    @Test
    public void testGlobalVar3() {
        checkTrue("const a = 1; var b = a;");
        checkFalse("var a = 1; var b = a;");
    }

    //

    @Test
    public void testMemMap1() {
        checkTrue("func f(v *ram) { var i *ram`int`; i = v; }");
        checkTrue("func f(v *ram) { var i *ram`[2]int`; i = v; }");
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
    public void testStruct0() {
        var def = "struct A {  } ";
        checkTrue(def + "func f() { var a A = {}; }");
        checkFalse(def + "func f() { var a *A; }");
        checkFalse(def + "func f() { var a = new(A); }");
    }

    @Test
    public void testStruct1() {
        var def = "struct A { id int; } ";
        checkTrue(def + "func f() { var a A = { id=123 }; }");
        checkFalse(def + "func f() { var a A = { id=true }; }");
    }

    @Test
    public void testStruct2() {
        var def = "struct A { id [4]int; } ";
        checkTrue(def + "func f() { var a A = { id=[1] }; }");
    }

    @Test
    public void testStruct3() {
        var def = "struct A { id [4]uint; } ";
        checkTrue(def + "func f() { var a A = { id=[1] }; }");
    }

    @Test
    public void testStruct6() {
        var def = "struct A { a struct{ id int; }; } ";
        checkTrue(def + "func f() { var a A = { a={id=123} }; }");
        checkFalse(def + "func f() { var a A = { a={id=true} }; }");
    }

    @Test
    public void testStructField1() {
        var def = "struct A {} union B {}";
        checkTrue(def + "struct R{ a int; b [16]int64; c [0]float; d A; e B; f[2]A; }");
        checkTrue(def + "union R{ a int; b [16]int64; c [0]float; d A; e B; f[2]A; }");
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

    @Test
    public void testStructField4() {
        checkTrue("struct R{ s struct{ id int; u union{ v int8; }; }; }");
    }

    @Test
    public void testStructField5() {
        checkTrue("struct R{ id int; } func f(r *ram`R`) { r.id = 1; }");
    }

    @Test
    public void testStructField6() {
        checkTrue("struct R{ id int; } func f() { var r R; r.id = 1; }");
        checkFalse("struct R{ id int; } func f(r R) { r.id = 1; }");
    }

    // enum

    @Test
    public void testEnum1() {
        checkTrue("enum S{A=1,}");
        checkFalse("enum S{A=3.14,}");
        checkFalse("enum S{A=false,}");
        checkFalse("enum S{A=nil,}");
        checkFalse("enum S{A=\"A\",}");
    }

    @Test
    public void testEnum2() {
        checkTrue("enum S{A,B,} func f() { var s S; s=S.A; }");
    }

    @Test
    public void testEnum3() {
        checkFalse("enum S{A,B,} func f() { var s int; s=S.A; }");
        checkFalse("enum S{A,B,} func f() { var s S; s=S.C; }");
    }

}
