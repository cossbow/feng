package org.cossbow.feng.coder;

import org.cossbow.feng.ast.Entity;
import org.cossbow.feng.parser.BaseParseTest;
import org.cossbow.feng.visit.SymbolContext;
import org.junit.jupiter.api.Test;

import static org.cossbow.feng.analysis.EmptySymbolContext.EMPTY;

public class CppGeneratorTest {

    private void gen(SymbolContext ctx, Entity root) {
        var sb = new StringBuilder("/* -------------------- */\n");
        new CppGenerator(ctx, sb).visit(root);
        System.out.println(sb);
    }

    private void trans(String code) {
        var src = BaseParseTest.doParseFile(code);
        gen(EMPTY, src);
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
    public void testClass1() {
        var code = "class A { var id int; }";
        trans(code);
    }

    @Test
    public void testClass2() {
        var code = "class A { var id int; func getId() int { return id; }}";
        trans(code);
    }

    @Test
    public void testClass3() {
        var code = "class A {}\nclass B : A {}";
        trans(code);
    }

    @Test
    public void testFunc1() {
        var code = "func add(a, b int) int { return a + b; }";
        trans(code);
    }

}
