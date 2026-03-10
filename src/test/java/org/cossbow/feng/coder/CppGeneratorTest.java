package org.cossbow.feng.coder;

import org.cossbow.feng.analysis.SemanticAnalysis;
import org.cossbow.feng.ast.Source;
import org.cossbow.feng.parser.BaseParseTest;
import org.cossbow.feng.visit.GlobalSymbolContext;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

public class CppGeneratorTest {

    private void gen(Source src) {

    }

    private void trans(String code) {
        var src = BaseParseTest.doParseFile(code);
        var sb = new StringBuilder("/* -------------------- */\n");
        var ctx = new GlobalSymbolContext(src.table());
        new SemanticAnalysis(ctx).analyse(src);
        new CppGenerator(ctx, sb, true).write(src);
        System.out.println(sb);
        try {
            Files.write(Path.of("D:\\Users\\j30036461\\CLionProjects\\cpptest2\\unittest.cpp"),
                    List.of(sb), StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    //

    @Test
    public void testGlobal1() {
        var code = "const PI float64 = 3.1415926535897932384626433832795;";
        trans(code);
    }

    @Test
    public void testGlobal2() {
        var code = "var open bool = false;";
        trans(code);
    }

    @Test
    public void testGlobal3() {
        var code = "var a *A = nil; class A{} ";
        trans(code);
    }

    @Test
    public void testClassMember1() {
        var code = "class A { var id int; }";
        trans(code);
    }

    @Test
    public void testClassMember2() {
        var code = "class A { var id int; func getId() int { return id + this.id; }}";
        trans(code);
    }

    @Test
    public void testClassMember3() {
        var code = "class A { func a() { } func b() { a(); this.a(); }}";
        trans(code);
    }

    @Test
    public void testClassExtend1() {
        var code = "class A {}\nclass B : A {}";
        trans(code);
    }

    @Test
    public void testClassExtend2() {
        var code = "class A { func a() {} }\nclass B : A {} func f(b*B){b.a();}";
        trans(code);
    }

    @Test
    public void testFunc1() {
        var code = "func add(a, b int) int { return a + b; }";
        trans(code);
    }

    @Test
    public void testFunc2() {
        var code = "var ct int; func add(a, b int) int { return ct + b; }";
        trans(code);
    }

    @Test
    public void testVar1() {
        var code = "func f(x,y A){ var a A = {id=1}; } class A{var id int;}";
        trans(code);
    }

    @Test
    public void testStmt1() {
        trans("class A{var id int; var n *A; func f() { n.n = nil; } }");
    }

    @Test
    public void testStmtFor1() {
        trans("func r() {} func f(n int) { for (var i=0; i<n; i+=1) { r(); } }");
    }

    @Test
    public void testExprAssert1() {
        trans("class A{} class B:A{} func f(a*A) { var b = a?(*B); }");
    }

}
