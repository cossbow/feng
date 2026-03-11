package org.cossbow.feng.parser;


import org.cossbow.feng.ast.BinaryOperator;
import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.Symbol;
import org.cossbow.feng.ast.dcl.DerivedTypeDeclarer;
import org.cossbow.feng.ast.dcl.NewArrayType;
import org.cossbow.feng.ast.dcl.NewDefinedType;
import org.cossbow.feng.ast.expr.BinaryExpression;
import org.cossbow.feng.ast.expr.NewExpression;
import org.cossbow.feng.ast.gen.DerivedType;
import org.cossbow.feng.ast.oop.ClassDefinition;
import org.cossbow.feng.ast.proc.FunctionDefinition;
import org.cossbow.feng.ast.stmt.AssignmentsStatement;
import org.cossbow.feng.ast.stmt.CallStatement;
import org.cossbow.feng.ast.stmt.DeclarationStatement;
import org.cossbow.feng.ast.stmt.Statement;
import org.cossbow.feng.util.Optional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.stream.Collectors;

public class ExportImportTest extends BaseParseTest {

    static List<Identifier> mod(String... values) {
        return identifiers(values);
    }

    // import

    @Test
    public void testImport1() {
        var code = "import a.b;";
        var file = doParseFile(code);
        var imports = file.imports();
        Assertions.assertEquals(1, imports.size());

        var i = imports.getFirst();
        Assertions.assertEquals(mod("a", "b"), i.path());

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
        Assertions.assertEquals(mod("b", "c", "d"), i.path());
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
        Assertions.assertEquals(mod("b", "c", "d"), i.path());
        Assertions.assertTrue(i.flat());
    }

    // module prefix

    private Symbol randVar(int ml, int nl) {
        var mod = ml > 0 ? randVarName(ml) : null;
        return new Symbol(Position.ZERO,
                Optional.of(mod),
                randVarName(nl));
    }

    private String makeImports(Collection<Identifier> set) {
        return set.stream().map("import %s;"::formatted)
                .collect(Collectors.joining("\n"));
    }

    private String makeImports(Identifier... arr) {
        return makeImports(Set.of(arr));
    }

    private String makeImports(Symbol... ss) {
        var mod = new ArrayList<Identifier>(ss.length);
        for (Symbol s : ss) {
            if (s.module().has()) mod.add(s.module().must());
        }
        return makeImports(mod);
    }

    private void testTypeDeclarer(String flag) {
        var name = randVarName(12);
        var type = randVar(12, 32);
        var im = makeImports(type.module().must());
        var code = "var %s %s%s = {};".formatted(name, flag, type);
        var ds = (DeclarationStatement) parseLocal(im, code);
        var v = ds.variables().getFirst();
        Assertions.assertEquals(name, v.name());
        var dt = ((DerivedTypeDeclarer) v.type().must())
                .derivedType().symbol();
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

    Statement parseLocal(String im, String stmt) {
        var fun = im + "func main() { %s }".formatted(stmt);
        var func = doParseProc(fun);
        return func.procedure().body().list().getFirst();
    }

    @Test
    public void testNewType() {
        var type = randVar(12, 32);
        var param = randVar(8, 16);
        var im = makeImports(type, param);
        var code = "var a = new(%s`%s`);".formatted(type, param);
        var ds = (DeclarationStatement) parseLocal(im, code);
        var v = ds.variables().getFirst();
        var n = (NewExpression) v.value().must();
        var t = (DerivedType) ((NewDefinedType) n.type()).type();
        Assertions.assertEquals(type, t.symbol());
        Assertions.assertEquals(param,
                typeName(t.generic().get(0)));
    }

    @Test
    public void testNewArray() {
        var index = randVar(8, 24);
        var type = randVar(12, 32);
        var im = makeImports(index, type);
        var code = "var a = new([%s]%s);".formatted(index, type);
        var ds = (DeclarationStatement) parseLocal(im, code);
        var n = (NewExpression) ds.variables().getFirst().value().must();
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
            var im = makeImports(a, b);
            var code = "r=%s%s%s;".formatted(a, o, b);
            var as = (AssignmentsStatement) parseLocal(im, code);
            var be = (BinaryExpression) as.value(0);
            Assertions.assertEquals(op, be.operator());
            Assertions.assertEquals(a, varName(be.left()));
            Assertions.assertEquals(b, varName(be.right()));
        }
    }

    @Test
    public void testCallee() {
        var name = randVar(16, 32);
        var im = makeImports(List.of(name.module().must()));
        var code = name + "();";
        var cs = (CallStatement) parseLocal(im, code);
        Assertions.assertEquals(name, varName(cs.call().callee()));
    }

    @Test
    public void testClass() {
        var parent = randVar(10, 32);
        var ifs = new Symbol[10];
        var mods = new HashSet<Identifier>(16);
        mods.add(parent.module().must());
        var impls = new String[ifs.length];
        for (int i = 0; i < ifs.length; i++) {
            ifs[i] = randVar(i + 1, 32);
            mods.add(ifs[i].module().must());
            impls[i] = ifs[i].toString();
        }
        var code = makeImports(mods) + "class Dev:%s(%s){}".formatted(parent,
                String.join(",", impls));
        var def = (ClassDefinition) doParseType(code, "Dev");
        Assertions.assertEquals(parent, def.inherit().must().symbol());
        for (int i = 0; i < ifs.length; i++) {
            Assertions.assertEquals(ifs[i], def.impl().getValue(i).symbol());
        }
    }

    // export

    @Test
    public void testExportGlobalDefinition() {
        var globalCodes = List.of(
                "func sin(){}",
                "func Run();",
                "class Cat{}",
                "interface Driver{}",
                "enum Status{A,}",
                "struct Block{}",
                "union Result{}",
                "attribute Section{}"
        );
        for (int i = 0; i <= 1; i++) {
            for (var gc : globalCodes) {
                var code = (i > 0 ? "export " : "") + gc;
                var src = doParseFile(code);
                var def = firstDef(src);
                var exported = def instanceof FunctionDefinition ?
                        src.table().exportedFunctions.exists(def.symbol()) :
                        src.table().exportedTypes.exists(def.symbol());
                Assertions.assertEquals(i > 0, exported);
            }
        }
    }

    @Test
    public void testExportGlobalDeclaration() {
        var globalCodes = List.of(
                "var engine Engine;",
                "const PI =3.14;"
        );
        for (int i = 0; i <= 1; i++) {
            for (var gc : globalCodes) {
                var code = (i > 0 ? "export " : "") + gc;
                var src = doParseFile(code);
                var v = src.variables().getFirst();
                var tab = src.table().exportedVariables;
                Assertions.assertEquals(i > 0, tab.exists(symbol(v.name())));
            }
        }
    }

    @Test
    public void testExportClassField() {
        for (int i = 0; i <= 1; i++) {
            var code = "class Cat{%s var price float;}".formatted(i > 0 ? "export" : "");
            var def = (ClassDefinition) doParseType(code, "Cat");
            var field = def.fields().get(identifier("price"));
            Assertions.assertEquals(i > 0, field.export());
        }
    }

    @Test
    public void testExportClassMethod() {
        for (int i = 0; i <= 1; i++) {
            var code = "class Cat{%s func run(){} }".formatted(i > 0 ? "export" : "");
            var def = (ClassDefinition) doParseType(code, "Cat");
            var method = def.methods().get(identifier("run"));
            Assertions.assertEquals(i > 0, method.export());
        }
    }

}
