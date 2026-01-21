package org.cossbow.feng.parser;

import org.cossbow.feng.ast.IdentifierTable;
import org.cossbow.feng.ast.dcl.Declare;
import org.cossbow.feng.ast.dcl.DerivedTypeDeclarer;
import org.cossbow.feng.ast.oop.ClassDefinition;
import org.cossbow.feng.ast.oop.ClassField;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.cossbow.feng.ast.dcl.ReferKind.STRONG;

public class ClassParseTest extends BaseParseTest {

    private static final Map<Declare, String> DECLARES =
            Map.of(Declare.VAR, "var", Declare.CONST, "const");

    @Test
    public void testDefine() {
        var name = randTypeSymbol(32);
        var def = (ClassDefinition) doParseType("class " + name + " {}", name);
        Assertions.assertEquals(name, def.symbol());
        Assertions.assertTrue(def.inherit().none());
        Assertions.assertTrue(def.impl().isEmpty());
        Assertions.assertTrue(def.generic().isEmpty());
        Assertions.assertTrue(def.fields().isEmpty());
        Assertions.assertTrue(def.methods().isEmpty());
    }

    @Test
    public void testInherit() {
        var name = randTypeSymbol(32);
        var parent = randTypeSymbol(32);
        var def = (ClassDefinition) doParseType(
                "class %s : %s {}".formatted(name, parent), name);
        Assertions.assertEquals(name, def.symbol());
        var ref = def.inherit().must();
        Assertions.assertEquals(parent, ref.symbol());
    }

    @Test
    public void testImpls() {
        for (int size = 1; size <= 8; size++) {
            var name = randTypeSymbol(16);
            var types = anyNames(RandTypeSymbol, 8, size);
            var code = "class %s (%s) {}".formatted(name, idList(types));
            var def = (ClassDefinition) doParseType(code, name);
            Assertions.assertEquals(name, def.symbol());
            Assertions.assertEquals(types.size(), def.impl().size());
            for (int i = 0; i < types.size(); i++) {
                var type = types.get(i);
                var impl = def.impl().getValue(i);
                Assertions.assertEquals(type, impl.symbol());
                Assertions.assertTrue(impl.generic().isEmpty());
            }
        }
    }


    private IdentifierTable<ClassField> parseFields(String fields) {
        var def = (ClassDefinition) doParseType("class A {" + fields + "}", "A");
        return def.fields();
    }

    @Test
    public void testField1() {
        var field = parseFields("export var id int;").get(identifier("id"));
        Assertions.assertTrue(field.export());
    }

    @Test
    public void testFieldValue() {
        for (var dcl : DECLARES.entrySet()) {
            for (int size = 1; size <= 8; size++) {
                var names = anyNames(RandVarFuncName, 10, size);
                var type = randTypeSymbol(16);
                var fields = parseFields("%s %s %s;".formatted(dcl.getValue(), idList(names), type));
                Assertions.assertEquals(size, fields.size());
                for (var name : names) {
                    var f = fields.get(name);
                    Assertions.assertSame(dcl.getKey(), f.declare());
                    Assertions.assertEquals(name, f.name());
                    var t = (DerivedTypeDeclarer) f.type();
                    Assertions.assertEquals(type, t.derivedType().symbol());
                    Assertions.assertTrue(t.derivedType().generic().isEmpty());
                    Assertions.assertTrue(t.refer().none());
                }
            }
        }
    }

    @Test
    public void testFieldReference() {
        for (var dcl : DECLARES.entrySet()) {
            for (int size = 1; size <= 8; size++) {
                var names = anyNames(RandVarFuncName, 10, size);
                var type = randTypeSymbol(16);
                var fields = parseFields("%s %s *%s;".formatted(
                        dcl.getValue(), idList(names), type));
                Assertions.assertEquals(size, fields.size());
                for (var name : names) {
                    var f = fields.get(name);
                    Assertions.assertSame(dcl.getKey(), f.declare());
                    Assertions.assertEquals(name, f.name());
                    var t = (DerivedTypeDeclarer) f.type();
                    Assertions.assertEquals(type, t.derivedType().symbol());
                    Assertions.assertTrue(t.derivedType().generic().isEmpty());
                    var ref = t.refer().get();
                    Assertions.assertSame(STRONG, ref.kind());
                    Assertions.assertFalse(ref.required());
                }
            }
        }
    }

    @Test
    public void testMethod1() {
        var name = randVarName(16);
        var code = "class A { func %s() {} }".formatted(name);
        var def = (ClassDefinition) doParseType(code, "A");
        var m = def.methods().get(name);
        Assertions.assertEquals(name, m.name());
        Assertions.assertFalse(m.export());
    }

    @Test
    public void testMethod2() {
        var name = randVarName(16);
        var code = "class A { export func %s() {} }".formatted(name);
        var def = (ClassDefinition) doParseType(code, "A");
        var method = def.methods().get(name);
        Assertions.assertEquals(name, method.name());
        Assertions.assertTrue(method.export());
    }

}
