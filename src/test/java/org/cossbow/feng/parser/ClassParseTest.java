package org.cossbow.feng.parser;

import org.cossbow.feng.ast.dcl.Declare;
import org.cossbow.feng.ast.dcl.DefinedTypeDeclarer;
import org.cossbow.feng.ast.oop.ClassDefinition;
import org.cossbow.feng.ast.oop.ClassField;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

public class ClassParseTest extends BaseParseTest {

    private static final Map<Declare, String> DECLARES =
            Map.of(Declare.VAR, "var", Declare.CONST, "const");

    @Test
    public void testDefine() {
        var name = randTypeName(32);
        var def = (ClassDefinition) doParseDefinition("class " + name + " {}");
        Assertions.assertEquals(name, def.name().orElseThrow());
        Assertions.assertTrue(def.parent().isEmpty());
        Assertions.assertTrue(def.impls().isEmpty());
        Assertions.assertTrue(def.generic().isEmpty());
        Assertions.assertTrue(def.fields().isEmpty());
        Assertions.assertTrue(def.methods().isEmpty());
    }

    @Test
    public void testInherit() {
        var name = randTypeName(32);
        var parent = randTypeName(32);
        var def = (ClassDefinition) doParseDefinition("class %s : %s {}".formatted(name, parent));
        Assertions.assertEquals(name, def.name().orElseThrow());
        var ref = def.parent().orElseThrow();
        Assertions.assertEquals(parent, ref.name());
    }

    @Test
    public void testImpls() {
        for (int size = 1; size <= 8; size++) {
            var name = randTypeName(16);
            var impls = anyNames(RandTypeName, 8, size);
            var code = "class %s (%s) {}".formatted(name, idList(impls));
            var def = (ClassDefinition) doParseDefinition(code);
            Assertions.assertEquals(name, def.name().orElseThrow());
            Assertions.assertEquals(impls.size(), def.impls().size());
            for (int i = 0; i < size; i++) {
                Assertions.assertEquals(impls.get(i), def.impls().get(i).name());
            }
        }
    }

    @Test
    public void testGeneric() {
        for (int size = 1; size <= 8; size++) {
            var name = randTypeName(16);
            var types = anyNames(RandTypeName, 8, size);
            var code = "class %s`%s` {}".formatted(name, idList(types));
            var def = (ClassDefinition) doParseDefinition(code);
            Assertions.assertEquals(name, def.name().orElseThrow());
            Assertions.assertEquals(types.size(), def.generic().params().size());
            for (int i = 0; i < size; i++) {
                var td = def.generic().params().get(i);
                Assertions.assertEquals(types.get(i), td.name());
            }
        }
    }

    private List<ClassField> parseFields(String fields) {
        var def = (ClassDefinition) doParseDefinition("class A {" + fields + "}");
        return def.fields();
    }

    @Test
    public void testField1() {
        var field = parseFields("export var id int;").getFirst();
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
                for (int i = 0; i < size; i++) {
                    var f = fields.get(i);
                    Assertions.assertSame(dcl.getKey(), f.declare());
                    Assertions.assertEquals(names.get(i), f.name());
                    var t = (DefinedTypeDeclarer) f.type();
                    Assertions.assertEquals(type, t.definedType().name());
                    Assertions.assertTrue(t.definedType().generic().isEmpty());
                    Assertions.assertFalse(t.pointer());
                    Assertions.assertFalse(t.phantom());
                    Assertions.assertFalse(t.optional());
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
                for (int i = 0; i < size; i++) {
                    var f = fields.get(i);
                    Assertions.assertSame(dcl.getKey(), f.declare());
                    Assertions.assertEquals(names.get(i), f.name());
                    var t = (DefinedTypeDeclarer) f.type();
                    Assertions.assertEquals(type, t.definedType().name());
                    Assertions.assertTrue(t.definedType().generic().isEmpty());
                    Assertions.assertTrue(t.pointer());
                    Assertions.assertFalse(t.phantom());
                    Assertions.assertFalse(t.optional());
                }
            }
        }
    }

    @Test
    public void testFieldOptional() {
        for (var dcl : DECLARES.entrySet()) {
            for (int size = 1; size <= 8; size++) {
                var names = anyNames(RandVarFuncName, 10, size);
                var type = randTypeName(16);
                var fields = parseFields("%s %s *?%s;".formatted(dcl.getValue(), idList(names), type));
                Assertions.assertEquals(size, fields.size());
                for (int i = 0; i < size; i++) {
                    var f = fields.get(i);
                    Assertions.assertSame(dcl.getKey(), f.declare());
                    Assertions.assertEquals(names.get(i), f.name());
                    var t = (DefinedTypeDeclarer) f.type();
                    Assertions.assertEquals(type, t.definedType().name());
                    Assertions.assertTrue(t.definedType().generic().isEmpty());
                    Assertions.assertTrue(t.pointer());
                    Assertions.assertFalse(t.phantom());
                    Assertions.assertTrue(t.optional());
                }
            }
        }
    }

    @Test
    public void testMethod1() {
        var name = randVarFuncName(16);
        var def = (ClassDefinition) doParseDefinition("class A { func %s() {} }".formatted(name));
        var method = def.methods().getFirst();
        Assertions.assertEquals(name, method.name().orElseThrow());
        Assertions.assertFalse(method.export());
    }

    @Test
    public void testMethod2() {
        var name = randVarFuncName(16);
        var def = (ClassDefinition) doParseDefinition("class A { export func %s() {} }".formatted(name));
        var method = def.methods().getFirst();
        Assertions.assertEquals(name, method.name().orElseThrow());
        Assertions.assertTrue(method.export());
    }

}
