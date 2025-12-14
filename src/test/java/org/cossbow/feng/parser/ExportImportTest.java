package org.cossbow.feng.parser;


import org.cossbow.feng.ast.BinaryOperator;
import org.cossbow.feng.ast.Optional;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.Symbol;
import org.cossbow.feng.ast.dcl.DefinedTypeDeclarer;
import org.cossbow.feng.ast.dcl.NewArrayType;
import org.cossbow.feng.ast.dcl.NewDefinedType;
import org.cossbow.feng.ast.expr.BinaryExpression;
import org.cossbow.feng.ast.expr.NewExpression;
import org.cossbow.feng.ast.mod.Module_;
import org.cossbow.feng.ast.oop.ClassDefinition;
import org.cossbow.feng.ast.stmt.ArrayTuple;
import org.cossbow.feng.ast.stmt.AssignmentsStatement;
import org.cossbow.feng.ast.stmt.CallStatement;
import org.cossbow.feng.ast.stmt.DeclarationStatement;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

public class ExportImportTest extends BaseParseTest {

    static Module_ mod(String... values) {
        return new Module_(Position.ZERO, identifiers(values));
    }

    // import

    @Test
    public void testImport1() {
        var code = "import a.b;";
        var file = doParseFile(code);
        var imports = file.imports();
        Assertions.assertEquals(1, imports.size());

        var i = imports.getFirst();
        Assertions.assertEquals(mod("a", "b"), i.module());

        Assertions.assertFalse(i.alias().has());
        Assertions.assertFalse(i.flat());
    }

    @Test
    public void testImport2() {
        var code = "import b.c.d dist;";
        var file = doParseFile(code);
        var imports = file.imports();
        Assertions.assertEquals(1, imports.size());

        var i = imports.getFirst();
        Assertions.assertEquals(mod("b", "c", "d"), i.module());
        Assertions.assertEquals(identifier("dist"), i.alias().must());
        Assertions.assertFalse(i.flat());
    }

    @Test
    public void testImport3() {
        var code = "import b.c.d *;";
        var file = doParseFile(code);
        var imports = file.imports();
        Assertions.assertEquals(1, imports.size());

        var i = imports.getFirst();
        Assertions.assertEquals(mod("b", "c", "d"), i.module());
        Assertions.assertTrue(i.flat());
    }

    // module prefix

    private Symbol randVar(int ml, int nl) {
        return new Symbol(Position.ZERO,
                Optional.of(randVarName(ml)),
                randVarName(nl));
    }

    private void testTypeDeclarer(String flag) {
        var name = randVarName(12);
        var type = randVar(12, 32);
        var code = "var %s %s%s = {};".formatted(name, flag, type);
        var ds = (DeclarationStatement) doParseLocal(code);
        var v = ds.variables().getFirst();
        Assertions.assertEquals(name, v.name());
        var dt = ((DefinedTypeDeclarer) v.type().must())
                .definedType().symbol();
        Assertions.assertEquals(type, dt);
    }

    @Test
    public void testTypeDeclarer1() {
        testTypeDeclarer("");
    }

    @Test
    public void testTypeDeclarer2() {
        testTypeDeclarer("*");
    }

    @Test
    public void testTypeDeclarer3() {
        testTypeDeclarer("&");
    }

    @Test
    public void testNewType() {
        var type = randVar(12, 32);
        var param = randVar(8, 16);
        var code = "var a = new(%s`%s`);".formatted(type, param);
        var ds = (DeclarationStatement) doParseLocal(code);
        var e = ((ArrayTuple) ds.init().must()).values().getFirst();
        var n = (NewExpression) e;
        var t = ((NewDefinedType) n.type()).type();
        Assertions.assertEquals(type, t.symbol());
        Assertions.assertEquals(param,
                typeName(t.generic().arguments().getFirst()));
    }

    @Test
    public void testNewArray() {
        var index = randVar(8, 24);
        var type = randVar(12, 32);
        var code = "var a = new([%s]%s);".formatted(index, type);
        var ds = (DeclarationStatement) doParseLocal(code);
        var e = ((ArrayTuple) ds.init().must()).values().getFirst();
        var n = (NewExpression) e;
        var t = (NewArrayType) n.type();
        Assertions.assertEquals(index, varName(t.length()));
        Assertions.assertEquals(type, typeName(t.element()));
    }

    @Test
    public void testBinExpr() {
        for (var op : BinaryOperator.values()) {
            var o = operator(op);
            var a = randVar(4, 12);
            var b = randVar(3, 15);
            var code = "r=%s%s%s;".formatted(a, o, b);
            var as = (AssignmentsStatement) doParseLocal(code);
            var be = (BinaryExpression) ((ArrayTuple) as.tuple())
                    .values().getFirst();
            Assertions.assertEquals(op, be.operator());
            Assertions.assertEquals(a, varName(be.left()));
            Assertions.assertEquals(b, varName(be.right()));
        }
    }

    @Test
    public void testCallee() {
        var name = randVar(16, 32);
        var code = name + "();";
        var cs = (CallStatement) doParseLocal(code);
        Assertions.assertEquals(name, varName(cs.call().callee()));
    }

    @Test
    public void testClass() {
        var parent = randVar(10, 32);
        var ifs = new Symbol[10];
        var impls = new String[ifs.length];
        for (int i = 0; i < ifs.length; i++) {
            ifs[i] = randVar(i + 1, 32);
            impls[i] = ifs[i].toString();
        }
        var code = "class Dev:%s(%s){}".formatted(parent,
                String.join(",", impls));
        var def = (ClassDefinition) doParseDefinition(code);
        Assertions.assertEquals(parent, def.parent().must().symbol());
        for (int i = 0; i < ifs.length; i++) {
            Assertions.assertEquals(ifs[i], def.impl().getValue(i).symbol());
        }
    }

    // export

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
            var field = def.fields().get(identifier("price"));
            Assertions.assertEquals(i > 0, field.export());
        }
    }

    @Test
    public void testExportClassMethod() {
        for (int i = 0; i <= 1; i++) {
            var code = "class Cat{%s func run(){} }".formatted(i > 0 ? "export" : "");
            var def = (ClassDefinition) doParseDefinition(code);
            var method = def.methods().get(identifier("run"));
            Assertions.assertEquals(i > 0, method.export());
        }
    }

}
