package org.cossbow.feng.analysis;

import org.cossbow.feng.ast.BinaryOperator;
import org.cossbow.feng.ast.TypeDomain;
import org.cossbow.feng.ast.UnaryOperator;
import org.cossbow.feng.ast.dcl.Primitive;
import org.cossbow.feng.err.SemanticException;
import org.cossbow.feng.parser.BaseParseTest;
import org.cossbow.feng.parser.SampleParseTest;
import org.cossbow.feng.visit.GlobalSymbolContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.List;

public class SemanticAnalysisTest {

    void checkSucc(String code) {
        System.out.print("[check]>>> ");
        System.out.println(code);
        System.out.println("<<<");
        var src = BaseParseTest.doParseFile(code);
        var ctx = new GlobalSymbolContext(src.table());
        new SemanticAnalysis(ctx).visit(src);
    }

    void checkFail(String code) {
        try {
            checkSucc(code);
            Assertions.fail("Expect error: " + code);
        } catch (SemanticException e) {
            System.out.println("checked error: " + e.getMessage());
        }
    }

    @Test
    public void testClass1() {
        checkSucc("class A {}");
        checkSucc("class A {} func f(a0 A, a1 *A){ var a2 A = {}; var a3 *A = new(A); }");
    }

    @Test
    public void testClass2() {
        var def = "class A { var code int; } ";
        checkSucc(def + "func f(){ var a A = {code=100}; }");
        checkSucc(def + "func f(){ var a *A = new(A,{code=100}); }");
    }

    @Test
    public void testClass3() {
        var def = "class A { const code int; } ";
        checkSucc(def + "func f(){ var a A = {code=100}; }");
        checkSucc(def + "func f(){ var a *A = new(A,{code=100}); }");
    }

    @Test
    public void testClass4() {
        var def = "class A { var code int; const type int8; } ";
        checkFail(def + "func f(){ var a A = { code=100 }; }");
        checkFail(def + "func f(){ var a *A = new(A,{code=100}); }");
    }

    @Test
    public void testClass5() {
        var def = "class A { const type int8; var code int; } ";
        checkFail(def + "func f(){ var a A = {code=100}; }");
        checkFail(def + "func f(){ var a *A = new(A,{code=100}); }");
    }

    @Test
    public void testClass6() {
        var def = "class A { const type int8; var code int; } ";
        checkFail(def + "func f(a A){ a.type=0; }");
        checkFail(def + "func f(a A){ a.code=100; }");
    }

    //

    List<TypeDomain> getDomains(TypeDomain exclude) {
        return Arrays.stream(TypeDomain.values())
                .filter(d -> d.custom)
                .filter(d -> d != exclude)
                .filter(d -> d != TypeDomain.ENUM)
                .filter(d -> d != TypeDomain.ATTRIBUTE)
                .toList();
    }

    @Test
    public void testClassInherit1() {
        checkSucc("class B {} class A : B {}");
        checkFail("class A : B {}");
        for (var domain : getDomains(TypeDomain.CLASS))
            checkFail(domain + " B {} class A : B {}");
        checkFail("enum B {WAIT,} class A : B {}");
    }

    @Test
    public void testClassInherit2() {
        checkSucc("class A{ var id int; } class B:A{ var cn int; }");
        checkFail("class A{ var id int; } class B:A{ var id int; }");
        checkFail("class A{ var id int; } class B:A{ var id float; }");
    }

    @Test
    public void testClassInherit3() {
        checkSucc("class A{ var id int; } class B:A{} func f(b *B) { b.id = 0; }");
        checkSucc("class A{ func go() {} } class B:A{} func f(b *B) { b.go(); }");
        checkSucc("class A{ var id int; } class B:A{} func f(b B) { var v = b.id; }");
        checkSucc("class A{ func go() {} } class B:A{} func f(b B) { b.go(); }");
    }

    @Test
    public void testClassInherit4() {
        checkFail("class A:B{} class B:A{}");
        checkFail("class A:C{} class B:A{} class C:B{}");
    }

    @Test
    public void testtestClassInherit5() {
        checkSucc("class A {} class B:A{}");
        checkFail("class A final {} class B:A{}");

        checkSucc("class A {} func f(a *A){var o *Object = a;}");
        checkFail("class A final {} func f(a *A){var o *Object = a;}");
    }

    @Test
    public void testClassInherit5() {
        checkFail("class A{var b A;}");
        checkFail("class A{var b B;} class B:A{}");
        checkFail("class A{var c C;} class B:A{} class C:B{}");
    }

    @Test
    public void testClassOverride1() {
        var def = "class A{ func f() int { return 0; } } ";
        checkSucc(def + "class B:A{ func f() int { return 0; } }");
        checkFail(def + "class B:A{ func f() int8 { return 0; } }");
    }

    @Test
    public void testClassOverride2() {
        var def = "class A{ func f() *A { return nil; } } ";
        checkSucc(def + "class B:A{ func f() *A { return nil; } }");
        checkSucc(def + "class B:A{ func f() *B { return nil; } }");
        checkFail(def + "class B:A{ func f() int { return 0; } }");
    }

    @Test
    public void testClassOverride3() {
        var def = "class A{ func f(s int) {} } ";
        checkSucc(def + "class B:A{ func f(s int) {} }");
        checkFail(def + "class B:A{ func f(s int8) {} }");
    }

    @Test
    public void testClassOverride4() {
        var def = "class A{ export func f(s int) {} } ";
        checkSucc(def + "class B:A{ export func f(s int) {} }");
        checkFail(def + "class B:A{ func f(s int8) {} }");
    }

    @Test
    public void testClassOverride5() {
        var def = "class A{ func f(s [2]int) {} } ";
        checkSucc(def + "class B:A{ func f(s [2]int) {} }");
        checkFail(def + "class B:A{ func f(s [3]int) {} }");
    }

    @Test
    public void testClassOverride6() {
        var def = "class A{ func f() [2]int {return [];} } ";
        checkSucc(def + "class B:A{ func f() [2]int {return [];} }");
        checkFail(def + "class B:A{ func f() [3]int {return [];} }");
    }

    @Test
    public void testClassResource1() {
        var def = "class A{func release(){}} ";
        checkSucc(def + "func f(a *A) {}");
        checkFail(def + "func f(a A) {}");
    }

    @Test
    public void testThis1() {
        checkSucc("class A{ var id int; func m(){ this.id = 0; } }");
        checkSucc("class A{ func f() {} func m(){ this.f(); } }");
        checkFail("class A{ func f() {} func m(){ this.g(); } }");
        checkSucc("class B{func f(){}} class A:B{ func f(){} func m(){ this.f(); } }");
        checkSucc("class B{func f(){}} class A:B{ func f(){} func m(){ super.f(); } }");
        checkFail("class B{func f(){}} class A:B{ func f(){} func m(){ super.g(); } }");
    }

    @Test
    public void testThis2() {
        checkSucc("class A{ func m(){ var a A; a = this; } }");
        checkSucc("class A{ func m(){ const a &A = this; } }");
        checkFail("class A{ func m(){ const a *A = this; } }");
        checkFail("class A{ func m(){ var b B = this; } } class B{}");
    }

    @Test
    public void testThis3() {
        var d = "class A{var id int; func m()this{}} ";
        // TODO: return this
//        checkSucc(d + "func f(a *A){var id = a.m().id;}");
    }

    //

    @Test
    public void testInterface1() {
        checkSucc("interface A{} interface B{A;}");
        checkFail("class A{} interface B{A;}");
        checkFail("enum A{S,} interface B{A;}");
        checkFail("struct A{} interface B{A;}");
    }

    @Test
    public void testInterface2() {
        checkFail("interface A{B;} interface B{A;}");
        checkFail("interface A{C;} interface B{A;} interface C{B;}");
        checkFail("interface A{C;} interface B{A;} interface C{B;}");
    }

    @Test
    public void testInterfaceMethod1() {
//        checkSucc("interface A{ run(); } func f(a *A) { a.run(); }");
        checkSucc("interface A{ run(); } interface B{A;} func f(a *B) { a.run(); }");
//        checkSucc("interface A{ run(); } interface B{A;} interface C{B;} func f(a *C) { a.run(); }");
    }

    @Test
    public void testInterfaceMethod2() {
        checkSucc("interface A{ f()int; } interface B{ f()int; } interface C{A;B;}");
        checkFail("interface A{ f()int; } interface B{ f()uint; } interface C{A;B;}");
    }

    @Test
    public void testInterfaceMethod3() {
        var def = "class S{} class R:S{} ";
        checkSucc(def + "interface A{ f()S; } interface B{ f()S; } interface C{A;B;}");
        checkFail(def + "interface A{ f()S; } interface B{ f()R; } interface C{A;B;}");
        checkSucc(def + "interface A{ f()*S; } interface B{ f()*S; } interface C{A;B;}");
        checkFail(def + "interface A{ f()*S; } interface B{ f()*R; } interface C{A;B;}");
        checkFail(def + "interface A{ f()*S; } interface B{ f()*R; } interface C{A;B; f()*S; }");
    }

    @Test
    public void testClassImpl1() {
        checkSucc("interface I {} class A (I) {}");
    }

    @Test
    public void testClassImpl2() {
        checkFail("class A (I) {}");
    }

    @Test
    public void testClassImpl3() {
        for (var domain : getDomains(TypeDomain.INTERFACE))
            checkFail(domain + " I {} class A (I) {}");
        checkFail("enum I {WAIT,} class A (I) {}");
    }

    @Test
    public void testClassImpl4() {
        var d = "interface I { get() int; } ";
        checkFail(d + "class F (I) {}");
        checkSucc(d + "class F (I) { func get() int { return 0; } }");
        checkFail(d + "class F (I) { func get() int8 { return 0; } }");

        d = "interface I { get() [2]int; } ";
        checkSucc(d + "class F (I) { func get() [2]int { return []; } }");
        checkFail(d + "class F (I) { func get() [3]int { return []; } }");

        d = "interface I { get([2]int); } ";
        checkSucc(d + "class F (I) { func get([2]int) {} }");
        checkFail(d + "class F (I) { func get([3]int) {} }");
    }

    @Test
    public void testClassImpl5() {
        var def = "class A{} class B:A{} interface I { get() *A; } ";
        checkSucc(def + "class F (I) { func get() *A { return nil; } }");
        checkSucc(def + "class F (I) { func get() *B { return nil; } }");
    }

    @Test
    public void testClassImpl6() {
        var def = "class A{} class B:A{} interface I { get(*A); } ";
        checkSucc(def + "class F (I) { func get(a *A) {} }");
        checkFail(def + "class F (I) { func get(b *B) {} }");
    }

    @Test
    public void testClassImpl7() {
        var def = "class A{} class B:A{} interface I { get(*A, int) *A; } ";
        checkSucc(def + "class F (I) { func get(a *A, i int) *A { return a; } }");
        checkFail(def + "class F (I) { func get(a *A) *A { return a; } }");
        checkFail(def + "class F (I) { func get(a *A, i int) int { return i; } }");
    }

    @Test
    public void testClassImpl8() {
        var def = "interface I { get() int; set(int); } ";
        checkSucc(def + "class F (I) { func get() int { return 0; } func set(i int) {} }");
        checkFail(def + "class F (I) { func get() int { return 0; } }");
        checkFail(def + "class F (I) { func set(i int) {} }");
    }

    @Test
    public void testClassImpl9() {
        var def = "interface I { get() int; } interface J { set(int); } ";
        checkSucc(def + "class F (I,J) { func get() int { return 0; } func set(i int) {} }");
        checkFail(def + "class F (I,J) { func get() int { return 0; } }");
        checkFail(def + "class F (I,J) { func set(i int) {} }");
    }

    @Test
    public void testClassImpl10() {
        var def = "interface I { set(int); } class P { func set(i int) {} }";
        checkSucc(def + "class F:P (I) {}");
        checkFail(def + "class F (I) {}");
    }

    @Test
    public void testClassImpl11() {
        var def = "interface I { get() int; } interface J { set(int); } interface IJ {I;J;} ";
        checkSucc(def + "class F (IJ) { func get() int { return 0; } func set(i int) {} }");
        checkFail(def + "class F (IJ) { func get() int { return 0; } }");
        checkFail(def + "class F (IJ) { func set(i int) {} }");
    }

    @Test
    public void testClassField1() {
        checkSucc("class A { var id int; }");
        checkFail("class A { var id ID; }");
    }

    @Test
    public void testClassField2() {
        var def = "class ID{} ";
        checkSucc(def + "class A { var id ID; }");
        checkSucc(def + "class A { var id ID; func get() ID { return id; } }");
        checkFail(def + "class A { var id int; func get() ID { return id; } }");
        checkSucc(def + "class A { var id ID; func set(id ID)  { this.id = id; } }");
        checkFail(def + "class A { var id int; func set(id ID)  { this.id = id; } }");
        checkFail(def + "class A { var id int; func set(id ID)  { this.type = id; } }");
    }

