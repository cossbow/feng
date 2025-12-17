package org.cossbow.feng.parser;

import org.cossbow.feng.Utils;
import org.cossbow.feng.ast.dcl.Declare;
import org.cossbow.feng.ast.dcl.DefinedTypeDeclarer;
import org.cossbow.feng.ast.dcl.Variable;
import org.cossbow.feng.ast.mod.GlobalDeclaration;
import org.cossbow.feng.ast.stmt.DeclarationStatement;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

public class DeclarationParseTest extends BaseParseTest {

    private static final Map<Declare, String> DECLARES = Map.of(
            Declare.LET, "let",
            Declare.VAR, "var",
            Declare.CONST, "const");

    @Test
    public void testGlobalVar() {
        var def = (GlobalDeclaration) doParseGlobal("var a,b = 1,2;");
        Assertions.assertFalse(def.export());
    }

    @Test
    public void testGlobal2() {
        var def = (GlobalDeclaration) doParseGlobal("export var a UserServer = 1;");
        Assertions.assertTrue(def.export());
    }

    @Test
    public void testGlobal3() {
        var def = (GlobalDeclaration) doParseGlobal("@Foo@Boo var a UserServer = 1;");
        var variables = def.statement().variables();
        for (var v : variables) {
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
            Assertions.assertTrue(dcl.variables().getFirst().type().isNil());
            Assertions.assertTrue(dcl.init().has());
        }
        {
            var dcl = parseLocalDecl("var i int");
            var type = (DefinedTypeDeclarer) dcl.variables().getFirst().type().must();
            Assertions.assertEquals("int", type.definedType().symbol().toString());
            Assertions.assertTrue(dcl.init().none());
        }
        {
            var dcl = parseLocalDecl("var u *User");
            var v = dcl.variables().getFirst();
            var td = (DefinedTypeDeclarer) v.type().must();
            Assertions.assertTrue(td.pointer());
            Assertions.assertFalse(td.phantom());
        }
        {
            var dcl = parseLocalDecl("var u &User");
            var v = dcl.variables().getFirst();
            var td = (DefinedTypeDeclarer) v.type().must();
            Assertions.assertTrue(td.pointer());
            Assertions.assertTrue(td.phantom());
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

}
