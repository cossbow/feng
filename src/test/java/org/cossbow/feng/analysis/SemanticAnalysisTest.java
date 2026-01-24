package org.cossbow.feng.analysis;

import org.cossbow.feng.ast.BinaryOperator;
import org.cossbow.feng.ast.TypeDomain;
import org.cossbow.feng.ast.UnaryOperator;
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
        System.out.print("[check]>>> ");
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
        checkTrue("class A{ var id int; } class B:A{} func f(b B) { var v = b.id; }");
        checkTrue("class A{ func go() {} } class B:A{} func f(b B) { b.go(); }");
    }

    @Test
    public void testClassInherit4() {
        checkFalse("class A:B{} class B:A{}");
        checkFalse("class A:C{} class B:A{} class C:B{}");
    }

    @Test
    public void testClassInherit5() {
        checkFalse("class A{var b B;} class B:A{}");
        checkFalse("class A{var c C;} class B:A{} class C:B{}");
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
    public void testInterface1() {
        var def = new StringBuilder();
        var parts = new StringBuilder();
        for (char i = 'A'; i <= 'Z'; i++) {
            def.append("interface ").append(i).append(" {}");
            parts.append(i).append(';');
            checkTrue(def + "interface Alpha{%s}".formatted(parts));
        }
    }

    @Test
    public void testInterface2() {
        checkFalse("interface A{B;} interface B{A;}");
        checkFalse("interface A{C;} interface B{A;} interface C{B;}");
        checkFalse("interface A{C;} interface B{A;} interface C{B;}");
    }

    @Test
    public void testInterfaceMethod1() {
        checkTrue("interface A{ run(); } func f(a *A) { a.run(); }");
        checkTrue("interface A{ run(); } interface B{A;} func f(a *B) { a.run(); }");
        checkTrue("interface A{ run(); } interface B{A;} interface C{B;} func f(a *C) { a.run(); }");
    }

    @Test
    public void testInterfaceMethod2() {
        checkTrue("interface A{ f()int; } interface B{ f()int; } interface C{A;B;}");
        checkFalse("interface A{ f()int; } interface B{ f()uint; } interface C{A;B;}");
    }

    @Test
    public void testInterfaceMethod3() {
        var def = "class S{} class R:S{} ";
        checkTrue(def + "interface A{ f()S; } interface B{ f()S; } interface C{A;B;}");
        checkFalse(def + "interface A{ f()S; } interface B{ f()R; } interface C{A;B;}");
        checkTrue(def + "interface A{ f()*S; } interface B{ f()*S; } interface C{A;B;}");
        checkFalse(def + "interface A{ f()*S; } interface B{ f()*R; } interface C{A;B;}");
        checkFalse(def + "interface A{ f()*S; } interface B{ f()*R; } interface C{A;B; f()*S; }");
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
    public void testClassImpl4() {
        var def = "interface I { get() int; } ";
        checkFalse(def + "class F (I) {}");
        checkTrue(def + "class F (I) { func get() int { return 0; } }");
        checkFalse(def + "class F (I) { func get() int8 { return 0; } }");
    }

    @Test
    public void testClassImpl5() {
        var def = "class A{} class B:A{} interface I { get() *A; } ";
        checkTrue(def + "class F (I) { func get() *A { return nil; } }");
        checkTrue(def + "class F (I) { func get() *B { return nil; } }");
    }

    @Test
    public void testClassImpl6() {
        var def = "class A{} class B:A{} interface I { get(*A); } ";
        checkTrue(def + "class F (I) { func get(a *A) {} }");
        checkFalse(def + "class F (I) { func get(b *B) {} }");
    }

    @Test
    public void testClassImpl7() {
        var def = "class A{} class B:A{} interface I { get(*A, int) (*A, bool); } ";
        checkTrue(def + "class F (I) { func get(a *A, i int) (*A, bool) { return a, i>0; } }");
        checkFalse(def + "class F (I) { func get(a *A) (*A, bool) { return a, i>0; } }");
        checkFalse(def + "class F (I) { func get(a *A, i int) (*A) { return a; } }");
    }

    @Test
    public void testClassImpl8() {
        var def = "interface I { get() int; set(int); } ";
        checkTrue(def + "class F (I) { func get() int { return 0; } func set(i int) {} }");
        checkFalse(def + "class F (I) { func get() int { return 0; } }");
        checkFalse(def + "class F (I) { func set(i int) {} }");
    }

    @Test
    public void testClassImpl9() {
        var def = "interface I { get() int; } interface J { set(int); } ";
        checkTrue(def + "class F (I,J) { func get() int { return 0; } func set(i int) {} }");
        checkFalse(def + "class F (I,J) { func get() int { return 0; } }");
        checkFalse(def + "class F (I,J) { func set(i int) {} }");
    }

    @Test
    public void testClassImpl10() {
        var def = "interface I { set(int); } class P { func set(i int) {} }";
        checkTrue(def + "class F:P (I) {}");
        checkFalse(def + "class F (I) {}");
    }

    @Test
    public void testClassImpl11() {
        var def = "interface I { get() int; } interface J { set(int); } interface IJ {I;J;} ";
        checkTrue(def + "class F (IJ) { func get() int { return 0; } func set(i int) {} }");
        checkFalse(def + "class F (IJ) { func get() int { return 0; } }");
        checkFalse(def + "class F (IJ) { func set(i int) {} }");
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
    public void testClassField6() {
        checkTrue("class A{} class B{var a A;}");
        checkFalse("class A{var f B;} class B{var f A;}");
        checkFalse("class A{var f C;} class B{var f A;} class C{var f B;}");
        checkFalse("class A{var f B;} class B{var f [2]A;}");
        checkFalse("class A{var f B;} class B{var f [8][2]A;}");
        checkTrue("class A{var f B;} class B{var f [*]A;}");
        checkTrue("class A{var f B;} class B{var f [2][*]A;}");
        checkTrue("class A{var f B;} class B{var f [*][2]A;}");
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

    // prototype

    @Test
    public void testPrototype1() {
        checkTrue("func F(); func f(){} func m(){ var c F = f; }");
        checkTrue("func F(int); func f(v int){} func m(){ var c F = f; }");
        checkFalse("func F(int8); func f(v int){} func m(){ var c F = f; }");
        checkTrue("func F()int; func f()int{} func m(){ var c F = f; }");
        checkFalse("func F()int; func f()int8{} func m(){ var c F = f; }");
        checkTrue("func F()int; func m(f func()int){ var c F = f; }");
        checkFalse("func F()int; func m(f func()int8){ var c F = f; }");
    }

    @Test
    public void testPrototype2() {
        var def = "class A{} class B:A{}";
        checkTrue(def + "func F(*A); func f(*A){} func m(){ var c F = f; }");
        checkFalse(def + "func F(*A); func f(*B){} func m(){ var c F = f; }");
        checkTrue(def + "func F()*A; func f()*A{} func m(){ var c F = f; }");
        checkTrue(def + "func F()*A; func f()*B{} func m(){ var c F = f; }");
        checkTrue(def + "func F()*A; func m(f func()*A){ var c F = f; }");
        checkTrue(def + "func F()*A; func m(f func()*B){ var c F = f; }");
    }

    @Test
    public void testPrototype3() {
        checkTrue("func f(){} func m(f func(int)){ f(1); }");
        checkTrue("func F(); func m(f F){ f(); }");
        checkFalse("func f(){} func m(f int){ f(); }");
    }

    @Test
    public void testPrototype4() {
        var def = "class A{} class B:A{}";
        checkTrue(def + "func m(f func(a*A)){f(new(B));}");
        checkTrue(def + "func F(a*A); func m(f F){ f(new(B)); }");
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
                var c = "func f(a,b %s) { var i bool; i = a %s b; ;}"
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
        checkTrue(def + "func f(c *C) { var a *B = c; }");
        checkTrue(def + "func f(c *C) { var a *A = c; }");
    }

    @Test
    public void testTransferable3() {
        var def = "interface I{} class A(I){} ";
        checkTrue(def + "func f(b *A) { var a *I = b; }");
        checkFalse(def + "func f(b *I) { var a *A = b; }");
    }

    @Test
    public void testTransferable4() {
        var def = "interface I{} class A(I){} class B:A{}";
        checkTrue(def + "func f(b *B) { var i *I = b; }");
        checkFalse(def + "func f(i *I) { var b *B = i; }");
    }

    @Test
    public void testTransferable5() {
        var def = "interface I{} interface J{I;} class A(J){} ";
        checkTrue(def + "func f(b *A) { var a *I = b; }");
        checkFalse(def + "func f(b *I) { var a *A = b; }");
    }

    @Test
    public void testTransferable6() {
        var def = "interface I{} interface J{I;} ";
        checkTrue(def + "func f(b *J) { var a *I = b; }");
        checkFalse(def + "func f(b *I) { var a *J = b; }");
    }

    @Test
    public void testTransferable7() {
        var def = "interface I{} interface J{I;} interface K{J;}";
        checkTrue(def + "func f(b *K) { var a *I = b; }");
        checkFalse(def + "func f(b *I) { var a *K = b; }");
    }

    @Test
    public void testPhantomRefer1() {
        var d = "class A{} class B:A{} var a A; var b B; ";
        checkFalse(d + "const r &A = a;");
        checkTrue(d + "func f() { const r &A = a; }");
        checkTrue(d + "func f() { const r &A = b; }");
        checkTrue(d + "func f(c func(&A)) { c(a); }");
        checkTrue(d + "func f(c func(&A)) { c(b); }");
        d = "class A{} class B:A{} const a A; const b B; ";
        checkFalse(d + "const r &A = a;");
        checkFalse(d + "func f() { const r &A = a; }");
        checkFalse(d + "func f() { const r &A = b; }");
        checkFalse(d + "func f(c func(&A)) { c(a); }");
        checkFalse(d + "func f(c func(&A)) { c(b); }");
    }

    @Test
    public void testPhantomRefer2() {
        var d = "interface I{} class A(I){} class B:A{} ";
        checkTrue(d + "func f() { var a A; const r &A = a; }");
        checkTrue(d + "func f() { var b B; const r &A = b; }");
        checkTrue(d + "func f() { var a A; const r &I = a; }");
        checkTrue(d + "func f() { var b B; const r &I = b; }");

        checkTrue(d + "func f() { const a *A; const r &A = a; }");
        checkTrue(d + "func f() { const b *B; const r &A = b; }");
        checkTrue(d + "func f() { const a *A; const r &I = a; }");
        checkTrue(d + "func f() { const b *B; const r &I = b; }");

        checkFalse(d + "func f() { var a *A; const r &A = a; }");
        checkFalse(d + "func f() { var b *B; const r &A = b; }");
        checkFalse(d + "func f() { var a *A; const r &I = a; }");
        checkFalse(d + "func f() { var b *B; const r &I = b; }");

        checkFalse(d + "func f() { const a A; const r &A = a; }");
        checkFalse(d + "func f() { const b B; const r &A = b; }");
        checkFalse(d + "func f() { const a A; const r &I = a; }");
        checkFalse(d + "func f() { const b B; const r &I = b; }");
    }

    @Test
    public void testPhantomRefer3() {
        var d = "interface I{} class A(I){} class B:A{} ";
        checkTrue(d + "func f(a *A) { const r &A = a; }");
        checkTrue(d + "func f(b *B) { const r &A = b; }");
        checkTrue(d + "func f(a *A) { const r &I = a; }");
        checkTrue(d + "func f(b *B) { const r &I = b; }");

        checkFalse(d + "func f(a A) { const r &A = a; }");
        checkFalse(d + "func f(b B) { const r &A = b; }");
        checkFalse(d + "func f(a A) { const r &I = a; }");
        checkFalse(d + "func f(b B) { const r &I = b; }");
    }

    @Test
    public void testPhantomRefer4() {
        var d = "interface I{} class A(I){} class B:A{} ";
        checkTrue(d + "func f(c func(a &B)) { var a B;c(a); }");
        checkTrue(d + "func f(c func(a &A)) { var a B;c(a); }");
        checkTrue(d + "func f(c func(a &I)) { var a B;c(a); }");
        checkFalse(d + "func f(c func(a &B)) { var a *B;c(a); }");
        checkFalse(d + "func f(c func(a &A)) { var a *B;c(a); }");
        checkFalse(d + "func f(c func(a &I)) { var a *B;c(a); }");

        checkFalse(d + "func f(c func(a &B), b B) { c(b); }");
        checkFalse(d + "func f(c func(a &A), b B) { c(b); }");
        checkFalse(d + "func f(c func(a &I), b B) { c(b); }");
        checkTrue(d + "func f(c func(a &B), b *B) { c(b); }");
        checkTrue(d + "func f(c func(a &A), b *B) { c(b); }");
        checkTrue(d + "func f(c func(a &I), b *B) { c(b); }");
        checkTrue(d + "func f(c func(a &I), b *B) { c(b); }");

        checkTrue(d + "func f(c func(a &B), b &B) { c(b); }");
        checkTrue(d + "func f(c func(a &A), b &B) { c(b); }");
        checkTrue(d + "func f(c func(a &I), b &B) { c(b); }");
        checkTrue(d + "func f(c func(a &I), b &B) { c(b); }");

    }

    @Test
    public void testPhantomRefer5() {
        var d = "interface I{} class A(I){} class B:A{} class R{var b B;}";
        checkTrue(d + "func f(c func(a &B)) { var r R;c(r.b); }");
        checkTrue(d + "func f(c func(a &A)) { var r R;c(r.b); }");
        checkTrue(d + "func f(c func(a &I)) { var r R;c(r.b); }");

        checkFalse(d + "func f(c func(a &B), r R) { c(r.b); }");
        checkFalse(d + "func f(c func(a &A), r R) { c(r.b); }");
        checkFalse(d + "func f(c func(a &I), r R) { c(r.b); }");


        checkTrue(d + "func f(c func(a &B), r *R) { c(r.b); }");
        checkTrue(d + "func f(c func(a &A), r *R) { c(r.b); }");
        checkTrue(d + "func f(c func(a &I), r *R) { c(r.b); }");

        checkFalse(d + "func f(c func(a &B), r R) { c(r.b); }");
        checkFalse(d + "func f(c func(a &A), r R) { c(r.b); }");
        checkFalse(d + "func f(c func(a &I), r R) { c(r.b); }");

        checkTrue(d + "func f(c func(a &B), r &R) { c(r.b); }");
        checkTrue(d + "func f(c func(a &A), r &R) { c(r.b); }");
        checkTrue(d + "func f(c func(a &I), r &R) { c(r.b); }");

    }

    @Test
    public void testPhantomRefer6() {
        var d = "interface I{} class A(I){} class B:A{} class R{const b *B;}";
        checkTrue(d + "func f(c func(a &B)) { var r R;c(r.b); }");
        checkTrue(d + "func f(c func(a &A)) { var r R;c(r.b); }");
        checkTrue(d + "func f(c func(a &I)) { var r R;c(r.b); }");

        checkTrue(d + "func f(c func(a &B), r *R) { c(r.b); }");
        checkTrue(d + "func f(c func(a &A), r *R) { c(r.b); }");
        checkTrue(d + "func f(c func(a &I), r *R) { c(r.b); }");

        checkTrue(d + "func f(c func(a &B), r &R) { c(r.b); }");
        checkTrue(d + "func f(c func(a &A), r &R) { c(r.b); }");
        checkTrue(d + "func f(c func(a &I), r &R) { c(r.b); }");
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

    @Test
    public void testDeclareMultiArray4() {
        checkTrue("func t(v int) { var a [2][4]int; a[0][1] = v; }");
        checkFalse("func t(v int8) { var a [2][4]int; a[0][1] = v; }");
        checkTrue("func t(a [2][4]int) int { return a[0][1]; }");
    }

    //

    @Test
    public void testGlobal1() {
        checkFalse("class A{} var A int;");
    }

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
        checkFalse("var a = b; var b = 1;");
    }

    @Test
    public void testGlobalVar4() {
        checkFalse("var a = b; var b = a;");
        checkFalse("var a = c; var b = a; var c = b;");
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
    public void testMemMap3() {
        var def = "struct A{ id int; } ";
        checkFalse(def + "func f(a *A) { a.id = 1; }");
        checkTrue(def + "func f(a *ram`A`) { a.id = 1; }");
        checkFalse(def + "func f(a *rom`A`) { a.id = 1; }");
    }

    @Test
    public void testMemTransfer1() {
        checkTrue("func f(v *ram`uint`) { var i *ram`float` = v; }");
        checkTrue("func f(v *ram`uint`) { var i *rom`float` = v; }");
        checkTrue("func f(v *rom`uint`) { var i *rom`float` = v; }");
        checkFalse("func f(v *rom`uint`) { var i *ram`float` = v; }");
    }

    @Test
    public void testMemTransfer2() {
        var d = "class C{} interface I{} enum E{S,} attribute A{} ";
        checkFalse(d + "func f(v *C) { var i *ram = v; }");
        checkFalse(d + "func f(v *I) { var i *ram = v; }");
        checkFalse(d + "func f(v E) { var i *ram = v; }");
        checkFalse(d + "func f(v A) { var i *ram = v; }");
        checkFalse(d + "func f(v int) { var i *ram = v; }");
        checkFalse(d + "func f(v float) { var i *ram = v; }");
        checkFalse(d + "func f(v bool) { var i *ram = v; }");
    }

    @Test
    public void testMemTransfer3() {
        checkTrue("func f() { var i *rom = \"字面量\"; }");
        checkFalse("func f() { var i *ram = \"字面量\"; }");
        checkFalse("func f() { var i &rom = \"字面量\"; }");
        checkFalse("func f() { var i *ram = 1; }");
        checkFalse("func f() { var i *ram = 3.3; }");
        checkFalse("func f() { var i *ram = true; }");
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
        //
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
        var def = "struct R{ id int; } ";
        checkTrue(def + "func f(r *ram`R`) { r.id = 1; }");
        checkFalse(def + "func f(r *rom`R`) { r.id = 1; }");
        checkTrue(def + "func f(r *ram`R`) { var v = r.id; }");
        checkTrue(def + "func f() { var r R; r.id = 1; }");
        checkFalse(def + "func f(r R) { r.id = 1; }");
    }

    @Test
    public void testStructField6() {
        checkTrue("struct A{} struct B{a A;}");
        checkFalse("struct A{f B;} struct B{f A;}");
        checkFalse("struct A{f C;} struct B{f A;} struct C{f B;}");
        checkFalse("struct A{f B;} struct B{f [2]A;}");
        checkFalse("struct A{f B;} struct B{f [2][4]A;}");
    }

    //


    // enum

    @Test
    public void testEnum1() {
        checkTrue("enum S{A=1,}");
        checkTrue("enum S{A,B=9,}");
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

    // statement

    @Test
    public void testStatementDeclaration() {
        checkTrue("func f() { var a,b,c int; }");
        checkTrue("func f() { var a,b,c int = 1,2,3; }");
        checkFalse("func f() { var a,b,c int = 1,2; }");
        checkFalse("func f() { var a,b,c int = 1,2,3,4; }");
        checkFalse("func f() { var a,b,c bool = 1,2,3; }");
        checkTrue("func f() { var a,b,c = 1,2,3; }");
    }

    @Test
    public void testStatementAssignment() {
        var def = "class A{ var id int; }";
        checkTrue(def + "func f(a *A) { var v int; v,a.id = 1,2; }");
        checkFalse(def + "func f(a *A) { var v int; v,a.id = 1; }");
        checkFalse(def + "func f(a *A) { var v int; v,a.id = 1,2,3; }");
        checkTrue(def + "func f(a *A, r [*]int) { var v int; v,a.id,r[2] = 1,2,3; }");
    }

    @Test
    public void testStatementScope() {
        checkFalse("func f(v int) { var v int; }");
        checkTrue("func f() { var v int; { var v int; } }");
        checkTrue("func f(v int) { { var v int; } }");
        checkTrue("func f(v int) { {var v int;} }");
        checkTrue("func f(v int) { {var v int; {var v int;}} }");
    }

    @Test
    public void testStatementIf() {
        checkTrue("func f(v bool){if(v){var v int;}}");
        checkTrue("func f(v int){if(v>0){var v int;}}");
        checkFalse("func f(v int){if(var v int=0;v>0){var v int;}}");
        checkTrue("func f(v int){if(var v int=0;v>0){{var v int;}}}");
        checkTrue("func f(v int,a func()){if(v<0)a();else a();}");
        checkTrue("func f(v int,a func()){if(v<0){a();}else{a();}}");
        checkFalse("func f(v int){if(v){var v int;}}");
        checkFalse("func f(v float){if(v){var v int;}}");
        checkFalse("func f(v *rom){if(v){var v int;}}");
    }

    @Test
    public void testStatementSwitch() {
        var d = "func a(){} enum E{U,V,W,} const S1K = 1<<10; var S1M = 1<<20;";
        checkTrue(d + "func f(v int){switch(v){ case 1{} default{} }}");
        checkTrue(d + "func f(v int){switch(v){ case S1K{} default{} }}");
        checkFalse(d + "func f(v int){switch(v){ case S1M{} default{} }}");
        checkFalse(d + "func f(v int){ const K1 = S1K; switch(v){ case K1{} default{} }}");
        checkFalse(d + "func f(v int, c int){switch(v){ case c{} }}");
        checkTrue(d + "func f(v int){switch(v){ case 1{} case 2{} default{} }}");
        checkFalse(d + "func f(v int){switch(v){ case 1{} }}");
        checkTrue(d + "func f(v E){switch(v){ case U{} case V{} case W{} }}");
        checkTrue(d + "func f(v E){switch(v){ case U{} default{} }}");
        checkFalse(d + "func f(v E){switch(v){ case U{} }}");
        checkTrue(d + "func f(v E){switch(v){ case U{var v E;} default{var v E;}  }}");
    }

    @Test
    public void testStatementThrow() {
        var d = "func a(){} enum E{U,V,W,} const S1K = 1<<10; var S1M = 1<<20;";
        checkTrue(d + "func f(v E){ throw v; }");
    }

    @Test
    public void testStatementTry() {
        var d = "func A(); enum E{U,V,W,} class C{} interface I{}";
        checkTrue(d + "func f(v E){ try{}finally{} }");
        checkTrue(d + "func f(v E){ try{var v E;}finally{} }");
        checkTrue(d + "func f(v E){ try{}finally{var v E;} }");
        checkTrue(d + "func f(v E){ try{}catch(v E){} }");
        checkTrue(d + "func f(v E){ try{var v E;}catch(v E){} }");
        checkFalse(d + "func f(v E){ try{}catch(v E){var v E;} }");
    }

}
