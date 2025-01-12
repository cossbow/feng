package org.cossbow.feng.parser;


import org.cossbow.feng.ast.oop.ClassDefinition;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

public class PackageParseTest extends BaseParseTest {

    @Test
    public void testImport1() {
        var code = "import a\\b {Alpha, Beta, Gamma: C, Delta :D};";
        var p = doParse(code);
        var imports = p.root().imports();
        Assertions.assertEquals(1, imports.size());

        var i = imports.getFirst();
        Assertions.assertEquals(identifiers("a", "b"), i.package_());

        Assertions.assertFalse(i.importAll());

        Assertions.assertEquals(4, i.symbols().size());

        {
            var s = i.symbols().get(0);
            Assertions.assertEquals(identifier("Alpha"), s.name());
            Assertions.assertFalse(s.alias().isPresent());
        }
        {
            var s = i.symbols().get(1);
            Assertions.assertEquals(identifier("Beta"), s.name());
            Assertions.assertFalse(s.alias().isPresent());
        }
        {
            var s = i.symbols().get(2);
            Assertions.assertEquals(identifier("Gamma"), s.name());
            Assertions.assertEquals(identifier("C"), s.alias().get());
        }
        {
            var s = i.symbols().get(3);
            Assertions.assertEquals(identifier("Delta"), s.name());
            Assertions.assertEquals(identifier("D"), s.alias().get());
        }
    }

    @Test
    public void testImport2() {
        var code = "import b\\c\\d {*};";
        var p = doParse(code);
        var imports = p.root().imports();
        Assertions.assertEquals(1, imports.size());

        var i = imports.getFirst();
        Assertions.assertEquals(identifiers("b", "c", "d"), i.package_());
        Assertions.assertTrue(i.importAll());
    }

    @Test
    public void testExportGlobal() {
        var globalCodes = List.of(
                "func sin(){}",
                "func Run();",
                "class Cat{}",
                "interface Driver{}",
                "enum Status{A,}",
                "struct Block{}",
                "union Result{}",
                "attribute Test{}",
                "var engine Engine;",
                "const PI =3.14;"
        );
        for (int i = 0; i <= 1; i++) {
            for (var gc : globalCodes) {
                var code = (i > 0 ? "export " : "") + gc;
                var global = doParseGlobal(code);
                Assertions.assertEquals(i > 0, global.export());
            }
        }
    }

    @Test
    public void testExportClassField() {
        for (int i = 0; i <= 1; i++) {
            var code = "class Cat{%s var price float;}".formatted(i > 0 ? "export" : "");
            var def = (ClassDefinition) doParseDefinition(code);
            var field = def.fields().getFirst();
            Assertions.assertEquals(i > 0, field.export());
        }
    }

    @Test
    public void testExportClassMethod() {
        for (int i = 0; i <= 1; i++) {
            var code = "class Cat{%s func run(){} }".formatted(i > 0 ? "export" : "");
            var def = (ClassDefinition) doParseDefinition(code);
            var method = def.methods().getFirst();
            Assertions.assertEquals(i > 0, method.export());
        }
    }

}
