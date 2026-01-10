package org.cossbow.feng.coder;

import org.cossbow.feng.ast.Entity;
import org.cossbow.feng.parser.BaseParseTest;
import org.cossbow.feng.visit.SymbolContext;
import org.junit.jupiter.api.Test;

import static org.cossbow.feng.analysis.EmptySymbolContext.EMPTY;

public class CppGeneratorTest {

    private void gen(SymbolContext ctx, Entity root) {
        var sb = new StringBuilder("#include <stdint.h>\n#include <stdbool.h>\n\nclass Object{};\n");
        new CppGenerator(ctx, sb).visit(root);
        System.out.println(sb);
    }

    @Test
    public void testClass1() {
        var code = "class A { var id int; }";
        var src = BaseParseTest.doParseFile(code);
        var def = src.definitions().getFirst();
        gen(EMPTY, def);
    }

    @Test
    public void testClass2() {
        var code = "class A { var id int; func getId() int { return id; }}";
        var src = BaseParseTest.doParseFile(code);
        var def = src.definitions().getFirst();
        gen(EMPTY, def);
    }

    @Test
    public void testClass3() {
        var code = "class A {}\nclass B : A {}";
        var src = BaseParseTest.doParseFile(code);
        gen(EMPTY, src);
    }

}