    @Test
    public void testClassField3() {
        checkSucc("var id int; class A { var id int; func get() int { return id; } }");
    }

    @Test
    public void testClassField4() {
        checkFail("class A { var id int; } func set(a A) { a.id = 0; }");
        checkSucc("class A { var id int; } func set(a *A) { a.id = 0; }");
        checkFail("class A { const id int; } func set(a *A) { a.id = 0; }");
    }

    @Test
    public void testClassField5() {
        checkSucc("class A { var ch [*]A; func f() { ch = new([4]A); } }");
        checkSucc("class A { var ch [*]*A; func f() { ch = new([4]*A); } }");
    }

    @Test
    public void testClassField6() {
        checkSucc("class A{} class B{var a A;}");
        checkFail("class A{var f B;} class B{var f A;}");
        checkFail("class A{var f C;} class B{var f A;} class C{var f B;}");
        checkFail("class A{var f B;} class B{var f [2]A;}");
        checkFail("class A{var f B;} class B{var f [8][2]A;}");
        checkSucc("class A{var f B;} class B{var f [*]A;}");
        checkSucc("class A{var f B;} class B{var f [2][*]A;}");
        checkSucc("class A{var f B;} class B{var f [*][2]A;}");
    }

    @Test
    public void testClassField7() {
        checkFail("class A{var b B;} class B:A{}");
        checkFail("class A{var b [6]B;} class B:A{}");
        checkSucc("class A{var b [*]B;} class B:A{}");

        checkFail("class A{var c C;} class B:A{} class C:B{}");
        checkFail("class A{var c [3]C;} class B:A{} class C:B{}");
        checkSucc("class A{var c [*]C;} class B:A{} class C:B{}");

        checkFail("class A{var d D;} class B:A{} class C:B{} class D{var c C;}");
        checkFail("class A{var d [3]D;} class B:A{} class C:B{} class D{var c C;}");
    }

    @Test
    public void testClassMethod1() {
        checkSucc("class A { func at() {} func test() { at(); } }");
    }

    @Test
    public void testClassMethod2() {
        checkFail("class A { func test() { at(); } }");
    }

    @Test
    public void testClassMethod3() {
        checkSucc("func at() {} \n class A { func at() {} \n func test() { at(); } }");
    }

    @Test
    public void testClassMethod4() {
        checkSucc("func at() {} \n class A { func at() {} \n func test() { this.at(); } }");
    }

    // prototype

    @Test
    public void testPrototype1() {
        checkSucc("func F(); func f(){} func m(){ var c F = f; }");
        checkSucc("func F(int); func f(v int){} func m(){ var c F = f; }");
        checkFail("func F(int8); func f(v int){} func m(){ var c F = f; }");
        checkSucc("func F()int; func f()int{return 1;} func m(){ var c F = f; }");
        checkFail("func F()int; func f()int8{} func m(){ var c F = f; }");
        checkSucc("func F()int; func m(f func()int){ var c F = f; }");
        checkFail("func F()int; func m(f func()int8){ var c F = f; }");

        checkSucc("func F()int; func m(f F){ var c func()int = f; }");
        checkFail("func F()int; func G()int; func m(f F){ var c G = f; }");
    }

    @Test
    public void testPrototype2() {
        var def = "class A{} class B:A{} ";
        checkSucc(def + "func F(*A); func f(*A){} func m(){ var c F = f; }");
        checkFail(def + "func F(*A); func f(*B){} func m(){ var c F = f; }");
        checkSucc(def + "func F()*A; func f()*A{return nil;} func m(){ var c F = f; }");
        checkSucc(def + "func F()*A; func f()*B{return nil;} func m(){ var c F = f; }");
        checkSucc(def + "func F()*A; func m(f func()*A){ var c F = f; }");
        checkSucc(def + "func F()*A; func m(f func()*B){ var c F = f; }");
    }

    @Test
    public void testPrototype3() {
        checkSucc("func f(){} func m(f func(int)){ f(1); }");
        checkSucc("func F(); func m(f F){ f(); }");
        checkSucc("func f(){} func m(f int){ f(); }");
    }

    @Test
    public void testPrototype4() {
        var def = "class A{} class B:A{}";
        checkSucc(def + "func m(f func(a*A)){f(new(B));}");
        checkSucc(def + "func F(a*A); func m(f F){ f(new(B)); }");
    }

    @Test
    public void testPrototype5() {
        var def = "class A{} class B:A{} ";
        checkSucc(def + "func m(f func(a*A)){ var c func(*A); c=f; }");
        checkFail(def + "func m(f func(a*B)){ var c func(*A); c=f; }");
        checkFail(def + "func m(f func(a*A)){ var c func(*B); c=f; }");
        checkFail(def + "func f(a func()[*]*C){var v func()[*]*P = a;}");
    }

    @Test
    public void testPrototype6() {
        var def = "func B(); func C1(); func C2(B); func C3(func()); ";
        checkSucc(def + "func f(a func()){var c C1 = a;}");
        checkSucc(def + "func f(a func(B)){var c C2 = a;}");
        checkSucc(def + "func f(a func(func())){var c C2 = a;}");
        checkSucc(def + "func f(a func(B)){var c C3 = a;}");
    }

    // assign

    @Test
    public void testAssignValue1() {
        checkSucc("func f(v int) { var i int; i = int(v); }");
        checkFail("func f(v int) { var i int; j = int(v); }");
        checkFail("func f() { var i int = i; }");
    }

    @Test
    public void testAssignValue2() {
        checkFail("func f(v int, u int8) { var x,y = int(v,u); }");
        checkFail("class A{} func f(v int) { var x = A(v); }");
    }

    @Test
    public void testAssignValue3() {
        checkFail("func f(v float) { var i int; i = v; }");
        checkSucc("func f(v float) { var i int; i = int(v); }");
        checkSucc("func f(v uint16) { var i int32; i = int32(v); }");
        checkSucc("func f(v float) { var i int; i = int(v); }");
        checkSucc("func f() { var i int16 = int16(123); }");
        checkSucc("func f(v float) { var i int16 = 123; }");
        checkSucc("func f() { var i = bool(false); }");
        checkFail("func f() { var i = bool(1); }");
    }

    @Test
    public void testAssignValue4() {
        checkFail("func f(v bool) { var i uint16; i = uint16(v); }");
        checkFail("func f(v bool) { var i int; i = float32(v); }");
        checkFail("func f(v uint) { var i bool; i = bool(v); }");
    }

    @Test
    public void testAssignValue5() {
        checkFail("class ID{} func f(v ID) { var i int; i = int(v); }");
        checkFail("class ID{} func f(v int) { var i ID; i = ID(v); }");
        checkSucc("class ID{} func f(v ID) { var i ID; i = v; }");
        checkFail("class A{} class B{} func f(v A) { var i B; i = v; }");
    }

    @Test
    public void testAssignValue6() {
        checkFail("class A{} struct B{} func f(v A) { var i B; i = v; }");
        checkFail("class A{} union B{} func f(v A) { var i B; i = v; }");
        checkFail("class A{} enum B{S,} func f(v A) { var i B; i = v; }");
    }

    @Test
    public void testAssignValue7() {
        var def = "class A{ var a *A; } ";
        checkSucc(def + "func f() { var a *A =new(A); a.a = a; }");
        checkFail(def + "func f() { var a *A =new(A, {a=a}); }");
    }

    @Test
    public void testAssignValue8() {
        var d = "class A{var v1 int; const v2 int;} ";
        checkSucc(d + "func f(o *Object){ o?(*A).v1 = 0; }");
        checkFail(d + "func f(o *Object){ o?(*A).v2 = 0; }");

        checkSucc(d + "func f(){ var ar [2]A; ar[0].v1 = 0; }");
        checkFail(d + "func f(){ const ar [2]A; ar[0].v1 = 0; }");

        checkSucc(d + "func f(g func()*A){ g().v1 = 0; }");
        checkFail(d + "func f(g func()A){ g().v1 = 0; }");

    }

    @Test
    public void testAssignValue9() {
        checkSucc("func r()int{return 0;} func f(){var i int = r();}");
        checkFail("func r() {} func f(){var i int = r();}");
    }

    // variable

    @Test
    public void testVariable1() {
        checkSucc("func f(){ var i int; i = 0; }");
        checkSucc("func f(){ var i int; {i = 0;} }");
        checkFail("func f(){ var i int; j = 0; }");
    }

    @Test
    public void testVariable2() {
        checkSucc("func f(){ const i int = 0; }");
        checkFail("func f(){ const i int; }");
        checkFail("func f(){ const i int = 0; i = 1; }");
        /* 语法检查过滤了 */
//         checkSucc("func f(){ if(const i int; true){} }");
//         checkSucc("func f(){ switch(const i int; i){} }");
//         checkSucc("func f(){ for(const i int; true; i +=1){} }");
    }

