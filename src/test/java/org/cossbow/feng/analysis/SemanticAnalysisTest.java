package org.cossbow.feng.analysis;

import org.cossbow.feng.ast.BinaryOperator;
import org.cossbow.feng.ast.Source;
import org.cossbow.feng.ast.TypeDomain;
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
                .filter(d -> d.derived)
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
        checkFail("class A{ var id int; } func m(){ this.id = 0; }");
        checkFail("class A{ var id int; } func m(){ var id = this; }");
    }

    @Test
    public void testThis3() {
        checkSucc("class A{ func m(){ var a A; a = this; } }");
        checkSucc("class A{ func m(){ const a &A = this; } }");
        checkFail("class A{ func m(){ const a *A = this; } }");
        checkFail("class A{ func m(){ var b B = this; } } class B{}");
    }

    //

    @Test
    public void testInterface1() {
        var def = new StringBuilder();
        var parts = new StringBuilder();
        for (char i = 'A'; i <= 'Z'; i++) {
            def.append("interface ").append(i).append(" {}");
            parts.append(i).append(';');
            checkSucc(def + "interface Alpha{%s}".formatted(parts));
        }
    }

    @Test
    public void testInterface2() {
        checkFail("interface A{B;} interface B{A;}");
        checkFail("interface A{C;} interface B{A;} interface C{B;}");
        checkFail("interface A{C;} interface B{A;} interface C{B;}");
    }

    @Test
    public void testInterfaceMethod1() {
        checkSucc("interface A{ run(); } func f(a *A) { a.run(); }");
        checkSucc("interface A{ run(); } interface B{A;} func f(a *B) { a.run(); }");
        checkSucc("interface A{ run(); } interface B{A;} interface C{B;} func f(a *C) { a.run(); }");
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
        var def = "interface I { get() int; } ";
        checkFail(def + "class F (I) {}");
        checkSucc(def + "class F (I) { func get() int { return 0; } }");
        checkFail(def + "class F (I) { func get() int8 { return 0; } }");
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
        var def = "class A{} class B:A{} interface I { get(*A, int) (*A, bool); } ";
        checkSucc(def + "class F (I) { func get(a *A, i int) (*A, bool) { return a, i>0; } }");
        checkFail(def + "class F (I) { func get(a *A) (*A, bool) { return a, i>0; } }");
        checkFail(def + "class F (I) { func get(a *A, i int) (*A) { return a; } }");
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
        checkSucc("func F()int; func f()int{} func m(){ var c F = f; }");
        checkFail("func F()int; func f()int8{} func m(){ var c F = f; }");
        checkSucc("func F()int; func m(f func()int){ var c F = f; }");
        checkFail("func F()int; func m(f func()int8){ var c F = f; }");
    }

    @Test
    public void testPrototype2() {
        var def = "class A{} class B:A{}";
        checkSucc(def + "func F(*A); func f(*A){} func m(){ var c F = f; }");
        checkFail(def + "func F(*A); func f(*B){} func m(){ var c F = f; }");
        checkSucc(def + "func F()*A; func f()*A{} func m(){ var c F = f; }");
        checkSucc(def + "func F()*A; func f()*B{} func m(){ var c F = f; }");
        checkSucc(def + "func F()*A; func m(f func()*A){ var c F = f; }");
        checkSucc(def + "func F()*A; func m(f func()*B){ var c F = f; }");
    }

    @Test
    public void testPrototype3() {
        checkSucc("func f(){} func m(f func(int)){ f(1); }");
        checkSucc("func F(); func m(f F){ f(); }");
        checkFail("func f(){} func m(f int){ f(); }");
    }

    @Test
    public void testPrototype4() {
        var def = "class A{} class B:A{}";
        checkSucc(def + "func m(f func(a*A)){f(new(B));}");
        checkSucc(def + "func F(a*A); func m(f F){ f(new(B)); }");
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
                var c = "func f(a,b %s) { var i bool; i = a %s b; ;}"
                        .formatted(p, s);
                checkSucc(c);
            }
        }
    }

    @Test
    public void testUnaryExpression1() {
        checkSucc("func f(){ var a int = +10; }");
        checkSucc("func f(){ var a int = -10; }");
        checkSucc("func f(){ var a int = !10; }");
        checkSucc("func f(){ var a bool = 1 >0; }");
        checkFail("func f(){ var a float= !1.3; }");

        checkSucc("func f(){ const i = 12; var a int = +i; }");
        checkSucc("func f(){ const i = 12; var a int = -i; }");
        checkSucc("func f(){ const i = 12; var a int = !i; }");
        checkSucc("func f(){ const i = 12; var a bool = 1 >i; }");
        checkFail("func f(){ const i = 1.2; var a = !i; }");
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
        checkFail(d + "func f(s *C){ var e = s?(*A); }");
    }

    @Test
    public void testAssertExpression3() {
        var d = "interface A{} interface B{A;} interface C{}";
        checkSucc(d + "func f(s *B){ var e = s?(*A); }");
        checkFail(d + "func f(s *C){ var e = s?(*A); }");
    }

    @Test
    public void testAssertExpression4() {
        var d = "interface A{} class B(A){} class C{}";
        checkFail(d + "func f(s B){ var e = s?(*A); }");
        checkFail(d + "func f(s *B){ var e = s?(A); }");
        checkFail(d + "func f(s *B){ var e = s?(C); }");
        checkFail(d + "func f(s *B){ var e = s?(int); }");
        checkFail(d + "func f(s *B){ var e = s?(int8); }");
        checkFail(d + "func f(s *B){ var e = s?(bool); }");
        checkFail(d + "func f(s *B){ var e = s?(*ram); }");
    }

    @Test
    public void testAssertExpression5() {
        checkFail("func f(s *Object){ var e = s?(int); }");
        checkFail("func f(s *Object){ var e = s?(*ram); }");
        var d = "enum E{S,T,} struct S{} func F();";
        checkFail(d + "func f(s *Object){ var e = s?(E); }");
        checkFail(d + "func f(s *Object){ var e = s?(F); }");
        checkFail(d + "func f(s *Object){ var e = s?(S); }");
    }

    @Test
    public void testAssertExpression6() {
        var d = "class A{} ";
        checkSucc(d + "func f(o *Object){ var a = o?(*A); }");
        checkFail(d + "func f(o *Object){ var a = o?(&A); }");
        checkFail(d + "func f(o &Object){ var a = o?(*A); }");
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
    public void testCallExpression1() {
        var d = "class A{var id int; func run(){}} func go()int{} const pi = 3.14159;";
        checkSucc(d + "func t() { go(); }");
        checkFail(d + "func t() { var v bool = go(); }");
        checkSucc(d + "func t() { var i=float(pi); }");
        checkFail(d + "func t(i int) { i(); }");
        checkFail(d + "func t() { pi(); }");
        checkSucc(d + "func t(a A) { a.run(); }");
        checkFail(d + "func t(a A) { a(); }");
        checkFail(d + "func t(a A) { a.id(); }");
        checkFail(d + "func t(r *rom) { r(); }");
    }

    @Test
    public void testNewExpression1() {
        var d = "func A(); enum E{U,V,W,} class C{} interface I{}";
        checkFail(d + "func f(){var v = new(A);}");
        checkFail(d + "func f(){var v = new(E);}");
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
        checkFail(d + "func f(){var b=new(C,{t=1});}");
        checkSucc(d + "func f(a C){var b=new(C,a);}");
        checkSucc(d + "func f(a *C){var b=new(C,a);}");
        checkSucc(d + "func f(a &C){var b=new(C,a);}");
    }

    @Test
    public void testNewExpression3() {
        var d = "class C{const s int;} ";
        checkSucc(d + "func f(){var b=new([2][*]int);}");
        checkSucc(d + "func f(){var b=new([2][*]int, [nil]);}");
        checkFail(d + "func f(){var b=new([2][*]int, [1]);}");

        checkSucc(d + "func f(){var b=new([2]C,[{s=1}]);}");
        checkSucc(d + "func f(a C){var b=new([2]C,[a]);}");
        checkFail(d + "func f(a *C){var b=new([2]C,[a]);}");
        checkSucc(d + "func f(a *C){var b=new([2]*C,[a]);}");
        checkFail(d + "func f(a &C){var b=new([2]C,[a]);}");
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
        checkSucc("func f() { const a=\"true\"; const c *rom = a; }");
        checkFail("func f() { const a=\"true\"; const c &rom = a; }");
        checkFail("func f() { const a=\"true\"; const c *ram = a; }");
    }

    //

    @Test
    public void testConstValue1() {
        checkFail("func f(v int) { const i int; i = v; }");
        checkFail("func f(v int) { v = 0; }");
        checkFail("func f(v [4]int) { v[0] = 0; }");
    }

    @Test
    public void testConstValue3() {
        checkFail("class Dev { const id int; func f() { id = 0; } }");
        checkFail("class Dev { var id int; } func f(d Dev) { d.id = 0; }");
        checkFail("class Dev { var id int; } class Disk { var dev Dev; } func f(d Disk) { d.dev.id = 0; }");
        checkFail("class Dev { var id int; } func f(d [4]Dev) { d[0].id = 0; }");
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
        checkFail("func f(v *bool) {}");
        checkFail("struct ID{} func f(v *ID) {}");
        checkFail("union ID{} func f(v *ID) {}");
        checkFail("enum ID{A,} func f(v *ID) {}");
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
        var def = "interface I{} interface J{I;} class A(J){} ";
        checkSucc(def + "func f(b *A) { var a *I = b; }");
        checkFail(def + "func f(b *I) { var a *A = b; }");
    }

    @Test
    public void testTransferable6() {
        var def = "interface I{} interface J{I;} ";
        checkSucc(def + "func f(b *J) { var a *I = b; }");
        checkFail(def + "func f(b *I) { var a *J = b; }");
    }

    @Test
    public void testTransferable7() {
        var def = "interface I{} interface J{I;} interface K{J;}";
        checkSucc(def + "func f(b *K) { var a *I = b; }");
        checkFail(def + "func f(b *I) { var a *K = b; }");
    }

    @Test
    public void testPhantomRefer1() {
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
    public void testPhantomRefer2() {
        var d = "interface I{} class A(I){} class B:A{} ";
        checkSucc(d + "func f() { var a A; const r &A = a; }");
        checkSucc(d + "func f() { var b B; const r &A = b; }");
        checkSucc(d + "func f() { var a A; const r &I = a; }");
        checkSucc(d + "func f() { var b B; const r &I = b; }");

        checkSucc(d + "func f() { const a *A=nil; const r &A = a; }");
        checkSucc(d + "func f() { const b *B=nil; const r &A = b; }");
        checkSucc(d + "func f() { const a *A=nil; const r &I = a; }");
        checkSucc(d + "func f() { const b *B=nil; const r &I = b; }");

        checkFail(d + "func f() { var a *A; const r &A = a; }");
        checkFail(d + "func f() { var b *B; const r &A = b; }");
        checkFail(d + "func f() { var a *A; const r &I = a; }");
        checkFail(d + "func f() { var b *B; const r &I = b; }");

        checkFail(d + "func f() { const a A; const r &A = a; }");
        checkFail(d + "func f() { const b B; const r &A = b; }");
        checkFail(d + "func f() { const a A; const r &I = a; }");
        checkFail(d + "func f() { const b B; const r &I = b; }");
    }

    @Test
    public void testPhantomRefer3() {
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
    public void testPhantomRefer4() {
        var d = "interface I{} class A(I){} class B:A{} ";
        checkSucc(d + "func f(c func(a &B)) { var a B;c(a); }");
        checkSucc(d + "func f(c func(a &A)) { var a B;c(a); }");
        checkSucc(d + "func f(c func(a &I)) { var a B;c(a); }");
        checkFail(d + "func f(c func(a &B)) { var a *B;c(a); }");
        checkFail(d + "func f(c func(a &A)) { var a *B;c(a); }");
        checkFail(d + "func f(c func(a &I)) { var a *B;c(a); }");

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
    public void testPhantomRefer5() {
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
    public void testPhantomRefer6() {
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
    public void testPhantomRefer7() {
        var d = "interface I{} interface J{I;} class R{const j *J;} ";
        checkSucc(d + "func f(r *R){ const i &I = r.j; }");
        d = d + "class T{var r R;}";
        checkSucc(d + "func f(t *T){ const i &I = t.r.j; }");
    }

    @Test
    public void testPhantomRefer8() {
        var d = "class A{} ";
        checkSucc(d + "func f(a &A){ const r &A = a; }");
        checkFail(d + "func f(a &A){ var r &A = a; }");
        checkFail(d + "func f(a &A){ var r *A = a; }");
        checkFail(d + "func f(a &A){ var r A = a; }");
    }

    @Test
    public void testPhantomRefer9() {
        var d = "class A{} class B{var ar [4]A; const sz [*]A;} ";
        checkSucc(d + "func f(b *B) { const a &A = b.ar[0]; }");
        checkFail(d + "func f(b B) { const a &A = b.ar[0]; }");
        checkFail(d + "func f(b *B) { const a &A = b.sz[0]; }");
        checkFail(d + "func f(b B) { const a &A = b.sz[0]; }");
    }

    //

    @Test
    public void testArray1() {
        checkSucc("func t() { var a = [1,2,3]; }");
        checkFail("func t() { var a = []; }");
    }

    @Test
    public void testArray2() {
        checkSucc("func t() { var a [2]int = []; }");
        checkFail("func t() { var a [*]int = []; }");
    }

    @Test
    public void testArray3() {
        checkFail("func t() { var a [2]int = nil; }");
        checkSucc("func t() { var a [*]int = nil; }");
    }

    @Test
    public void testArray4() {
        checkSucc("func t() { var a [4]int = [1,2,3]; }");
        checkSucc("func t() { var a [4]int = [1,2,3,4]; }");
        checkFail("func t() { var a [4]int = [1,2,3,4,5]; }");
    }

    @Test
    public void testArray5() {
        checkSucc("func t() { var a [*]int = new([8]int); }");
        checkSucc("func t() { var a [*]int = new([4]int, [1,2,3]); }");
        checkSucc("func t() { var a [*]int; a = new([8]int); }");
        checkSucc("func t() { var a [*]int; a = new([4]int, [1,2,3]); }");
        checkFail("func t() { var a [*]int; a = new([4]int, {}); }");
    }

    @Test
    public void testArray6() {
        checkSucc("func t() { var a = new([8]int); }");
        checkSucc("func t() { var a = new([4]int, [1,2,3]); }");
        checkFail("func t() { var a = new([4]int, {}); }");
    }

    @Test
    public void testArray7() {
        checkFail("func t() { var a [4]int = new([4]int); }");
        checkFail("func t() { var a [*]int = [1,2,3]; }");
        checkFail("func t() { var a [4]int; a = new([4]int); }");
        checkFail("func t() { var a [*]int; a = [1,2,3]; }");
    }

    @Test
    public void testArray8() {
        checkSucc("func t(s int) { var a [*]int = new([s]int); }");
        checkFail("func t(s bool) { var a [*]int = new([s]int); }");
        checkSucc("func t(s int) { var l = s; var a [*]int = new([l]int); }");
        checkFail("func t() { var a [*]int = [1,2,3]; }");
        checkFail("func t() { var a [4]int; a = new([4]int); }");
        checkFail("func t() { var a [*]int; a = [1,2,3]; }");
    }

    @Test
    public void testArray9() {
        checkSucc("func t(s [*]int) { s[0] = s[1]; }");
        checkSucc("func t(s [*]int, i int) { s[i] = s[1]; }");
        checkFail("func t(s int) { s[0] = s[1]; }");
        checkFail("func t(s [4]int) { s[0] = s[1]; }");
        checkFail("func t(s [*]int) { s[true] = 1; }");
    }

    @Test
    public void testArray10() {
        checkSucc("func f(){var s [*][2]int;}");
        checkFail("func f(){var s [&][2]int;}");

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
    public void testArray11() {
        var d = "class C{} ";
        checkSucc(d + "func f(){var s [5]C;}");
        checkSucc(d + "func f(){var s [5]*C;}");
        checkFail(d + "func f(){var s [5]&C;}");
        checkSucc(d + "func f(){var s [*]C;}");
        checkSucc(d + "func f(){var s [*]*C;}");
        checkFail(d + "func f(){var s [*]&C;}");
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
        checkFail("var a = 1; var b = a;");
        checkFail("var a = b; var b = 1;");
    }

    @Test
    public void testGlobalVar4() {
        checkFail("var a = b; var b = a;");
        checkFail("var a = c; var b = a; var c = b;");
        var d = "class C{var n*C;} ";
        checkFail(d + "var a=new(C,{n=c});var b=a;var c=b;");
    }

    //

    @Test
    public void testMemMap1() {
        checkSucc("func f() { var i *ram`int` = nil; }");
        checkSucc("func f(v *ram) { var i *ram`int`; i = v; }");
        checkSucc("func f(v *ram) { var i *ram`[2]int`; i = v; }");
        checkSucc("struct ID{} func f(v *ram) { var i *ram`ID`; i = v; }");
        checkSucc("func f() { var i = new(ram`[2]int`); }");
        checkSucc("func f(s int) { var i = new(ram, s); }");
        checkFail("func f() { var i = new(ram`[2]int`, 2); }");
        checkFail("func f() { var s *ram`int`int``; }");
    }

    @Test
    public void testMemMap2() {
        checkFail("class ID{} func f(v *ram) { var i *ram`ID`; i = v; }");
        checkFail("enum ID{S,} func f(v *ram) { var i *ram`ID`; i = v; }");
        checkFail("interface ID{} func f(v *ram) { var i *ram`ID`; i = v; }");
        checkFail("func ID(); func f(v *ram) { var i *ram`ID`; i = v; }");
    }

    @Test
    public void testMemMap3() {
        var def = "struct A{ id int; } ";
        checkFail(def + "func f(a *A) { a.id = 1; }");
        checkSucc(def + "func f(a *ram`A`) { a.id = 1; }");
        checkFail(def + "func f(a *rom`A`) { a.id = 1; }");
    }

    @Test
    public void testMemMap4() {
        checkSucc("func f(){ var r *ram`[]int`; }");
        checkFail("func f(){ var r *ram`[][]int`; }");
        checkSucc("func f(){ var r *ram`[][2]int`; }");
        checkFail("func f(v int){ var r *ram`[][v]int`; }");
        checkFail("func f(){ var r *ram`[2][]int`; }");

        checkSucc("func f(){ var r = new(ram`[2]int`); }");
        checkFail("func f(v int){ var r = new(ram`[v]int`); }");
        checkFail("func f(){ var r = new(ram`[][2]int`); }");
        checkFail("func f(){ var r = new(ram`[2][]int`); }");
        checkFail("func f(){ var r = new(ram`[][]int`); }");
    }

    @Test
    public void testMemMap5() {
        checkSucc("func f(){ const s = 10; var r = new(ram`[s]int`); }");
        checkFail("func f(s int){ var r = new(ram`[s]int`); }");
    }

    @Test
    public void testMemTransfer1() {
        checkSucc("func f(v *ram`uint`) { var i *ram`float` = v; }");
        checkSucc("func f(v *ram`uint`) { var i *rom`float` = v; }");
        checkSucc("func f(v *rom`uint`) { var i *rom`float` = v; }");
        checkFail("func f(v *rom`uint`) { var i *ram`float` = v; }");
    }

    @Test
    public void testMemTransfer2() {
        var d = "class C{} interface I{} enum E{S,} attribute A{} ";
        checkFail(d + "func f(v *C) { var i *ram = v; }");
        checkFail(d + "func f(v *I) { var i *ram = v; }");
        checkFail(d + "func f(v E) { var i *ram = v; }");
        checkFail(d + "func f(v A) { var i *ram = v; }");
        checkFail(d + "func f(v int) { var i *ram = v; }");
        checkFail(d + "func f(v float) { var i *ram = v; }");
        checkFail(d + "func f(v bool) { var i *ram = v; }");
    }

    @Test
    public void testMemTransfer3() {
        checkSucc("func f() { var i *rom = \"字面量\"; }");
        checkFail("func f() { var i *ram = \"字面量\"; }");
        checkFail("func f() { var i &rom = \"字面量\"; }");
        checkFail("func f() { var i *ram = 1; }");
        checkFail("func f() { var i *ram = 3.3; }");
        checkFail("func f() { var i *ram = true; }");
    }

    // struct

    @Test
    public void testStruct0() {
        var def = "struct A {  } ";
        checkSucc(def + "func f() { var a A = {}; }");
        checkFail(def + "func f() { var a *A; }");
        checkFail(def + "func f() { var a = new(A); }");
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
    public void testStructField1() {
        var def = "struct A {} union B {}";
        checkSucc(def + "struct R{ a int; b [16]int64; c [0]float; d A; e B; f[2]A; }");
        checkSucc(def + "union R{ a int; b [16]int64; c [0]float; d A; e B; f[2]A; }");
    }

    @Test
    public void testStructField2() {
        checkFail("struct R{ a bool; }");
        checkFail("struct R{ a ram; }");
        checkFail("struct R{ a rom; }");
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
        checkSucc(def + "func f(r *ram`R`) { r.id = 1; }");
        checkFail(def + "func f(r *rom`R`) { r.id = 1; }");
        checkSucc(def + "func f(r *ram`R`) { var v = r.id; }");
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
    }

    @Test
    public void testStructBitfield1() {
        checkSucc("struct A{id:7 int8;}");
        checkFail("struct A{id:0 int8;}");
        checkFail("struct A{id:9 int8;}");
        checkFail("struct A{id:-0 int8;}");
        checkSucc("struct A{id:6,type:2 int;}");
        checkFail("struct A{id:true int8;}");
        checkFail("struct A{id:3.3 int8;}");
        checkFail("struct A{id:\"tr\" int8;}");
    }

    @Test
    public void testStructBitfield2() {
        checkSucc("struct A{id:X int;} const X=8;");
        checkFail("struct A{id:X int;} const X=80;");
        checkSucc("struct A{id:Y int;} const X=8; const Y=X;");
        checkFail("struct A{id:X int;} var X=8;");
        checkFail("struct A{id:X int;} const X=1.3;");
        checkFail("struct A{id:X int;} const X=true;");
        checkFail("struct A{id:X int;} const X=\"str\";");
        checkFail("struct A{id:X int;} var X=false;");
        checkFail("struct A{id:X int;} var X=false;");
    }

    @Test
    public void testStructBitfield3() {
        checkFail("struct D{} struct A{id:X int;} const X D={};");
        checkFail("union D{} struct A{id:X int;} const X D={};");
        checkFail("class D{} struct A{id:X int;} const X D={};");
        checkFail("enum D{S,} struct A{id:X int;} const X D={};");
        checkFail("func D(); struct A{id:X int;} const X D={};");
    }

    @Test
    public void testStructBitfield4() {
        checkFail("struct D{w int;} struct A{id:X.w int;} const X D={w=10};");
    }

    //


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
    }

    @Test
    public void testEnum3() {
        checkFail("enum S{A,B,} func f() { var s int; s=S.A; }");
        checkFail("enum S{A,B,} func f() { var s S; s=S.C; }");
    }

    // statement

    @Test
    public void testStatementDeclaration() {
        checkSucc("func f() { var a,b,c int; }");
        checkSucc("func f() { var a,b,c int = 1,2,3; }");
        checkFail("func f() { var a,b,c int = 1,2; }");
        checkFail("func f() { var a,b,c int = 1,2,3,4; }");
        checkFail("func f() { var a,b,c bool = 1,2,3; }");
        checkSucc("func f() { var a,b,c = 1,2,3; }");
    }

    @Test
    public void testStatementAssignment() {
        var def = "class A{ var id int; }";
        checkSucc(def + "func f(a *A) { var v int; v,a.id = 1,2; }");
        checkFail(def + "func f(a *A) { var v int; v,a.id = 1; }");
        checkFail(def + "func f(a *A) { var v int; v,a.id = 1,2,3; }");
        checkSucc(def + "func f(a *A, r [*]int) { var v int; v,a.id,r[2] = 1,2,3; }");
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
        checkFail("func f(v int){if(var v int=0;v>0){var v int;}}");
        checkSucc("func f(v int){if(var v int=0;v>0){{var v int;}}}");
        checkSucc("func f(v int,a func()){if(v<0)a();else a();}");
        checkSucc("func f(v int,a func()){if(v<0){a();}else{a();}}");
        checkFail("func f(v int){if(v){var v int;}}");
        checkFail("func f(v float){if(v){var v int;}}");
        checkFail("func f(v *rom){if(v){var v int;}}");
    }

    @Test
    public void testStatementSwitch() {
        var d = "func a(){} enum E{U,V,W,} const S1K = 1<<10; var S1M = 1<<20;";
        checkSucc(d + "func f(v int){switch(v){ case 1{} default{} }}");
        checkSucc(d + "func f(v int){switch(v){ case S1K{} default{} }}");
        checkFail(d + "func f(v int){switch(v){ case S1M{} default{} }}");
        checkSucc(d + "func f(v int){ const K1 = S1K; switch(v){ case K1{} default{} }}");
        checkFail(d + "func f(v int, c int){switch(v){ case c{} }}");
        checkSucc(d + "func f(v int){switch(v){ case 1{} case 2{} default{} }}");
        checkFail(d + "func f(v int){switch(v){ case 1{} }}");
        checkSucc(d + "func f(v E){switch(v){ case U{} case V{} case W{} }}");
        checkSucc(d + "func f(v E){switch(v){ case U{} default{} }}");
        checkFail(d + "func f(v E){switch(v){ case U{} }}");
        checkSucc(d + "func f(v E){switch(v){ case U{var v E;} default{var v E;}  }}");
        checkFail(d + "func f(v E){switch(v){ case A1{} }} enum A{A1,A2,}");
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
        checkSucc(d + "func f(v E){ try{}finally{} }");
        checkSucc(d + "func f(v E){ try{var v E;}finally{} }");
        checkSucc(d + "func f(v E){ try{}finally{var v E;} }");
        checkSucc(d + "func f(v E){ try{}catch(v E){} }");
        checkSucc(d + "func f(v E){ try{var v E;}catch(v E){} }");
        checkFail(d + "func f(v E){ try{}catch(v E){var v E;} }");
    }

    @Test
    public void testStatementLoop() {
        checkSucc("func f(){for(true){}}");
        checkSucc("func f(){var i int=10; for(i<0){i-=0;}}");
        checkSucc("func f(){for(var i=10;i<100;i+=1){}}");
        checkFail("func f(){for(var i=10;i<100;i+=1){var i = 0;}}");
        checkSucc("func f(){for(true){break;}}");
        checkFail("func f(){for(true){}break;}");
        checkSucc("func f(){for(true){continue;}}");
        checkFail("func f(){for(true){}continue;}");
        checkSucc("func f(){jjj:for(true){break jjj;}}");
        checkFail("func f(){jjj:for(true){break jj;}}");
        checkSucc("func f(){jjj:for(true){continue jjj;}}");
        checkFail("func f(){jjj:for(true){continue jj;}}");
    }

    @Test
    public void testStatementGoto() {
        checkSucc("func f(){var i=0; jjj:i+=1; if(i<10) goto jjj;}");
    }

    //


    //

    static InputStream getSample(String name) {
        return SampleParseTest.class.getResourceAsStream("/analysis/" + name + ".feng");
    }

    static Source parseSample(String name) {
        try (var is = getSample(name)) {
            Assertions.assertNotNull(is);
            return BaseParseTest.doParseFile(is);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    public void testSample() {
        var src = parseSample("list");
        var ctx = new GlobalSymbolContext(src.table());
        new SemanticAnalysis(ctx).visit(src);
    }

}
