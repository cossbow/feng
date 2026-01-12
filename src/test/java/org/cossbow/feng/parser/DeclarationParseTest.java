package org.cossbow.feng.parser;

import org.cossbow.feng.Utils;
import org.cossbow.feng.ast.dcl.*;
import org.cossbow.feng.ast.stmt.DeclarationStatement;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.cossbow.feng.ast.dcl.ReferKind.*;

public class DeclarationParseTest extends BaseParseTest {

    private static final Map<Declare, String> DECLARES = Map.of(
            Declare.LET, "let",
            Declare.VAR, "var",
            Declare.CONST, "const");

    @Test
    public void testGlobalVar() {
        var src = doParseFile("var a,b = 1,2;");
        var tab = src.table().exportedVariables;
        Assertions.assertFalse(tab.exists(identifier("a")));
        Assertions.assertFalse(tab.exists(identifier("b")));
    }

    @Test
    public void testGlobal2() {
        var src = doParseFile("export var a UserServer = 1;");
        var tab = src.table().exportedVariables;
        Assertions.assertTrue(tab.exists(identifier("a")));
    }

    @Test
    public void testGlobal3() {
        var dcl = doParseDeclaration("@Foo@Boo var a UserServer = 1;");
        for (var v : dcl.variables()) {
            var attrs = v.modifier().attributes();
            Assertions.assertEquals(identifier("Foo"), attrs.getValue(0).type());
            Assertions.assertEquals(identifier("Boo"), attrs.getValue(1).type());
        }
    }

    DeclarationStatement parseLocalDecl(String code) {
        return (DeclarationStatement) doParseLocal(code + ";");
    }

    @Test
    public void testLocal1() {
        for (int size = 1; size <= 10; size++) {
            for (var d : DECLARES.entrySet()) {
                var names = anyNames(RandVarFuncName, 6, size);
                var type = randTypeSymbol(32);
                var dcl = parseLocalDecl(d.getValue() + " " + idList(names) + "*" + type);
                var i = 0;
                for (var v : dcl.variables()) {
                    Assertions.assertEquals(names.get(i), v.name());
                    Assertions.assertEquals(d.getKey(), v.declare());
                    Assertions.assertEquals(type, typeName(v.type().must()));
                    i++;
                }
            }
        }
    }

    @Test
    public void testLocal3() {
        {
            var dcl = parseLocalDecl("var i = 0");
            Assertions.assertTrue(dcl.variables().getFirst().type().none());
            Assertions.assertTrue(dcl.init().has());
        }
        for (var prim : Primitive.values()) {
            var dcl = parseLocalDecl("var i " + prim.code);
            var type = (PrimitiveTypeDeclarer) dcl.variables().getFirst().type().must();
            Assertions.assertEquals(prim, type.primitive());
            Assertions.assertTrue(dcl.init().none());
        }
        {
            var dcl = parseLocalDecl("var u *User");
            var v = dcl.variables().getFirst();
            var td = (DefinedTypeDeclarer) v.type().must();
            var ref = td.refer().get();
            Assertions.assertSame(STRONG, ref.kind());
            Assertions.assertFalse(ref.required());
        }
        {
            var dcl = parseLocalDecl("var u *!User");
            var v = dcl.variables().getFirst();
            var td = (DefinedTypeDeclarer) v.type().must();
            var ref = td.refer().get();
            Assertions.assertSame(STRONG, ref.kind());
            Assertions.assertTrue(ref.required());
        }
        {
            var dcl = parseLocalDecl("var u &User");
            var v = dcl.variables().getFirst();
            var td = (DefinedTypeDeclarer) v.type().must();
            var ref = td.refer().get();
            Assertions.assertSame(PHANTOM, ref.kind());
            Assertions.assertFalse(ref.required());
        }
        {
            var dcl = parseLocalDecl("var u &!User");
            var v = dcl.variables().getFirst();
            var td = (DefinedTypeDeclarer) v.type().must();
            var ref = td.refer().get();
            Assertions.assertSame(PHANTOM, ref.kind());
            Assertions.assertTrue(ref.required());
        }
        {
            var dcl = parseLocalDecl("var u ~User");
            var v = dcl.variables().getFirst();
            var td = (DefinedTypeDeclarer) v.type().must();
            var ref = td.refer().get();
            Assertions.assertSame(WEAK, ref.kind());
            Assertions.assertFalse(ref.required());
        }
    }


    @Test
    public void testLocal4() {
        var fmtList = new String[]{
                "var i %s",
                "var i *%s",
                "var i &%s",
        };
        for (var fmt : fmtList) {
            for (int i = 0; i < 10; i++) {
                var name = randTypeSymbol(16);
                var dcl = parseLocalDecl(fmt.formatted(name));
                var td = (DefinedTypeDeclarer) dcl.variables().getFirst().type().must();
                Assertions.assertEquals(name, td.definedType().symbol());
            }
        }
    }

    @Test
    public void testLocal5() {
        for (int n = 1; n <= 10; n++) {
            var names = anyNames(RandVarFuncName, 8, n);
            var values = anyNames(RandVarSymbol, 8, n);
            var code = "var " + idList(names) + " = " + idList(values);
            var dcl = parseLocalDecl(code);
            Assertions.assertEquals(names, Utils.listOf(dcl.variables(), Variable::name));
            Assertions.assertEquals(values, Utils.listOf(exprs(dcl.init().must()),
                    BaseParseTest::varName));
        }
    }

    @Test
    public void testLocal6() {
        {
            var len = randInt(1, 16);
            var dcl = parseLocalDecl("var a [%s]Host".formatted(len));
            var v = dcl.variables().getFirst();
            var td = (ArrayTypeDeclarer) v.type().must();
            Assertions.assertEquals(symbol("Host"), typeName(td.element()));
            Assertions.assertEquals(len, integer(td.length().must()).value());
        }
        {
            var dcl = parseLocalDecl("var a [*]Host");
            var v = dcl.variables().getFirst();
            var td = (ArrayTypeDeclarer) v.type().must();
            Assertions.assertEquals(symbol("Host"), typeName(td.element()));
            Assertions.assertTrue(td.length().none());
            Assertions.assertTrue(td.refer().must().checkType(STRONG));
            Assertions.assertFalse(td.refer().must().immutable());
            Assertions.assertFalse(td.refer().must().required());
        }
        {
            var len = randInt(1, 16);
            var dcl = parseLocalDecl("var a [%s][&!]Host".formatted(len));
            var v = dcl.variables().getFirst();
            var td = (ArrayTypeDeclarer) v.type().must();
            Assertions.assertEquals(len, integer(td.length().must()).value());

            var td2 = (ArrayTypeDeclarer) td.element();
            Assertions.assertEquals(symbol("Host"), typeName(td2.element()));
            Assertions.assertTrue(td2.length().none());
            Assertions.assertTrue(td2.refer().must().checkType(PHANTOM));
            Assertions.assertFalse(td2.refer().must().immutable());
            Assertions.assertTrue(td2.refer().must().required());
        }
    }

}