    @Test
    public void testVariable3() {
        var d = "class A{} ";
        checkSucc(d + "func f(v1 *A){ const v2=v1; }");
        checkSucc(d + "func f(g func()*A){ const v1=g(); const v2=v1; }");
        checkSucc(d + "func f(){ var v1 *A = nil; const v2=v1; }");
        checkFail(d + "func f(){ const v1 *A; }");
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
                checkSucc(c);
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
                checkSucc(c);
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
                checkSucc(c);
            }
        }
    }

    @Test
    public void testBinaryExpression4() {
        for (var bo : BinaryOperator.SetLogic) {
            for (var p : ofKind(Primitive.Kind.BOOL)) {
                var s = BaseParseTest.operatorSymbols.get(bo);
                var c = "func f(a,b %s) { var i bool; i = a %s b;}"
                        .formatted(p, s);
                checkSucc(c);
            }
        }
    }

    @Test
    public void testBinaryExpression5() {
        checkSucc("func f(a,b int){var c=a*(b+3);}");
    }

    @Test
    public void testBinaryExpression6() {
        var d = "class A{} class B:A{} class C{} struct S{} struct T{}";
        checkSucc(d + "func f(a *A, b *B) {var r = a==b;}");
        checkSucc(d + "func f(a *A, b &B) {var r = a==b;}");
        checkFail(d + "func f(a *A, b *C) {var r = a==b;}");
        checkSucc(d + "func f(a *S, b *T) {var r = a==b;}");
        checkSucc(d + "func f(a S, b &T) {var r = a==b;}");

        checkSucc(d + "func f(a[2]int,b[2]int){var r = a==b;}");
        checkFail(d + "func f(a[2]int,b[3]int){var r = a==b;}");
        checkSucc(d + "func f(a[*]int,b[*]int){var r = a==b;}");
        checkSucc(d + "func f(a[*]int,b[&]int){var r = a==b;}");

        checkSucc(d + "func f(a[2]A,b[2]A){var r = a==b;}");
        checkFail(d + "func f(a[2]A,b[3]A){var r = a==b;}");
        checkSucc(d + "func f(a[*]A,b[*]A){var r = a==b;}");
        checkSucc(d + "func f(a[*]A,b[&]A){var r = a==b;}");

        checkFail(d + "func f(a[2]A,b[2]B){var r = a==b;}");
        checkFail(d + "func f(a[2]A,b[3]B){var r = a==b;}");
        checkFail(d + "func f(a[*]A,b[*]B){var r = a==b;}");
        checkFail(d + "func f(a[*]A,b[&]B){var r = a==b;}");

    }

    @Test
    public void testBinaryExpression7() {
        checkSucc("func f(){ var r = nil == nil ; }");
        checkSucc("func f(a*int){ var r = nil == a ; }");
        checkSucc("func f(a*int){ var r = a == nil ; }");
        checkSucc("func f(){ var r = nil != nil ; }");
        checkSucc("func f(a*int){ var r = nil != a ; }");
        checkSucc("func f(a*int){ var r = a != nil ; }");
        checkFail("func f(){ var r = nil < nil ; }");
        checkFail("func f(a*int){ var r = nil < a ; }");
        checkFail("func f(a*int){ var r = a < nil ; }");
    }

    @Test
    public void testBinaryExpression8() {
        var d = "interface I{} class A(I){} class B{} ";
        checkSucc(d + "func f(x*I,y*A)bool{return x==y;}");
        checkFail(d + "func f(x*I,y*B)bool{return x==y;}");
    }

    @Test
    public void testUnaryExpression1() {
        checkSucc("func f(){ var a int = +10; }");
        checkSucc("func f(){ var a int = -10; }");
        checkSucc("func f(){ var a int = !10; }");
        checkSucc("func f(){ var a float = +1.3; }");
        checkSucc("func f(){ var a float = -1.3; }");
        checkFail("func f(){ var a float = !1.3; }");
        checkSucc("func f(){ var a bool = 1 >0; }");
        checkFail("func f(){ var a float= !1.3; }");
        checkSucc("func f(){ var a bool = !true; }");

        checkSucc("func f(){ const i = 12; var a int = +i; }");
        checkSucc("func f(){ const i = 12; var a int = -i; }");
        checkSucc("func f(){ const i = 12; var a int = !i; }");
        checkSucc("func f(){ const i = 12; var a bool = 1 >i; }");
        checkFail("func f(){ const i = 1.2; var a = !i; }");

        checkSucc("func f(a int){ var v = +a; }");
        checkSucc("func f(a int){ var v = -a; }");
        checkSucc("func f(a int){ var v = !a; }");

        checkSucc("func f(a float){ var v = +a; }");
        checkSucc("func f(a float){ var v = -a; }");
        checkFail("func f(a float){ var v = !a; }");

        checkFail("func f(a bool){ var v = +a; }");
        checkFail("func f(a bool){ var v = -a; }");
        checkSucc("func f(a bool){ var v = !a; }");

    }

    @Test
    public void testUnaryExpression2() {
        checkSucc("func f(a bool) { var i bool; i = !a; }");
        for (var p : ofKind(Primitive.Kind.INTEGER)) {
            var c = "func f(a %s) { var i %s; i = !a; }";
            checkSucc(c.formatted(p, p));
        }
    }

    @Test
    public void testUnaryExpression3() {
        for (var p : ofKind(Primitive.Kind.INTEGER, Primitive.Kind.FLOAT)) {
            checkSucc("func f(a %s) { var i %s; i = +a; }".formatted(p, p));
            checkSucc("func f(a %s) { var i %s; i = -a; }".formatted(p, p));
        }
    }

    @Test
    public void testAssertExpression0() {
        var d = "interface I{} struct S{} class C{}";
        checkFail(d + "func f(s *Object){ var e = s?(I); }");
        checkFail(d + "func f(s *Object){ var e = s?(S); }");
        checkFail(d + "func f(s *Object){ var e = s?(C); }");
        checkFail(d + "func f(s *Object){ var e = s?(int); }");
        checkFail(d + "func f(s *Object){ var e = s?(bool); }");
        checkFail(d + "func f(s *Object){ var e = s?([1]B); }");
    }

    @Test
    public void testAssertExpression1() {
        var d = "class A{} class B:A{} class C{}";
        checkSucc(d + "func f(s *Object){ var e = s?(*A); }");
        checkSucc(d + "func f(s *A){ var e = s?(*A); }");
        checkSucc(d + "func f(s *B){ var e = s?(*A); }");
        checkFail(d + "func f(s *C){ var e = s?(*A); }");
    }

    @Test
    public void testAssertExpression2() {
        var d = "interface A{} class B(A){} class C{}";
        checkSucc(d + "func f(s *B){ var e = s?(*A); }");
        checkSucc(d + "func f(s *C){ var e = s?(*A); }");
        checkFail(d + "func f(s B){ var e = s?(*A); }");
        checkFail(d + "func f(s *B){ var e = s?(*int); }");
        checkFail(d + "func f(s *B){ var e = s?(*bool); }");
        checkFail(d + "func f(s *B){ var e = s?([*]B); }");
    }

    @Test
    public void testAssertExpression3() {
        var d = "interface A{} interface B{A;} interface C{}";
        checkSucc(d + "func f(s *B){ var e = s?(*A); }");
        checkSucc(d + "func f(s *C){ var e = s?(*A); }");
    }

    @Test
    public void testAssertExpression4() {
        var d = "interface A{} class B(A){} class C{}";
        checkSucc(d + "func f(s*B){ var e = s?(*A); }");
        checkFail(d + "func f(s*B){ var e = s?(*C); }");
    }

    @Test
    public void testAssertExpression5() {
        checkFail("func f(s *Object){ var e = s?(int); }");
        checkFail("func f(s *Object){ var e = s?(*int); }");
        var d = "enum E{S,T,} struct S{} func F();";
        checkFail(d + "func f(s *Object){ var e = s?(E); }");
        checkFail(d + "func f(s *Object){ var e = s?(F); }");
        checkFail(d + "func f(s *Object){ var e = s?(S); }");
    }

    @Test
    public void testAssertExpression6() {
        var d = "interface I{} class A{} ";
        checkSucc(d + "func f(o *Object){ var a = o?(*A); }");
        checkSucc(d + "func f(o &Object){ var a = o?(&A); }");
        checkSucc(d + "func f(o *Object){ var a = o?(&A); }");
        checkFail(d + "func f(o &Object){ var a = o?(*A); }");
    }

    @Test
    public void testSizeofExpression1() {
        var d = "struct A{} class B{}";
        checkSucc(d + "func f(){ var s = sizeof(A); }");
        checkFail(d + "func f(){ var s = sizeof(*B); }");
        checkFail(d + "func f(){ var s = sizeof(&B); }");
        checkSucc(d + "func f(){ var s = sizeof([4]int); }");
        checkFail(d + "func f(){ var s = sizeof([*]int); }");
        checkSucc(d + "func f(){ var s = sizeof([4]A); }");
        checkFail(d + "func f(){ var s = sizeof([*]A); }");
    }

    @Test
    public void testSizeofExpression2() {
        var d = "struct A{} class B{}";
        checkSucc(d + "func f(){ var s = sizeof(A); }");
        checkFail(d + "func f(){ var s = sizeof(*B); }");
        checkFail(d + "func f(){ var s = sizeof(&B); }");
        checkSucc(d + "func f(){ var s = sizeof([4]int); }");
        checkFail(d + "func f(){ var s = sizeof([*]int); }");
        checkSucc(d + "func f(){ var s = sizeof([4]A); }");
        checkFail(d + "func f(){ var s = sizeof([*]A); }");
    }

    @Test
    public void testSizeofExpression3() {
        checkSucc("struct A{v(Y)int;} const Y=1;");
        checkFail("struct A{v(Y)int;} var Y=1;");
        checkFail("struct A{v(Y)int;} const Y=1.2;");
        checkFail("struct A{v(Y)int;} const Y=true;");
        checkSucc("struct A{v(sizeof(B))int;} struct B{u uint8;}");
        checkFail("struct A{v(sizeof(B))int;} struct B{u [9]uint;}");
    }

    @Test
    public void testSizeofExpression4() {
        checkFail("struct A{v [sizeof(B)]int;} struct B{u [sizeof(A)]int8;}");
        checkSucc("struct A{v [3]int;} struct B{u [sizeof(A)]int8;}");
        checkSucc("struct A{v [sizeof(B)]int;} struct B{u [6]int8;}");
    }

    @Test
    public void testConvertExpression1() {
        checkSucc("func f(){ var s int32 = 1; }");
        checkFail("func f(){ var s int32 = 1.1; }");
        checkSucc("func f(){ var s int32 = int32(1.1); }");
        checkSucc("func f(a int){ var s int32 = int32(a); }");
        checkSucc("func f(a int){ var s int32 = int32(1+a); }");
        checkSucc("func f(a float){ var s int = int(a); }");
        checkFail("func f(a float){ var s int = int(1+a); }");
        checkSucc("func f(a float){ var s int = int(float(1)+a); }");
        checkSucc("func f(a float){ var s int = int(1+int(a)); }");
    }

    @Test
    public void testIndexExpression1() {
        checkSucc("func f(a [*]int){var v=a[0];}");
        checkFail("func f(a [*]int){var v=a[true];}");
        checkFail("func f(a [*]int, i bool){var v=a[i];}");
        checkFail("func f(a int){var v=a[0];}");
        var d = "class C{} ";
        checkFail(d + "func f(a A){var v=a[0];}");
        checkFail(d + "func f(a [2]int, c C){var v=a[c];}");
    }

    @Test
    public void testIndexExpression2() {
        checkSucc("func f(a [*]int){var v=a[%d];}".formatted(Long.MAX_VALUE));
        checkFail("func f(a [*]int){var v=a[-1];}");
        checkSucc("func f(a [4]int){var v=a[3];}");
        checkFail("func f(a [4]int){var v=a[4];}");

        var d = "enum St{A,B,C,D,E,} struct A{} class B{} ";
        checkSucc(d + "func f()St{return St[0];}");
        checkSucc(d + "func f()St{return St[4];}");
        checkFail(d + "func f()St{return St[-1];}");
        checkFail(d + "func f()St{return A[0];}");
        checkFail(d + "func f()St{return S[0];}");
    }

    @Test
    public void testCallExpression1() {
        var d = "class A{var id int; func run(){}} func go()int{return 0;} func set(int){} const pi = 3.14159;";
        checkSucc(d + "func t() { go(); }");
        checkFail(d + "func t() { var v bool = go(); }");
        checkSucc(d + "func t() { var i=float(pi); }");
        checkFail(d + "func t(i int) { i(); }");
        checkFail(d + "func t() { pi(); }");
        checkSucc(d + "func t(a A) { a.run(); }");
        checkFail(d + "func t(a A) { a(); }");
        checkFail(d + "func t(a A) { a.id(); }");
        checkFail(d + "func t(r *int) { r(); }");
        checkFail(d + "func t() { set(true); }");
    }

    @Test
    public void testNewExpression1() {
        var d = "func A(); enum E{U,V,W,} class C{} interface I{}";
        checkFail(d + "func f(){var v = new(A);}");
        checkSucc(d + "func f(){var v = new(E);}");
        checkSucc(d + "func f(){var v = new(C);}");
        checkFail(d + "func f(){var v = new(I);}");

        checkSucc(d + "func f(s int){var v = new([s]A);}");
        checkSucc(d + "func f(s int){var v = new([s]E);}");
        checkSucc(d + "func f(s int){var v = new([s]C);}");
        checkSucc(d + "func f(s int){var v = new([s]*I);}");
        checkFail(d + "func f(s int){var v = new([s]I);}");
    }

    @Test
    public void testNewExpression2() {
        var d = "class C{var s int;} ";
        checkSucc(d + "func f(){var b=new(C,{});}");
        checkSucc(d + "func f(){var b=new(C,{s=1});}");
        checkFail(d + "func f(){var b=new(C,1);}");
        checkFail(d + "func f(){var b=new(C,true);}");
        checkFail(d + "func f(){var b=new(C,\"\");}");
        checkFail(d + "func f(){var b=new(C,{t=1});}");
        checkSucc(d + "func f(a C){var b=new(C,a);}");
        checkSucc(d + "func f(a *C){var b=new(C,a);}");
        checkSucc(d + "func f(a &C){var b=new(C,a);}");
    }

    @Test
    public void testNewExpression3() {
        checkSucc("func f(){var b=new([2][*]int);}");
        checkFail("func f(){var b=new([2][&]int);}");
        checkSucc("func f(){var b=new([2][3][*]int);}");
        checkFail("func f(){var b=new([2][3][&]int);}");
        checkSucc("func f(){var b=new([2][*]int, [nil]);}");
        checkFail("func f(){var b=new([2][*]int, [1]);}");
        checkSucc("func f(a [*][*]int){var b=new([2][*]int, a);}");
        checkSucc("func f(a [*][3]int){var b=new([2][3]int, a);}");
        checkSucc("func f(a [*]int){var b=new([2]int, a);}");
        checkSucc("func f(a [2]int){var b=new([2]int, a);}");
    }

    @Test
    public void testNewExpression4() {
        var d = "class C{const s int;} ";
        checkSucc(d + "func f(){var b=new([2]C,[{s=1}]);}");
        checkSucc(d + "func f(a C){var b=new([2]C,[a]);}");
        checkFail(d + "func f(a *C){var b=new([2]C,[a]);}");
        checkSucc(d + "func f(a *C){var b=new([2]*C,[a]);}");
        checkFail(d + "func f(a &C){var b=new([2]C,[a]);}");
        checkFail(d + "func f(){var b=new([2]&C);}");
    }

    @Test
    public void testDereferExpression1() {
        var d = "struct A{} class B{} ";
        checkSucc(d + "func f(a*int){var v int = *a;}");
        checkFail(d + "func f(a*int){var v int = a;}");
        checkSucc(d + "func f(a*A){var v A = *a;}");
        checkFail(d + "func f(a*A){var v A = a;}");
        checkSucc(d + "func f(a*B){var v B = *a;}");
        checkFail(d + "func f(a*B){var v B = a;}");

        checkFail(d + "func f(a*B){var v B = **a;}");
    }

    @Test
    public void testDereferExpression2() {
        for (var op : BinaryOperator.SetMath) {
            checkSucc("func f(a,b *int){var v int = *a %s *b; }".formatted(op));
        }
        for (var op : BinaryOperator.SetBits) {
            checkSucc("func f(a,b *int){var v int = *a %s *b; }".formatted(op));
        }
        for (var op : BinaryOperator.SetRel) {
            checkSucc("func f(a,b *int){var v bool = *a %s *b; }".formatted(op));
        }
        for (var op : UnaryOperator.values()) {
            checkSucc("func f(a *int){var v int = %s *a; }".formatted(op));
        }
    }

    //

    @Test
    public void testAssignTuple1() {
        checkSucc("func f() { var i,j int = 1,2; }");
        checkSucc("func f() { var i,j int; i,j = 1,2; }");
        checkSucc("func f(a,b int) { var i,j int; i,j = a-b,a+b; }");
        checkSucc("func f(a,b int) { var i int; var j bool; i,j = a-b, a<b; }");
    }

    @Test
    public void testAssignTuple2() {
        checkFail("func f() { var i,j int = 1; }");
        checkFail("func f() { var i,j int = 1,2,3; }");
        checkFail("func f() { var i,j int = 1,2.5; }");
        checkFail("func f() { var i,j int = false,2; }");
    }

    //

    @Test
    public void testLiteral1() {
        checkSucc("func f() { const a,b=1,2; const c=a+b; }");
        checkSucc("func f() { const a,b=1,2; const c=a-b; }");
        checkSucc("func f() { const a,b=1,2; const c=a*b; }");
        checkSucc("func f() { const a,b=1,2; const c=a/b; }");
        checkSucc("func f() { const a,b=1,2; const c=a%b; }");
        checkSucc("func f() { const a,b=1,2; const c=a&b; }");
        checkSucc("func f() { const a,b=1,2; const c=a|b; }");
        checkSucc("func f() { const a,b=1,2; const c=a~b; }");
        checkSucc("func f() { const a,b=1,2; const c=a==b; }");
        checkSucc("func f() { const a,b=1,2; const c=a!=b; }");
        checkFail("func f() { const a,b=1,2; const c=a&&b; }");
        checkFail("func f() { const a,b=1,2; const c=a||b; }");
    }

    @Test
    public void testLiteral2() {
        checkSucc("func f() { const a,b=1.1, 2.2; const c=a+b; }");
        checkSucc("func f() { const a,b=1.1, 2.2; const c=a-b; }");
        checkSucc("func f() { const a,b=1.1, 2.2; const c=a*b; }");
        checkSucc("func f() { const a,b=1.1, 2.2; const c=a/b; }");
        checkSucc("func f() { const a,b=1.1, 2.2; const c=a%b; }");
    }

    @Test
    public void testLiteral3() {
        checkSucc("func f() { const a,b=1.1, 2.2; const c=a<b; }");
        checkSucc("func f() { const a,b=1.1, 2.2; const c=a<=b; }");
        checkSucc("func f() { const a,b=1.1, 2.2; const c=a>b; }");
        checkSucc("func f() { const a,b=1.1, 2.2; const c=a>=b; }");
        checkSucc("func f() { const a,b=1.1, 2.2; const c=a==b; }");
        checkSucc("func f() { const a,b=1.1, 2.2; const c=a!=b; }");
    }

    @Test
    public void testLiteral4() {
        checkSucc("func f() { const a,b=true, false; const c=a==b; }");
        checkSucc("func f() { const a,b=true, false; const c=a!=b; }");
        checkSucc("func f() { const a,b=true, false; const c=a&&b; }");
        checkSucc("func f() { const a,b=true, false; const c=a||b; }");
        checkSucc("func f() { const a,b=true, false; const c=a&b; }");
        checkSucc("func f() { const a,b=true, false; const c=a|b; }");
    }

    @Test
    public void testLiteral5() {
        checkSucc("func f() { const a,b=\"true\", \"false\"; const c=a+b; }");
        checkFail("func f() { const a,b=\"true\", \"false\"; const c=a-b; }");
    }

    @Test
    public void testLiteral6() {
        checkSucc("func f() { const a=\"true\"; const c [*#]uint8 = a; }");
        checkSucc("func f() { const a=\"true\"; const c [&#]uint8 = a; }");
    }

    //

    @Test
    public void testConstValue1() {
        checkFail("func f(v int) { const i int; i = v; }");
        checkFail("func f(v int) { v = 0; }");
        checkFail("func f(v [4]int) { v[0] = 0; }");
    }

    @Test
    public void testConstValue2() {
        checkFail("class Dev { const id int; func f() { id = 0; } }");
        checkFail("class Dev { var id int; } func f(d Dev) { d.id = 0; }");
        checkFail("class Dev { var id int; } class Disk { var dev Dev; } func f(d Disk) { d.dev.id = 0; }");
        checkFail("class Dev { var id int; } func f(d [4]Dev) { d[0].id = 0; }");
    }

    @Test
    public void testConstValue3() {
        var d = "class A{var id int;} ";
        checkSucc(d + "func f(a *A){a.id+=2;}");
        checkFail(d + "func f(a  A){a.id+=2;}");
    }

    @Test
    public void testConstValue4() {
        var d = "class A{var id int;} class B{var a *A;}";
        checkSucc(d + "func f(b *B){b.a.id+=2;}");
        checkSucc(d + "func f(b  B){b.a.id+=2;}");
    }

    @Test
    public void testConstValue5() {
        var d = "class A{var id int;} class B{var a A;}";
        checkSucc(d + "func f(b *B){b.a.id+=2;}");
        checkFail(d + "func f(b  B){b.a.id+=2;}");
    }

    @Test
    public void testConstValue6() {
        var d = "struct A{id int;} class B{var a A;}";
        checkSucc(d + "func f(b *B){b.a.id+=2;}");
        checkFail(d + "func f(b  B){b.a.id+=2;}");
    }

    @Test
    public void testConstValue7() {
        var d = "struct A{id int;} class B{var a *A;}";
        checkSucc(d + "func f(b *B){b.a.id+=2;}");
        checkSucc(d + "func f(b &B){b.a.id+=2;}");
        checkSucc(d + "func f(b  B){b.a.id+=2;}");
    }

    @Test
    public void testConstValue8() {
        var d = "struct A{id int;} struct B{a A;} ";
        checkFail(d + "func f(b B){b.a.id+=2;}");

        checkSucc(d + "func f(b *B){b.a.id+=2;}");
        checkSucc(d + "func f(b &B){b.a.id+=2;}");

        checkSucc(d + "func f(b *B){var i int = -b.a.id;}");
    }

    @Test
    public void testConstValue9() {
        var d = "struct S{tag int;} class C{var id int;} ";
        checkSucc(d + "func f(){new(C).id=1;}");
        checkSucc(d + "func f(c*C){c?(*C).id=1;}");
        checkSucc(d + "func f(){new([5]S)[2].tag=1;}");
        checkFail(d + "func f(){[5][2]=1;}");
        checkFail(d + "func f(){{id=2}.id=1;}");
    }

    //

    @Test
    public void testReferrable1() {
        checkSucc("class ID{} func f() { var i *ID; i = nil; }");
        checkSucc("class ID{} func f() { var i *ID; i = new(ID); }");
        checkSucc("class ID{} func f(v *ID) { var i *ID; i = v; }");
        checkSucc("interface ID{} func f(v *ID) { var i *ID; i = v; }");
        checkSucc("interface ID{} func f(v *ID) { var i *ID; i = nil; }");
    }

    @Test
    public void testReferrable2() {
        checkSucc("class ID{} func f(v *ID) *ID { return nil; }");
        checkSucc("class ID{} func f(v *ID) *ID { return new(ID); }");
        checkSucc("class ID{} func f(v *ID) *ID { return v; }");
        checkSucc("interface ID{} func f(v *ID) *ID { return v; }");
        checkSucc("interface ID{} func f(v *ID) *ID { return nil; }");
    }

    @Test
    public void testReferrable3() {
        checkSucc("func f(v *bool) {}");
        checkSucc("struct ID{} func f(v *ID) {}");
        checkSucc("union ID{} func f(v *ID) {}");
        checkSucc("enum ID{A,} func f(v *ID) {}");
        checkFail("func ID(); func f(v *ID) {}");
    }

    @Test
    public void testTransferable1() {
        var def = "class A{} class B:A{} ";
        checkSucc(def + "func f(b *B) { var a *A = b; }");
        checkFail(def + "func f(b *A) { var a *B = b; }");
    }

    @Test
    public void testTransferable2() {
        var def = "class A{} class B:A{} class C:B{} ";
        checkSucc(def + "func f(c *C) { var a *B = c; }");
        checkSucc(def + "func f(c *C) { var a *A = c; }");
        checkFail(def + "func f(b *B) { var c *C = b; }");
        checkFail(def + "func f(a *A) { var c *C = a; }");
    }

    @Test
    public void testTransferable3() {
        var def = "interface I{} class A(I){} ";
        checkSucc(def + "func f(b *A) { var a *I = b; }");
        checkFail(def + "func f(b *I) { var a *A = b; }");
    }

    @Test
    public void testTransferable4() {
        var def = "interface I{} class A(I){} class B:A{}";
        checkSucc(def + "func f(b *B) { var i *I = b; }");
        checkFail(def + "func f(i *I) { var b *B = i; }");
    }

    @Test
    public void testTransferable5() {
        var d = "interface I{} interface J{I;} class A(J){} ";
        checkSucc(d + "func f(b *A) { var a *I = b; }");
        checkFail(d + "func f(b *I) { var a *A = b; }");
    }

    @Test
    public void testTransferable6() {
        var d = "interface I{} interface J{I;} ";
        checkSucc(d + "func f(b *J) { var a *I = b; }");
        checkFail(d + "func f(b *I) { var a *J = b; }");
    }

    @Test
    public void testTransferable7() {
        var d = "interface I{} interface J{I;} interface K{J;}";
        checkSucc(d + "func f(b *K) { var a *I = b; }");
        checkFail(d + "func f(b *I) { var a *K = b; }");
    }

    @Test
    public void testTransferable8() {
        var d = "struct S{id int;} class C{var id int;} interface I{get();}";
        checkFail(d + "func f(s *S){var c *C = s;}");
        checkFail(d + "func f(c *C){var s *S = c;}");
        checkFail(d + "func f(s *int){var c *C = s;}");
        checkFail(d + "func f(c *C){var s *bool = c;}");
        checkFail(d + "func f(s *int){var i *I = s;}");
        checkFail(d + "func f(i *I){var s *bool = i;}");
    }

    @Test
    public void testTransferable9() {
        var d = "interface I{} class A{} ";
        checkSucc(d + "func f(i*I){var o *Object = i;}");
        checkSucc(d + "func f(a*A){var o *Object = a;}");
    }

    @Test
    public void testImmutable1() {
        checkSucc("func f(a *int){var v *#int = a;}");
        checkSucc("func f(a *#int){var v *#int = a;}");
        checkFail("func f(a *#int){var v *int = a;}");

        checkSucc("func f(a *int){const v &#int = a;}");
        checkSucc("func f(a *#int){const v &#int = a;}");
        checkFail("func f(a *#int){const v &int = a;}");

        var d = "class C{} ";
        checkSucc(d + "func f(a *C){var v *#C = a;}");
        checkSucc(d + "func f(a *#C){var v *#C = a;}");
        checkFail(d + "func f(a *#C){var v *C = a;}");

        checkSucc(d + "func f(a *C){const v &#C = a;}");
        checkSucc(d + "func f(a *#C){const v &#C = a;}");
        checkFail(d + "func f(a *#C){const v &C = a;}");
    }

    @Test
    public void testImmutable2() {
        checkSucc("func f(){ var a int; const v &int = a;}");
        checkSucc("func f(){ var a int; const v &#int = a;}");
        checkFail("func f(a int){ const v &int = a;}");
        checkSucc("func f(a int){ const v &#int = a;}");

        var d = "class C{} ";
        checkSucc(d + "func f(){ var a C; const v &C = a;}");
        checkSucc(d + "func f(){ var a C; const v &#C = a;}");
        checkFail(d + "func f(a C){ const v &C = a;}");
        checkSucc(d + "func f(a C){ const v &#C = a;}");
    }

    @Test
    public void testImmutable3() {
        checkSucc("func f(a [*]int){var v [*#]int = a;}");
        checkSucc("func f(a [*#]int){var v [*#]int = a;}");
        checkFail("func f(a [*#]int){var v [*]int = a;}");

        checkSucc("func f(a [*]int){var v [*#]int = a;}");
        checkSucc("func f(a [*#]int){var v [*#]int = a;}");
        checkFail("func f(a [*#]int){var v [*]int = a;}");

        checkSucc("func f(){ var a [2]int; const v [&]int = a;}");
        checkSucc("func f(){ var a [2]int; const v [&#]int = a;}");
        checkFail("func f(a [2]int){ const v [&]int = a;}");
        checkSucc("func f(a [2]int){ const v [&#]int = a;}");
    }

    @Test
    public void testRequired1() {
        checkSucc("func f(){var v *int = nil;}");
        checkFail("func f(){var v *!int = nil;}");
        checkSucc("func f(){var v *int;}");
        checkFail("func f(){var v *!int;}");
    }

    @Test
    public void testRequired2() {
        checkSucc("func f(a *!int){var v *int = a;}");
        checkSucc("func f(a *!int){var v *!int = a;}");
        checkFail("func f(a *int){var v *!int = a;}");
    }

    @Test
    public void testRequired3() {
        checkFail("func f(a *int){var v *!int = a;}");
        checkSucc("func f(a *int){if(a != nil) var v *!int = a;}");
        checkFail("func f(a *int){if(a == nil) var v *!int = a;}");
        checkSucc("func f(a *int){if(a == nil) {} else var v *!int = a;}");
    }

    //

    @Test
    public void testPhantom1() {
        var d = "class A{var n *A;} ";
        checkSucc(d + "func f(){ var a A; const r &A = a; }");
        checkSucc(d + "func f(a *A){ const r &A = a; }");
        checkSucc(d + "func f(a &A){ const r &A = a; }");
        checkFail(d + "func f(a &A){ var r &A = a; }");
        checkFail(d + "func f(a &A){ var r *A = a; }");
        checkFail(d + "func f(a &A){ var r A = a; }");
    }

    @Test
    public void testPhantom2() {
        checkSucc( "func f(){var s int; const r &int = s;}");
        checkFail( "func f(){const s int; const r &int = s;}");
        checkSucc( "func f(s *int){const r &int = s;}");
        checkSucc( "func f(s &int){const r &int = s;}");
    }

    @Test
    public void testPhantom3() {
        var d = "class A{} ";
        checkSucc(d + "func f() {const a *A=new(A); const b &A =a;}");
        checkSucc(d + "func f() {var a *A=new(A); const b &A =a;}");
        checkFail(d + "func f() {var a *A=new(A); const b &A =a; a=nil;}");
        checkSucc(d + "func f() {var a *A=new(A); {const b &A =a;} a=nil;}");
        checkFail(d + "func f() {var a *A=new(A); {const b &A =a; a=nil;} }");
    }

    @Test
    public void testPhantom4() {
        var d = "class A{} var a0 = A{}; var a1 = new(A); const a2 = new(A); ";
        checkSucc(d + "func f() {const b &A = a0;}");
        checkFail(d + "func f() {const b &A = a1;}");
        checkSucc(d + "func f() {const b &A = a2;}");
    }

    @Test
    public void testPhantomInherit1() {
        var d = "class A{} class B:A{} var a A; var b B; ";
        checkFail(d + "const r &A = a;");
        checkSucc(d + "func f() { const r &A = a; }");
        checkSucc(d + "func f() { const r &A = b; }");
        checkSucc(d + "func f(c func(&A)) { c(a); }");
        checkSucc(d + "func f(c func(&A)) { c(b); }");
        d = "class A{} class B:A{} const a A; const b B; ";
        checkFail(d + "const r &A = a;");
        checkFail(d + "func f() { const r &A = a; }");
        checkFail(d + "func f() { const r &A = b; }");
        checkFail(d + "func f(c func(&A)) { c(a); }");
        checkFail(d + "func f(c func(&A)) { c(b); }");
    }

    @Test
    public void testPhantomInherit2() {
        var d = "interface I{} class A(I){} class B:A{} ";
        checkSucc(d + "func f() { var a A; const r &A = a; }");
        checkSucc(d + "func f() { var b B; const r &A = b; }");
        checkSucc(d + "func f() { var a A; const r &I = a; }");
        checkSucc(d + "func f() { var b B; const r &I = b; }");

        checkSucc(d + "func f() { const a *A=nil; const r &A = a; }");
        checkSucc(d + "func f() { const b *B=nil; const r &A = b; }");
        checkSucc(d + "func f() { const a *A=nil; const r &I = a; }");
        checkSucc(d + "func f() { const b *B=nil; const r &I = b; }");

        checkSucc(d + "func f() { var a *A; const r &A = a; }");
        checkSucc(d + "func f() { var b *B; const r &A = b; }");
        checkSucc(d + "func f() { var a *A; const r &I = a; }");
        checkSucc(d + "func f() { var b *B; const r &I = b; }");

        checkFail(d + "func f() { const a A; const r &A = a; }");
        checkFail(d + "func f() { const b B; const r &A = b; }");
        checkFail(d + "func f() { const a A; const r &I = a; }");
        checkFail(d + "func f() { const b B; const r &I = b; }");
    }

    @Test
    public void testPhantomInherit3() {
        var d = "interface I{} class A(I){} class B:A{} ";
        checkSucc(d + "func f(a *A) { const r &A = a; }");
        checkSucc(d + "func f(b *B) { const r &A = b; }");
        checkSucc(d + "func f(a *A) { const r &I = a; }");
        checkSucc(d + "func f(b *B) { const r &I = b; }");

        checkFail(d + "func f(a A) { const r &A = a; }");
        checkFail(d + "func f(b B) { const r &A = b; }");
        checkFail(d + "func f(a A) { const r &I = a; }");
        checkFail(d + "func f(b B) { const r &I = b; }");
    }

    @Test
    public void testPhantomInherit4() {
        var d = "interface I{} class A(I){} class B:A{} ";
        checkSucc(d + "func f(c func(a &B)) { var a B;c(a); }");
        checkSucc(d + "func f(c func(a &A)) { var a B;c(a); }");
        checkSucc(d + "func f(c func(a &I)) { var a B;c(a); }");
        checkSucc(d + "func f(c func(a &B)) { var a *B;c(a); }");
        checkSucc(d + "func f(c func(a &A)) { var a *B;c(a); }");
        checkSucc(d + "func f(c func(a &I)) { var a *B;c(a); }");

        checkFail(d + "func f(c func(a &B), b B) { c(b); }");
        checkFail(d + "func f(c func(a &A), b B) { c(b); }");
        checkFail(d + "func f(c func(a &I), b B) { c(b); }");
        checkSucc(d + "func f(c func(a &B), b *B) { c(b); }");
        checkSucc(d + "func f(c func(a &A), b *B) { c(b); }");
        checkSucc(d + "func f(c func(a &I), b *B) { c(b); }");
        checkSucc(d + "func f(c func(a &I), b *B) { c(b); }");

        checkSucc(d + "func f(c func(a &B), b &B) { c(b); }");
        checkSucc(d + "func f(c func(a &A), b &B) { c(b); }");
        checkSucc(d + "func f(c func(a &I), b &B) { c(b); }");
        checkSucc(d + "func f(c func(a &I), b &B) { c(b); }");

    }

    @Test
    public void testPhantomInherit5() {
        var d = "interface I{} class A(I){} class B:A{} class R{var b B;}";
        checkSucc(d + "func f(c func(a &B)) { var r R;c(r.b); }");
        checkSucc(d + "func f(c func(a &A)) { var r R;c(r.b); }");
        checkSucc(d + "func f(c func(a &I)) { var r R;c(r.b); }");

        checkFail(d + "func f(c func(a &B), r R) { c(r.b); }");
        checkFail(d + "func f(c func(a &A), r R) { c(r.b); }");
        checkFail(d + "func f(c func(a &I), r R) { c(r.b); }");


        checkSucc(d + "func f(c func(a &B), r *R) { c(r.b); }");
        checkSucc(d + "func f(c func(a &A), r *R) { c(r.b); }");
        checkSucc(d + "func f(c func(a &I), r *R) { c(r.b); }");

        checkFail(d + "func f(c func(a &B), r R) { c(r.b); }");
        checkFail(d + "func f(c func(a &A), r R) { c(r.b); }");
        checkFail(d + "func f(c func(a &I), r R) { c(r.b); }");

        checkSucc(d + "func f(c func(a &B), r &R) { c(r.b); }");
        checkSucc(d + "func f(c func(a &A), r &R) { c(r.b); }");
        checkSucc(d + "func f(c func(a &I), r &R) { c(r.b); }");

    }

    @Test
    public void testPhantomInherit6() {
        var d = "interface I{} class A(I){} class B:A{} class R{const b *B;}";
        checkSucc(d + "func f(c func(a &B)) { var r R;c(r.b); }");
        checkSucc(d + "func f(c func(a &A)) { var r R;c(r.b); }");
        checkSucc(d + "func f(c func(a &I)) { var r R;c(r.b); }");

        checkSucc(d + "func f(c func(a &B), r *R) { c(r.b); }");
        checkSucc(d + "func f(c func(a &A), r *R) { c(r.b); }");
        checkSucc(d + "func f(c func(a &I), r *R) { c(r.b); }");

        checkSucc(d + "func f(c func(a &B), r &R) { c(r.b); }");
        checkSucc(d + "func f(c func(a &A), r &R) { c(r.b); }");
        checkSucc(d + "func f(c func(a &I), r &R) { c(r.b); }");
    }

    @Test
    public void testPhantomField1() {
        var d = "struct R{s S;} struct S{v int8;} class C{var r R;} ";
        checkSucc(d + "func f(c *C){const s &S = c.r.s;}");
        checkFail(d + "func f(c *C){const s &int16 = c.r.s;}");
    }

    @Test
    public void testPhantomField2() {
        var d = "class R{var s S;} class S{var v int8;} class C{var r R;} ";
        checkSucc(d + "func f(c *C){const s &S = c.r.s;}");
        checkFail(d + "func f(c *C){const s &int16 = c.r.s;}");
    }

    @Test
    public void testPhantomField3() {
        var d = "interface I{} interface J{I;} class R{const j *J;} ";
        checkSucc(d + "func f(r *R){ const i &I = r.j; }");
        d = d + "class T{var r R;}";
        checkSucc(d + "func f(t *T){ const i &I = t.r.j; }");
    }

    @Test
    public void testPhantomMethod1() {
        var d = "class T{} ";
        checkSucc(d + "class A{const v *T; func m(){ const r &T = v; } }");
        checkFail(d + "class A{var v *T; func m(){ const r &T = v; } }");
        checkSucc(d + "class A{const v *T; func m(){ const r &T = this.v; } }");
        checkFail(d + "class A{var v *T; func m(){ const r &T = this.v; } }");
        d = "struct S{} ";
        checkSucc(d + "class A{const v *S; func m(){ const r &S = v; } }");
        checkFail(d + "class A{var v *S; func m(){ const r &S = v; } }");
        checkSucc(d + "class A{const v *S; func m(){ const r &S = this.v; } }");
        checkFail(d + "class A{var v *S; func m(){ const r &S = this.v; } }");
    }

    @Test
    public void testPhantomArray1() {
        var d = "class A{} class B{var ar [4]A; const sz [*]A;} ";
        checkSucc(d + "func f(b *B) { const a &A = b.ar[0]; }");
        checkFail(d + "func f(b B) { const a &A = b.ar[0]; }");
        checkSucc(d + "func f(b *B) { const a &A = b.sz[0]; }");
        checkSucc(d + "func f(b B) { const a &A = b.sz[0]; }");
    }

    @Test
    public void testPhantomArray2() {
        var d = "class A{} class B{var ar [4]*A; const sz [*]*A;} ";
        checkFail(d + "func f(b *B) { const a &A = b.ar[0]; }");
        checkFail(d + "func f(b B) { const a &A = b.ar[0]; }");
        checkFail(d + "func f(b *B) { const a &A = b.sz[0]; }");
        checkFail(d + "func f(b B) { const a &A = b.sz[0]; }");
    }

    @Test
    public void testPhantomArray3() {
        var d = "class T{} struct R{} ";
        String[] elements = {"int", "[3]int",
                "T", "*T", "[3]T", "[*]T", "[5]*T", "[*]*T",
                "R", "*R", "[3]R", "[*]R", "[5]*R", "[*]*R"};
        for (var e : elements) {
            checkSucc(d + "func m(){ var v [2]%s; const r [&]%s = v; }".formatted(e, e));
            checkFail(d + "func m(){ const v [2]%s; const r [&]%s = v; }".formatted(e, e));
            checkSucc(d + "func m(){ var v [*]%s; const r [&]%s = v; }".formatted(e, e));
            checkSucc(d + "func m(v [*]%s){ const r [&]%s = v; }".formatted(e, e));
        }
    }

    @Test
    public void testPhantomArray4() {
        var d = "class C{var id int;} struct S{head [4]int;}";
        String[] elements = {"int", "[3]int", "*int", "S", "[1]S", "[*]S",
                "C", "*C", "[3]C", "[*]C", "[5]*C", "[*]*C"};
        for (var e : elements) {
            checkSucc(d + "class T{var f [3]%s;} func m(t *T){ const r [&]%s = t.f; }".formatted(e, e));
            checkFail(d + "class T{const f [3]%s;} func m(t *T){ const r [&]%s = t.f; }".formatted(e, e));
            checkFail(d + "class T{var f [*]%s;} func m(t *T){ const r [&]%s = t.f; }".formatted(e, e));
            checkSucc(d + "class T{const f [*]%s;} func m(t *T){ const r [&]%s = t.f; }".formatted(e, e));
        }
    }

    @Test
    public void testPhantomArray5() {
        checkSucc("union U{t [2]int;} struct S{h [13]U;} func f(){ var s S; const r [&]U = s.h; }");
    }

    //

    @Test
    public void testArray1() {
        checkSucc("func t() { var a [2]int; }");
        checkSucc("func t() { var a [0]int; }");
        checkFail("func t() { var a [-2]int; }");
        checkFail("func t() { var a [true]int; }");
        checkFail("func t() { var a [2.1]int; }");
        checkSucc("func t() { var a [*]int; }");

        checkSucc("func t() { var a = [1,2,3]; }");
        checkFail("func t() { var a = []; }");

        checkSucc("func t() { var a [2]int = []; }");
        checkFail("func t() { var a [*]int = []; }");

        checkFail("func t() { var a [2]int = nil; }");
        checkSucc("func t() { var a [*]int = nil; }");

        checkSucc("func t() { var a [4]int = [1,2,3]; }");
        checkSucc("func t() { var a [4]int = [1,2,3,4]; }");
        checkFail("func t() { var a [4]int = [1,2,3,4,5]; }");
    }

    @Test
    public void testArray2() {
        checkSucc("func t() {var a [4]int; const b [&]int =a;}");
        checkFail("func t() {const a [4]int; const b [&]int =a;}");
        checkSucc("func t() {var a [*]int; const b [&]int =a;}");
        checkSucc("func t() {const a [*]int=new([4]int); const b [&]int =a;}");
        checkFail("func t() {var a [*]int; const b [&]int =a; a=nil;}");
        checkSucc("func t() {var a [*]int; {const b [&]int =a;} a=nil;}");
    }

    @Test
    public void testArray3() {
        checkSucc("func t() { var a [*]int = new([8]int); }");
        checkSucc("func t() { var a [*]int = new([4]int, [1,2,3]); }");
        checkSucc("func t() { var a [*]int; a = new([8]int); }");
        checkSucc("func t() { var a [*]int; a = new([4]int, [1,2,3]); }");
        checkFail("func t() { var a [*]int; a = new([4]int, {}); }");
    }

    @Test
    public void testArray4() {
        checkSucc("func t() { var a = new([8]int); }");
        checkSucc("func t() { var a = new([4]int, [1,2,3]); }");
        checkFail("func t() { var a = new([4]int, {}); }");
    }

    @Test
    public void testArray5() {
        checkFail("func t() { var a [4]int = new([4]int); }");
        checkFail("func t() { var a [*]int = [1,2,3]; }");
        checkFail("func t() { var a [4]int; a = new([4]int); }");
        checkFail("func t() { var a [*]int; a = [1,2,3]; }");
    }

    @Test
    public void testArray6() {
        checkSucc("func t(s int) { var a [*]int = new([s]int); }");
        checkFail("func t(s bool) { var a [*]int = new([s]int); }");
        checkSucc("func t(s int) { var l = s; var a [*]int = new([l]int); }");
        checkFail("func t() { var a [*]int = [1,2,3]; }");
        checkFail("func t() { var a [4]int; a = new([4]int); }");
        checkFail("func t() { var a [*]int; a = [1,2,3]; }");
    }

    @Test
    public void testArray7() {
        checkSucc("func t(s [*]int) { s[0] = s[1]; }");
        checkSucc("func t(s [*]int, i int) { s[i] = s[1]; }");
        checkFail("func t(s int) { s[0] = s[1]; }");
        checkFail("func t(s [4]int) { s[0] = s[1]; }");
    }

    @Test
    public void testArray8() {
        checkSucc("func f(){var s [*][2]int;}");

        checkSucc("func f(){var s [3][*]int;}");
        checkSucc("func f(){var s [3][2]int;}");
        checkFail("func f(){var s [3][&]int;}");

        checkFail("func f(){var s [3][2][&]int;}");
        checkFail("func f(){var s [3][*][&]int;}");
        checkSucc("func f(){var s [3][*][*]int;}");

        checkSucc("func f(){var s [*][2]int;}");
        checkSucc("func f(){var s [*][*]int;}");
        checkFail("func f(){var s [*][&]int;}");
        checkFail("func f(){var s [*][2][&]int;}");
    }

    @Test
    public void testArray9() {
        var d = "class C{} ";
        checkSucc(d + "func f(){var s [5]C;}");
        checkSucc(d + "func f(){var s [5]*C;}");
        checkFail(d + "func f(){var s [5]&C;}");
        checkSucc(d + "func f(){var s [*]C;}");
        checkSucc(d + "func f(){var s [*]*C;}");
        checkFail(d + "func f(){var s [*]&C;}");
    }

    @Test
    public void testArray10() {
        var d = "const s1=s2*s3-s4; const s4=s2+s3; const s3=s2-1; const s2=5; var s6=s2;";
        checkSucc(d + "func f(a [s1]int){ var b [11]int = a; }");
        checkFail(d + "func f(a [s1]int){ var b [s3]int = a; }");
        checkFail(d + "func f(a [s1]int){ var b [s2]int = a; }");
        checkSucc(d + "func f(){var a=new([s1]int);}");
        checkSucc(d + "func f(){var a=new([s1]*int);}");

        checkFail(d + "func f(a [*]int){var b [s1]int = a;}");
        checkFail(d + "func f(a [s1]int){var b [*]int = a;}");
        checkSucc(d + "func f(a [s1]int){var b [s1]int = a;}");

        checkFail(d + "func f(){ var b [s6]int; }");
    }

    @Test
    public void testArray11() {
        var d = "class A(I){} class B:A{} interface I{} ";
        checkSucc(d + "func f(s *B){var r *A = s;}");
        checkFail(d + "func f(s [2]*B){var r [2]*A = s;}");
        checkFail(d + "func f(s [*]*B){var r [*]*A = s;}");
        checkFail(d + "func f(s [&]*B){var r [&]*A = s;}");
        checkFail(d + "func f(s [*]*A){var r [*]*I = s;}");
        checkFail(d + "func f(s [&]*A){var r [&]*I = s;}");

        checkSucc(d + "func f(s [&]*A){var r [3]*I; r[2] = s[0];}");
    }

    @Test
    public void testArray12() {
        checkSucc("func f(){var a [4]int; a[3]=0;}");
        checkFail("func f(){var a [4]int; a[false]=0;}");
        checkFail("func f(){var a [4]int; a[0.1]=0;}");
        checkSucc("func f(i uint8){var a [4]int; a[i]=0;}");
        checkFail("func f(i bool){var a [4]int; a[i]=0;}");
        checkFail("func f(i float){var a [4]int; a[i]=0;}");
    }

    @Test
    public void testArray13() {
        var d = "class P{} class C:P(I){} interface I{} struct S{} enum E{A,} func F(); ";
        checkSucc(d + "func f(){var a [2]int = [0]; }");
        checkSucc(d + "func f(){var a [2]C = [{}]; }");
        checkSucc(d + "func f(){var a [2]S = [{}]; }");
        checkSucc(d + "func f(){var a [2]E = [E.A]; }");
        checkSucc(d + "func f(){var a [2]F = [f]; }");
        checkSucc(d + "func f(){var a [2]func() = [f]; }");

        checkSucc(d + "func f(){var a [2]*int = [new(int)]; }");
        checkSucc(d + "func f(){var a [2]*C = [new(C)]; }");
        checkSucc(d + "func f(){var a [2]*P = [new(C)]; }");
        checkSucc(d + "func f(){var a [2]*I = [new(C)]; }");
        checkSucc(d + "func f(){var a [2]*S = [new(S)]; }");

        checkFail(d + "func f(a [*]func()*C){var v [*]func()*P = a;}");
    }

    @Test
    public void testArray14() {
        checkSucc("var a [2]int = [1,2];");
        checkSucc("var a [2]int = [2]int[1,2];");
        checkSucc("var a [2]int = []int[1,2];");
        checkFail("var a [2]int = []int[1,2,3];");

        var d = "struct A{id int;} ";
        checkSucc(d + "var a [2]A = [{id=1}];");
        checkSucc(d + "var a [2]A = [2]A[{id=1}];");
        checkSucc(d + "var a [2]A = []A[{id=1}];");

        checkSucc(d + "var a = []int[1];");
        checkFail(d + "var a = []int[{id=1}];");
        checkFail(d + "var a = []A[1];");
        checkSucc(d + "var a = []A[{id=1}];");

    }

    @Test
    public void testDeclareMultiArray1() {
        checkSucc("func t() { var a [2][4]int = []; }");
        checkSucc("func t() { var a [2][4]int = [[]]; }");
        checkSucc("func t() { var a [2][4]int = [[1,2]]; }");
        checkSucc("func t() { var a [2][4]int = [[1,2,3,4]]; }");
        checkFail("func t() { var a [2][4]int = [[1,2,3,4,5]]; }");
        checkFail("func t() { var a [2][4]int = [[1,2],[2],[]]; }");
    }

    @Test
    public void testDeclareMultiArray2() {
        checkSucc("func t(v [4]int) { var a [2][4]int = [v]; }");
        checkFail("func t(v [2]int) { var a [2][4]int = [v]; }");
        checkFail("func t(v [*]int) { var a [2][4]int = [v]; }");
    }

    @Test
    public void testDeclareMultiArray3() {
        checkSucc("func t(v [*]int) { var a [2][*]int = [v]; }");
        checkFail("func t(v [4]int) { var a [2][*]int = [v]; }");
        checkFail("func t(v [4]int) { var a [*][4]int = [v]; }");
        checkSucc("func t(v [4]int) { var a [*][4]int = new([6][4]int, [v]); }");
    }

    @Test
    public void testDeclareMultiArray4() {
        checkSucc("func t(v int) { var a [2][4]int; a[0][1] = v; }");
        checkFail("func t(v int8) { var a [2][4]int; a[0][1] = v; }");
        checkSucc("func t(a [2][4]int) int { return a[0][1]; }");
    }

    //

    @Test
    public void testGlobal1() {
        checkFail("class A{} var A int;");
    }

    @Test
    public void testGlobalVar1() {
        checkSucc("var a int;");
        checkSucc("var a int = 1;");
        checkFail("var a int = b;");
        checkSucc("var a = 1;");
        checkFail("const a int;");
        checkSucc("const a int = 1;");
        checkSucc("const a = 1;");
    }

    @Test
    public void testGlobalVar2() {
        checkSucc("var a int;");
        checkSucc("var a int = 1;");
        checkSucc("var a = 1;");
        checkFail("var a float = 1;");
    }

    @Test
    public void testGlobalVar3() {
        checkSucc("const a = 1; var b = a;");
        checkSucc("var b = a; const a = 1;");
        checkSucc("var b = -a; const a = 1;");
        checkSucc("var b = [a]; const a = 1;");
        checkSucc("var b = (a); const a = 1;");
        checkSucc("var x = c*b; const c = a+b; const a,b = 1,2;");
        checkSucc("var a = 1; var b = a;");
        checkSucc("var a = b; var b = 1;");

        checkFail("const a = b; var b = 1;");
    }

    @Test
    public void testGlobalVar4() {
        checkFail("var a = b; var b = a;");
        checkFail("var a = c; var b = a; var c = b;");
        var d = "class C{var n*C;} ";
        checkFail(d + "var a=new(C,{n=c});var b=a;var c=b;");
    }

    @Test
    public void testGlobalVar5() {
        checkFail("const a [2]int=[1,2]; var b=a[0];");
        var d = "struct A{id int;} ";
        checkFail(d + "const a A={id=1}; var b=a.id;");
    }

    // struct

    @Test
    public void testStruct0() {
        var def = "struct A {  } ";
        checkSucc(def + "func f() { var a A = {}; }");
        checkSucc(def + "func f() { var a *A; }");
        checkSucc(def + "func f() { var a = new(A); }");
    }

    @Test
    public void testStruct1() {
        var def = "struct A { id int; } ";
        checkSucc(def + "func f() { var a A = { id=123 }; }");
        checkFail(def + "func f() { var a A = { id=true }; }");
    }

    @Test
    public void testStruct2() {
        var def = "struct A { id [4]int; } ";
        checkSucc(def + "func f() { var a A = { id=[1] }; }");
    }

    @Test
    public void testStruct3() {
        var def = "struct A { id [4]uint; } ";
        checkSucc(def + "func f() { var a A = { id=[1] }; }");
    }

    @Test
    public void testStruct6() {
        var def = "struct A { a struct{ id int; }; } ";
        checkSucc(def + "func f() { var a A = { a={id=123} }; }");
        checkFail(def + "func f() { var a A = { a={id=true} }; }");
    }

    @Test
    public void testStructRefer1() {
        checkSucc("func f(a *int32){ var b *int32 = a; }");
        checkSucc("func f(a *int32){ var b *uint32 = a; }");
        checkSucc("func f(a *int32){ var b *int16 = a; }");
        checkFail("func f(a *int32){ var b *int64 = a; }");
        checkSucc("func f(){ var a int32; const b &int32 = a; }");
        checkSucc("func f(){ var a int32; const b &uint32 = a; }");
        checkSucc("func f(){ var a int32; const b &int16 = a; }");
        checkFail("func f(){ var a int32; const b &int64 = a; }");
    }

    @Test
    public void testStructRefer2() {
        var d = "struct A{ a1 int32; } struct B { b1 int8; } ";
        checkSucc(d + "func f(a *A){ var b *B = a; }");
        checkFail(d + "func f(b *B){ var a *A = b; }");
        checkSucc(d + "func f(a *A){ const b &B = a; }");
        checkFail(d + "func f(b *B){ const a &A = b; }");
    }

    @Test
    public void testStructRefer3() {
        var d = "struct A{ a1 [4][3]int32; } ";
        checkSucc(d + "func f(){ var s [48]uint8; const a &A = s; }");
        checkFail(d + "func f(){ var s [47]uint8; const a &A = s; }");
        checkSucc(d + "func f(){ var s [3][4]int32; const r &A = s; }");
        checkSucc(d + "func f(){ var s [3][5]int32; const r &A = s; }");
        checkFail(d + "func f(){ var s [3][3]int32; const r &A = s; }");

        checkSucc(d + "func f(){ var a A; const r [&]uint8 = a; }");
        checkSucc(d + "func f(){ var a A; const r [&][48]uint8 = a; }");
        checkSucc(d + "func f(){ var a A; const r [&][96]uint8 = a; }");
    }

    @Test
    public void testStructRefer4() {
        var d = "struct A{a1 int32;} struct B{b1 int16;} struct C{c1 int64;} class T{var d A;} ";
        checkSucc(d + "func f(a *A){const s &int32 = a.a1; }");
        checkSucc(d + "func f(a *A){const s &int16 = a.a1; }");
        checkFail(d + "func f(a *A){const s &int64 = a.a1; }");

        checkSucc(d + "func f(t *T){const s &B = t.d; }");
        checkFail(d + "func f(t *T){const s &C = t.d; }");
    }

    @Test
    public void testStructField1() {
        var def = "struct A {} union B {}";
        checkSucc(def + "struct R{ a int; b [16]int64; c [0]float; d A; e B; f[2]A; }");
        checkSucc(def + "union R{ a int; b [16]int64; c [0]float; d A; e B; f[2]A; }");
    }

    @Test
    public void testStructField2() {
        checkFail("struct R{ a bool; }");
        var def = "class A{} interface B{} enum C{S,} attribute D{} func E();";
        checkFail(def + "struct R{ a A; }");
        checkFail(def + "struct R{ a B; }");
        checkFail(def + "struct R{ a C; }");
        checkFail(def + "struct R{ a D; }");
        checkFail(def + "struct R{ a E; }");
        checkFail(def + "struct R{ a [2]A; }");
    }

    @Test
    public void testStructField3() {
        var def = "struct A {} union B {} var size = 2; ";
        checkFail(def + "struct R{ a [-1]A; }");
        checkFail(def + "struct R{ a [size]A; }");
        //
//        checkFail(def + "struct R{ a *A; }");
//        checkFail(def + "struct R{ a [2]*A; }");
//        checkFail(def + "struct R{ a &A; }");
//        checkFail(def + "struct R{ a []A; }");
    }

    @Test
    public void testStructField4() {
        checkSucc("struct R{ s struct{ id int8; u union{ v int8; }; }; } func f() { var r R; r.s.u.v = r.s.id; }");
    }

    @Test
    public void testStructField5() {
        var def = "struct R{ id int; } ";
        checkSucc(def + "func f(r *R) { r.id = 1; }");
        checkSucc(def + "func f(r *R) { var v = r.id; }");
        checkSucc(def + "func f() { var r R; r.id = 1; }");
        checkFail(def + "func f(r R) { r.id = 1; }");
    }

    @Test
    public void testStructField6() {
        checkSucc("struct A{} struct B{a A;}");
        checkFail("struct A{f B;} struct B{f A;}");
        checkFail("struct A{f C;} struct B{f A;} struct C{f B;}");
        checkFail("struct A{f B;} struct B{f [2]A;}");
        checkFail("struct A{f B;} struct B{f [2][4]A;}");
        checkFail("struct A{f C;} struct B{f A;} struct C{f [3]B;}");
        checkFail("struct A{f [7]C;} struct B{f A;} struct C{f [3]B;}");
    }

    @Test
    public void testStructBitfield1() {
        checkSucc("struct A{id(7) int8;}");
        checkFail("struct A{id(0) int8;}");
        checkFail("struct A{id(9) int8;}");
        checkFail("struct A{id(-0) int8;}");
        checkSucc("struct A{id(6),type(2) int;}");
        checkFail("struct A{id(true) int8;}");
        checkFail("struct A{id(3.3) int8;}");
        checkFail("struct A{id(\"tr\") int8;}");
    }

    @Test
    public void testStructBitfield2() {
        for (var p : Primitive.values()) {
            if (p.isInteger()) {
                checkSucc("struct A{id(3) %s;}".formatted(p));
            } else {
                checkFail("struct A{id(3) %s;}".formatted(p));
            }
        }
    }

    @Test
    public void testStructBitfield3() {
        checkSucc("struct A{id(X) int;} const X=8;");
        checkFail("struct A{id(X) int;} const X=80;");
        checkSucc("struct A{id(Y) int;} const X=8; const Y=X;");
        checkFail("struct A{id(X) int;} var X=8;");
        checkFail("struct A{id(X) int;} const X=1.3;");
        checkFail("struct A{id(X) int;} const X=true;");
        checkFail("struct A{id(X) int;} const X=\"str\";");
        checkFail("struct A{id(X) int;} var X=false;");
        checkFail("struct A{id(X) int;} var X=false;");
    }

    @Test
    public void testStructBitfield4() {
        checkFail("struct D{} struct A{id(X) int;} const X D={};");
        checkFail("union D{}  struct A{id(X) int;} const X D={};");
        checkFail("class D{}  struct A{id(X) int;} const X D={};");
        checkFail("enum D{S,} struct A{id(X) int;} const X D={};");
        checkFail("func D();  struct A{id(X) int;} const X D={};");
    }

    @Test
    public void testStructBitfield5() {
        checkFail("struct D{w int;} struct A{id(X.w) int;} const X D={w=10};");
    }

    //

    @Test
    public void testMappable1() {
        for (var a : Primitive.values()) {
            for (var b : Primitive.values()) {
                var code = "func f(a*%s){ var b*%s = a; }".formatted(a, b);
                if (a.isBool() != b.isBool()) {
                    checkFail(code);
                } else if (a.isBool()) {
                    checkSucc(code);
                } else {
                    if (a.width >= b.width) {
                        checkSucc(code);
                    } else {
                        checkFail(code);
                    }
                }
            }
        }
    }

    @Test
    public void testMappable2() {
        checkSucc("func f(){var a [4]uint8; const b &uint32 = a;}");
        checkSucc("func f(){var a [4]uint8; const b &uint16 = a;}");
        checkFail("func f(){var a [4]uint8; const b &uint64 = a;}");
        checkSucc("func f(){var a [8]uint8; const b &uint64 = a;}");
        checkSucc("func f(){var a [9]uint8; const b &uint64 = a;}");
    }

    @Test
    public void testMappable3() {
        var d = "struct A{v int32;} struct B{v int64;} struct C{v1,v2 int64;} ";
        checkSucc(d + "func f(x *A){var y *int32 = x;}");
        checkFail(d + "func f(x *A){var y *int64 = x;}");
        checkSucc(d + "func f(x *B){var y *int64 = x;}");
        checkSucc(d + "func f(x *C){var y *int64 = x;}");

        checkFail(d + "func f(x *A){var y *B = x;}");
        checkSucc(d + "func f(x *B){var y *A = x;}");
        checkFail(d + "func f(x *B){var y *C = x;}");
        checkSucc(d + "func f(x *C){var y *B = x;}");

        checkSucc(d + "func f(){var x [10]int8; const y &B = x;}");
        checkFail(d + "func f(){var x [10]int8; const y &C = x;}");
        checkSucc(d + "func f(){var x [16]int8; const y &C = x;}");
        checkSucc(d + "func f(){var x [20]int8; const y &C = x;}");
    }

    @Test
    public void testMappable4() {
        checkSucc("func f(x *int32){ const y [*]int32 = x;}");
        checkFail("func f(x *int32){ const y [*]*int32 = x;}");
        checkSucc("func f(x *int32){ const y [*]uint8 = x;}");
        checkFail("func f(x *int32){ const y [*]*uint8 = x;}");
        var d = "struct A{v int32;} struct B{v int64;} struct C{v1,v2 int64;} ";
        checkSucc(d + "func f(x *A){ const y [*]int32 = x;}");
        checkFail(d + "func f(x *A){ const y [*]*int32 = x;}");
        checkSucc(d + "func f(x *B){ const y [*]int32 = x;}");
        checkFail(d + "func f(x *B){ const y [*]*int32 = x;}");
        checkSucc(d + "func f(x *C){ const y [*]int32 = x;}");
        checkFail(d + "func f(x *C){ const y [*]*int32 = x;}");
        checkSucc(d + "func f(x *B){ const y [*]A = x;}");
        checkFail(d + "func f(x *B){ const y [*]*A = x;}");
        checkSucc(d + "func f(x *B){ const y [*]B = x;}");
        checkFail(d + "func f(x *B){ const y [*]*B = x;}");
        checkSucc(d + "func f(x *B){ const y [*]C = x;}");
        checkFail(d + "func f(x *B){ const y [*]*C = x;}");
    }

    @Test
    public void testMappable5() {
        checkSucc("func f(a [*]*int){var v [*]*int = a;}");
        checkFail("func f(a [*]*int){var v [*]*int8 = a;}");
        var d = "struct S{} interface I{} class C{} enum E{T,} func F(); ";
        checkFail(d + "func f(a [*]S){var v [*]*int = a;}");
        checkFail(d + "func f(a [*]*S){var v [*]*int = a;}");
        checkFail(d + "func f(a [*]*I){var v [*]*int = a;}");
        checkFail(d + "func f(a [*]C){var v [*]*int = a;}");
        checkFail(d + "func f(a [*]*C){var v [*]*int = a;}");
        checkFail(d + "func f(a [*]E){var v [*]*int = a;}");
        checkFail(d + "func f(a [*]F){var v [*]*int = a;}");
    }

    @Test
    public void testMappable6() {
        checkFail("func f(a [2]int){var v *int = a;}");

        checkSucc("func f(a [*]int){var v *int = a;}");
        checkFail("func f(a [*]*int){var v *int = a;}");
        checkSucc("func f(a [*][2]int){var v *int = a;}");
        checkFail("func f(a [*][*]int){var v *int = a;}");
        checkFail("func f(a [*][2]*int){var v *int = a;}");
        checkFail("func f(a [*][2][*]int){var v *int = a;}");

        checkSucc("func f(a [*][2][3]int){var v *int = a;}");
        checkFail("func f(a [*][2][3]*int){var v *int = a;}");
        checkFail("func f(a [*][2][3][*]int){var v *int = a;}");
    }

    //

    @Test
    public void testFieldInit1() {
        var d = "struct S{tag uint;} class C{var s S; var id int;} ";
        checkSucc(d + "var s S = {tag=1}; ");
        checkFail(d + "var s S = {id=1}; ");
        checkSucc(d + "var c C = {id=3}; ");
        checkFail(d + "var c C = {tag=3}; ");
        checkSucc(d + "var c C = {s={tag=5}}; ");
        checkFail(d + "var c C = {s={id=5}}; ");

        checkSucc(d + "var s = S{tag=1}; ");
        checkSucc(d + "var c = C{id=3}; ");
        checkSucc(d + "var c = C{s={tag=5}}; ");
    }

    @Test
    public void testFieldInit2() {
        var d = "struct S{tag [3]uint;} class C{var s [6]S; var vv [7]int;} ";
        checkSucc(d + "var s S = {tag=[1,3]}; ");
        checkSucc(d + "var c C = {vv=[3,5]}; ");
        checkSucc(d + "var c C = {s=[]}; ");
        checkSucc(d + "var c C = {s=[{}]}; ");
        checkSucc(d + "var c C = {s=[{tag=[5]}]}; ");
        checkSucc(d + "var c C = {s=[{tag=[5]}, {tag=[7]} ]}; ");
    }

    @Test
    public void testFieldInit3() {
        var d = "class A{var id int;} class B{var id int;} ";
        checkSucc(d + "var v A = {id=1};");
        checkSucc(d + "var v A = A{id=1};");
        checkFail(d + "var v A = B{id=1};");
        checkSucc(d + "var v B = {id=1};");
        checkSucc(d + "var v B = B{id=1};");
        checkFail(d + "var v B = A{id=1};");

        d = "struct A{id int;} struct B{id int;} ";
        checkSucc(d + "var v A = {id=1};");
        checkSucc(d + "var v A = A{id=1};");
        checkFail(d + "var v A = B{id=1};");
        checkSucc(d + "var v B = {id=1};");
        checkSucc(d + "var v B = B{id=1};");
        checkFail(d + "var v B = A{id=1};");
    }

    // enum

    @Test
    public void testEnum1() {
        checkSucc("enum S{A=1,}");
        checkSucc("enum S{A,B=9,}");
        checkFail("enum S{A=3.14,}");
        checkFail("enum S{A=false,}");
        checkFail("enum S{A=nil,}");
        checkFail("enum S{A=\"A\",}");
    }

    @Test
    public void testEnum2() {
        checkSucc("enum S{A,B,} func f() { var s S; s=S.A; }");
        checkFail("enum S{A,B,} func f() { var s int; s=S.A; }");
        checkFail("enum S{A,B,} func f() { var s S; s=S.C; }");
        checkSucc("enum S{A,B,} func f() { var s *S; }");
        checkSucc("enum S{A,B,} func f() { var s = new(S); }");
    }

    @Test
    public void testEnum3() {
        var d = "enum S{A,B=4,C,}";

        checkSucc(d + "func f(s S) { var v = S.B.id; }");
        checkSucc(d + "func f(s S) { var v = S.B.name; }");
        checkSucc(d + "func f(s S) { var v = S.B.value; }");

        checkSucc(d + "func f(s S) { var v = s.id; }");
        checkSucc(d + "func f(s S) { var v = s.name; }");
        checkSucc(d + "func f(s S) { var v = s.value; }");
    }

    @Test
    public void testEnum4() {
        var d = "enum A{X,} enum B{X,} ";
        checkFail(d + "func f(a A){var b B =a;}");
    }

    // statement

    @Test
    public void testStatementDeclaration1() {
        checkSucc("func f() { var a,b,c int; }");
        checkSucc("func f() { var a,b,c int = 1,2,3; }");
        checkFail("func f() { var a,b,c int = 1,2; }");
        checkFail("func f() { var a,b,c int = 1,2,3,4; }");
        checkFail("func f() { var a,b,c bool = 1,2,3; }");
        checkSucc("func f() { var a,b,c = 1,2,3; }");
    }

    @Test
    public void testStatementDeclaration2() {
        checkSucc("func f() { var a,b,c = 1,2,3; }");
        checkSucc("func f() { var a,b,c = 1,true,0.3; }");
    }

    @Test
    public void testStatementLabel() {
        checkFail("func f() { jjj:var a int;}");
        checkFail("func f() { jjj:ggyy:var a int;}");
    }

    @Test
    public void testStatementAssignment() {
        var def = "class A{ var id int; }";
        checkSucc(def + "func f(a *A) { var v int; v,a.id = 1,2; }");
        checkFail(def + "func f(a *A) { var v int; v,a.id = 1; }");
        checkFail(def + "func f(a *A) { var v int; v,a.id = 1,2,3; }");
        checkSucc(def + "func f(a *A, r [*]int) { var v int; v,a.id,r[2] = 1,2,3; }");
        checkFail(def + "func f(a *A) { a.type=2; }");
        checkFail(def + "func f() { var a int; a.id=1; }");
        checkFail(def + "func f() { var a int; a[0]=1; }");
    }

    @Test
    public void testStatementScope() {
        checkFail("func f(v int) { var v int; }");
        checkSucc("func f() { var v int; { var v int; } }");
        checkSucc("func f(v int) { { var v int; } }");
        checkSucc("func f(v int) { {var v int;} }");
        checkSucc("func f(v int) { {var v int; {var v int;}} }");
    }

    @Test
    public void testStatementIf() {
        checkSucc("func f(){if(true){var v int;}}");
        checkSucc("func f(v bool){if(v){var v int;}}");
        checkSucc("func f(v int){if(v>0){var v int;}}");
        checkSucc("func f(v int){if(var v int=0;v>0){var v int;}}");
        checkSucc("func f(v int){if(var v int=0;v>0){{var v int;}}}");
        checkSucc("func f(v int,a func()){if(v<0)a();else a();}");
        checkSucc("func f(v int,a func()){if(v<0){a();}else{a();}}");
        checkFail("func f(v int){if(v){var v int;}}");
        checkFail("func f(v float){if(v){var v int;}}");
        checkFail("func f(v *int){if(v){var v int;}}");
    }

    @Test
    public void testStatementSwitch() {
        var d = "class C{} enum E{U,V,W,} const S1K = 1<<10; var S1M = 1<<20;";
        checkSucc(d + "func f(v int){switch(v){ case 1{} default{} }}");
        checkSucc(d + "func f(v int){switch(v){ case S1K{} default{} }}");
        checkFail(d + "func f(v int){switch(v){ case S1M{} default{} }}");
        checkSucc(d + "func f(v int){ const K1 = S1K; switch(v){ case K1{} default{} }}");
        checkFail(d + "func f(v int, c int){switch(v){ case c{} }}");
        checkSucc(d + "func f(v int){switch(v){ case 1{} case 2{} default{} }}");
        checkFail(d + "func f(v int){switch(v){ case 1{} }}");

        checkSucc(d + "func f(v E){switch(v){ case U{} case W,V{} }}");
        checkSucc(d + "func f(v E){switch(v){ case U{} default{} }}");
        checkFail(d + "func f(v E){switch(v){ case U{} }}");
        checkFail(d + "func f(v E){switch(v){ case U,W,V{} default{} }}");

        checkSucc(d + "func f(v A){switch(v){ case A1,A2{} }} enum A{A1,A2,}");
        checkFail(d + "func f(v E){switch(v){ case A1,A2{} }} enum A{A1,A2,}");

        checkFail(d + "func f(v [2]E){ switch(v){ case U,W,V{} } }");
        checkFail(d + "func f(v bool){ switch(v){ case U,W,V{} } }");
        checkFail(d + "func f(v C){ var c C; switch(v){ case c{} default{} } }");
    }

    @Test
    public void testStatementFor1() {
        checkSucc("func f(){var i=0;for(i<10){}i+=1;}");
        checkSucc("func f(n int){var i=0;for(i<n){}i+=1;}");

        checkSucc("func f(){var i=0;for(i=0;i<10;i+=1){}}");
        checkSucc("func f(){for(var i=0;i<10;i+=1){}}");
        checkSucc("func f(n int){for(var i=0;i<n;i+=1){}}");
        checkSucc("func f(n int){for(var i=0;i<n;i+=1)i+=1;}");

        checkSucc("func f(){var i=0;for(i=0;i<10;i+=1){}}");
        checkSucc("func f(){var i=0;for(i+=0;i<10;i+=1){}}");
    }

    @Test
    public void testStatementFor2() {
        checkSucc("func f(a [4]int){for(v:a){}}");
        checkSucc("func f(a [4]int){for(v:a){var u=v;}}");
        checkSucc("func f(a [4]int){for(v:a){var v=a;}}");
        checkSucc("func f(a [*]int){for(v:a){}}");
        checkSucc("func f(a [*]int){for(v:a){var u=v;}}");
        checkSucc("func f(a [*]int){for(v:a){var v=a;}}");

        checkSucc("func f(a [4]int){for(i,v:a){}}");
        checkSucc("func f(a [4]int){for(i,v:a){var u=i;}}");
        checkSucc("func f(a [4]int){for(i,v:a){var i=a;}}");
        checkSucc("func f(a [*]int){for(i,v:a){}}");
        checkSucc("func f(a [*]int){for(i,v:a){var u=i;}}");
        checkSucc("func f(a [*]int){for(i,v:a){var i=a;}}");

    }

    @Test
    public void testStatementFor3() {
        var d = "enum E{A,B,C,D,E,F,G,} class T{} ";
        checkSucc(d + "func f(a [4]E){for(v:a){}}");
        checkSucc(d + "func f(){for(v:E){}}");
        checkFail(d + "func f(){for(v,u:E){}}");
        checkFail(d + "func f(){for(t:T){}}");
        checkFail(d + "func f(a *int){for(t:a){}}");
    }


    @Test
    public void testStatementThrow1() {
        var d = "func a(){} enum E{U,V,W,} const S1K = 1<<10; var S1M = 1<<20;";
        checkSucc(d + "func f(v E){ throw a; }");
        checkSucc(d + "func f(v E){ throw v; }");
        checkSucc(d + "func f(v E){ throw v; }");
    }

    @Test
    public void testStatementTry1() {
        var d = "func A(); enum E{U,V,W,} class C{} interface I{}";
        checkSucc(d + "func f(v E){ try{}final{} }");
        checkSucc(d + "func f(v E){ try{var v E;}final{} }");
        checkSucc(d + "func f(v E){ try{}final{var v E;} }");
        checkSucc(d + "func f(v E){ try{}catch(v E){} }");
        checkSucc(d + "func f(v E){ try{var v E;}catch(v E){} }");
        checkSucc(d + "func f(v E){ try{}catch(v E){var v E;} }");
    }

    @Test
    public void testStatementLoop() {
        checkSucc("func f(){for(true){}}");
        checkSucc("func f(){var i int=10; for(i<0){i-=0;}}");
        checkSucc("func f(){for(var i=10;i<100;i+=1){}}");
        checkSucc("func f(){for(var i=10;i<100;i+=1){var i = 0;}}");
        checkSucc("func f(){for(true){break;}}");
        checkFail("func f(){for(true){}break;}");
        checkSucc("func f(){for(true){continue;}}");
        checkFail("func f(){for(true){}continue;}");
    }

    @Test
    public void testStatementGoto() {
        checkSucc("func f(){var i=0; jjj:i+=1; if(i<10) goto jjj;}");
        checkFail("func f(){var i=0; jjj:i+=1; if(i<10) goto jj;}");
    }

    @Test
    public void testStatementReturnType1() {
        checkSucc("func f(){return;}");
        checkFail("func f(){return 1;}");
        checkSucc("func f()int{return 1;}");
        checkFail("func f()int{return ;}");
        checkFail("func f()int{return false;}");

        checkSucc("func f()int{jjj:return 1;}");

        checkSucc("func f(v int)int{throw v;}");
    }

    @Test
    public void testStatementReturnPath1() {
        checkSucc("func f()int{return 1;}");

        checkFail("func f()int{}");
        checkFail("func f()int{{}}");
        checkFail("func f()int{{{}}}");

        checkSucc("func f()int{{return 1;}}");
        checkSucc("func f()int{{{return 1;}}}");

        checkSucc("func f()int{{}return 1;}");
        checkSucc("func f()int{{{}return 1;}}");

        checkFail("func f()int{return 1;{}}");
        checkFail("func f()int{{return 1;{}}}");
    }

    @Test
    public void testStatementReturnPath2() {
        checkSucc("func f()int{if(true){return 1;}else{return 0;}}");
        checkSucc("func f()int{if(true){return 1;}else{}}");
        checkFail("func f()int{if(false){return 1;}else{}}");
        checkSucc("func f()int{if(true){return 1;}}");
        checkFail("func f()int{if(true){}else{return 0;}}");
        checkFail("func f()int{if(true){}}");

        checkSucc("func f(c bool)int{if(c){return 1;}else{return 0;}}");
        checkFail("func f(c bool)int{if(c){return 1;}else{}}");
        checkFail("func f(c bool)int{if(c){return 1;}}");
        checkFail("func f(c bool)int{if(c){}else{return 0;}}");
        checkFail("func f(c bool)int{if(c){}}");
    }

    @Test
    public void testStatementReturnPath3() {
        var d = "enum E{A,B,} ";
        checkSucc(d + "func f(e E)int{switch(e){case A{return 1;} case B{return 0;}}}");
        checkSucc(d + "func f(e E)int{switch(e){case A{return 1;} default{return 0;}}}");
        checkFail(d + "func f(e E)int{switch(e){case A{return 1;} case B{return 0;}}{}}");
        checkFail(d + "func f(e E)int{switch(e){case A{return 1;} case B{}}}");
        checkFail(d + "func f(e E)int{switch(e){case A{} default{return 0;}}}");
    }

    @Test
    public void testStatementReturnPath4() {
        checkSucc("func f()int{for(true){}}");
        checkFail("func f()int{for(false){}}");
        checkFail("func f()int{for(false){return 1;}}");
        checkSucc("func f()int{for(false){}return 1;}");

        checkSucc("func f()int{for(var i=0;true;i+=1){}}");
        checkFail("func f()int{for(var i=0;false;i+=1){return 1;}}");
        checkFail("func f()int{for(var i=0;false;i+=1){}}");
        checkSucc("func f()int{for(var i=0;false;i+=1){}return 1;}");

        checkFail("func f(a [4]int)int{for(v:a){}}");
    }

    @Test
    public void testStatementReturnPath5() {
        var d = "func c(){} ";
        checkSucc(d + "func f()int{try{return 1;}final{}}");
        checkFail(d + "func f()int{try{}final{}}");
        checkSucc(d + "func f()int{try{}final{return 1;}}");

        checkSucc(d + "func f()int{try{return 1;}catch(e int){return 1;}}");
        checkFail(d + "func f()int{try{return 1;}catch(e int){}}");
        checkFail(d + "func f()int{try{}catch(e int){return 1;}}");
        checkSucc(d + "func f()int{try{return 1;}catch(e int){}final{return 1;}}");
        checkSucc(d + "func f()int{try{}catch(e int){return 1;}final{return 1;}}");
    }

    //


    //

    static InputStream getSample(String name) {
        return SampleParseTest.class.getResourceAsStream("/analysis/" + name + ".feng");
    }

    static void parseSample(String name) {
        System.out.printf("[test]%s.feng\n", name);
        try (var is = getSample(name)) {
            Assertions.assertNotNull(is);
            var src = BaseParseTest.doParseFile(is);
            var ctx = new GlobalSymbolContext(src.table());
            new SemanticAnalysis(ctx).visit(src);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    public void testSample() {
        parseSample("string");
        parseSample("queue");
        parseSample("hashmap");
    }

}
