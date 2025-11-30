package org.cossbow.feng.parser;

import org.cossbow.feng.ast.UniqueTable;
import org.cossbow.feng.ast.dcl.Declare;
import org.cossbow.feng.ast.dcl.DefinedTypeDeclarer;
import org.cossbow.feng.ast.oop.ClassDefinition;
import org.cossbow.feng.ast.oop.ClassField;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

public class ClassParseTest extends BaseParseTest {

    private static final Map<Declare, String> DECLARES =
            Map.of(Declare.VAR, "var", Declare.CONST, "const");

    @Test
    public void testDefine() {
        var name = randTypeName(32);
        var def = (ClassDefinition) doParseDefinition("class " + name + " {}");
        Assertions.assertEquals(name, def.name());
        Assertions.assertTrue(def.parent().none());
        Assertions.assertTrue(def.impl().isEmpty());
        Assertions.assertTrue(def.generic().isEmpty());
        Assertions.assertTrue(def.fields().isEmpty());
        Assertions.assertTrue(def.methods().isEmpty());
    }

    @Test
    public void testInherit() {
        var name = randTypeName(32);
        var parent = randTypeName(32);
        var def = (ClassDefinition) doParseDefinition("class %s : %s {}".formatted(name, parent));
        Assertions.assertEquals(name, def.name());
        var ref = def.parent().must();
        Assertions.assertEquals(parent, ref.name());
    }

    @Test
    public void testImpls() {
        for (int size = 1; size <= 8; size++) {
            var name = randTypeName(16);
            var types = anyNames(RandTypeName, 8, size);
            var code = "class %s (%s) {}".formatted(name, idList(types));
            var def = (ClassDefinition) doParseDefinition(code);
            Assertions.assertEquals(name, def.name());
            Assertions.assertEquals(types.size(), def.impl().size());
            for (var type : types) {
                var impl = def.impl().get(type);
                Assertions.assertEquals(type, impl.name());
                Assertions.assertTrue(impl.generic().isEmpty());
            }
        }
    }


    private UniqueTable<ClassField> parseFields(String fields) {
        var def = (ClassDefinition) doParseDefinition("class A {" + fields + "}");
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
                var type = randTypeName(16);
                var fields = parseFields("%s %s %s;".formatted(dcl.getValue(), idList(names), type));
                Assertions.assertEquals(size, fields.size());
                for (var name : names) {
                    var f = fields.get(name);
                    Assertions.assertSame(dcl.getKey(), f.declare());
                    Assertions.assertEquals(name, f.name());
                    var t = (DefinedTypeDeclarer) f.type();
                    Assertions.assertEquals(type, t.definedType().name());
                    Assertions.assertTrue(t.definedType().generic().isEmpty());
                    Assertions.assertFalse(t.pointer());
                    Assertions.assertFalse(t.phantom());
                }
            }
        }
    }

    @Test
    public void testFieldPointer() {
        for (var dcl : DECLARES.entrySet()) {
            for (int size = 1; size <= 8; size++) {
                var names = anyNames(RandVarFuncName, 10, size);
                var type = randTypeName(16);
                var fields = parseFields("%s %s *%s;".formatted(dcl.getValue(), idList(names), type));
                Assertions.assertEquals(size, fields.size());
                for (var name : names) {
                    var f = fields.get(name);
                    Assertions.assertSame(dcl.getKey(), f.declare());
                    Assertions.assertEquals(name, f.name());
                    var t = (DefinedTypeDeclarer) f.type();
                    Assertions.assertEquals(type, t.definedType().name());
                    Assertions.assertTrue(t.definedType().generic().isEmpty());
                    Assertions.assertTrue(t.pointer());
                    Assertions.assertFalse(t.phantom());
                }
            }
        }
    }

    @Test
    public void testMethod1() {
        var name = randVarFuncName(16);
        var code = "class A { func %s() {} }".formatted(name);
        var def = (ClassDefinition) doParseDefinition(code);
        var method = def.methods().get(name);
        Assertions.assertEquals(name, method.name());
        Assertions.assertFalse(method.export());
    }

    @Test
    public void testMethod2() {
        var name = randVarFuncName(16);
        var code = "class A { export func %s() {} }".formatted(name);
        var def = (ClassDefinition) doParseDefinition(code);
        var method = def.methods().get(name);
        Assertions.assertEquals(name, method.name());
        Assertions.assertTrue(method.export());
    }

}
