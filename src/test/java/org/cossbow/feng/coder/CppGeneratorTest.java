package org.cossbow.feng.coder;

import org.cossbow.feng.analysis.SemanticAnalysis;
import org.cossbow.feng.ast.Source;
import org.cossbow.feng.parser.BaseParseTest;
import org.cossbow.feng.visit.GlobalSymbolContext;
import org.junit.jupiter.api.Test;

public class CppGeneratorTest {

    private void gen(Source src) {

    }

    private void trans(String code) {
        var src = BaseParseTest.doParseFile(code);
        var sb = new StringBuilder("/* -------------------- */\n");
        var ctx = new GlobalSymbolContext(src.table());
        new SemanticAnalysis(ctx).visit(src);
        new CppGenerator(src.table(), ctx, sb).visit(src);
        System.out.println(sb);
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
    public void testFunc1() {
        var code = "func add(a, b int) int { return a + b; }";
        trans(code);
    }

}
