package org.cossbow.feng.parser;

import org.cossbow.feng.ast.Symbol;
import org.cossbow.feng.ast.UniqueTable;
import org.cossbow.feng.ast.struct.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.concurrent.ThreadLocalRandom;

public class StructureParseTest extends BaseParseTest {

    @Test
    public void testTypeDefine() {
        enum Domain {struct, union}
        for (var domain : Domain.values()) {
            var name = randTypeName(32);
            var code = "%s %s{}".formatted(domain, name);
            var def = (StructureDefinition) doParseDefinition(code);
            Assertions.assertEquals(name, def.name());
            Assertions.assertEquals(domain == Domain.union, def.union());
            Assertions.assertTrue(def.fields().isEmpty());
            Assertions.assertTrue(def.generic().isEmpty());
        }
    }

    UniqueTable<StructureField> parseFields(String names) {
        var code = "struct Foo { %s }".formatted(names);
        var def = (StructureDefinition) doParseDefinition(code);
        return def.fields();
    }

    public static void checkTypeName(StructureType type, Symbol name) {
        var dst = (DefinedStructureType) type;
        Assertions.assertEquals(name, dst.type().symbol());
    }

    @Test
    public void testFieldName() {
        var names = anyNames(RandVarFuncName, 8, 10);
        var fields = parseFields("%s int;".formatted(idList(names)));
        Assertions.assertEquals(names.size(), fields.size());
        for (var name : names) {
            var field = fields.get(name);
            Assertions.assertEquals(name, field.name());
            checkTypeName(field.type(), symbol("int"));
        }
    }

    @Test
    public void testFieldBit() {
        for (int i = 1; i < 64; i++) {
            var fields = parseFields("a:%d int64;".formatted(i));
            var field = fields.get(identifier("a"));
            Assertions.assertEquals(identifier("a"), field.name());
            var bf = field.bitfield().must();
            Assertions.assertEquals(BigInteger.valueOf(i), integer(bf).value());
        }
    }

    @Test
    public void testFieldBitMix() {
        var a = randVarName(8);
        var bit = ThreadLocalRandom.current().nextInt(0, 64) + 1;
        var b = randVarName(8);
        var fields = parseFields("%s:%d, %s int64;".formatted(a, bit, b));
        var af = fields.get(a);
        Assertions.assertEquals(a, af.name());
        var bitfield = af.bitfield().must();
        Assertions.assertEquals(BigInteger.valueOf(bit), integer(bitfield).value());
        var bf = fields.get(b);
        Assertions.assertEquals(b, bf.name());
        Assertions.assertTrue(bf.bitfield().none());
    }

    @Test
    public void testFieldReferenceType() {
        final int n = 8;
        var names = anyNames(RandVarFuncName, 6, n);
        var types = anyNames(RandTypeSymbol, 10, n);
        var code = new StringBuilder();
        for (int i = 0; i < n; i++) {
            code.append(names.get(i)).append(" ").append(types.get(i)).append(";");
        }
        var fields = parseFields(code.toString());
        Assertions.assertEquals(n, fields.size());
        for (int i = 0; i < n; i++) {
            var f = fields.get(names.get(i));
            Assertions.assertEquals(names.get(i), f.name());
            checkTypeName(f.type(), types.get(i));
        }
    }

    @Test
    public void testFieldLocalType() {
        var a = randVarName(6);
        var b = randVarName(6);
        var bt = randTypeSymbol(12);
        var code = "%s struct{%s %s;};".formatted(a, b, bt);
        var fields = parseFields(code);
        Assertions.assertEquals(1, fields.size());
        var f = fields.get(a);
        Assertions.assertEquals(a, f.name());

        var def = ((UnnamedStructureType) f.type()).definition();
        Assertions.assertFalse(def.named());
        Assertions.assertFalse(def.union());
        Assertions.assertEquals(1, def.fields().size());
        var bf = def.fields().get(b);
        Assertions.assertEquals(b, bf.name());
        checkTypeName(bf.type(), bt);
    }

    @Test
    public void testArrayField1() {
        {
            var fields = parseFields("a []int;");
            var field = fields.get(identifier("a"));
            var type = (ArrayStructureType) field.type();
            checkTypeName(type.elementType(), symbol("int"));
            Assertions.assertTrue(type.length().none());
        }
        {
            var length = randInt(10, 100);
            var fields = parseFields("a [%d]int;".formatted(length));
            var field = fields.get(identifier("a"));
            var type = (ArrayStructureType) field.type();
            checkTypeName(type.elementType(), symbol("int"));
            Assertions.assertEquals(length, integer(type.length()).value());
        }
    }

    @Test
    public void testArrayField2() {
        {
            var length = randInt(10, 100);
            var fields = parseFields("a [][%d]int;".formatted(length));
            var at = (ArrayStructureType) fields.get(identifier("a")).type();
            Assertions.assertTrue(at.length().none());
            var eat = (ArrayStructureType) at.elementType();
            Assertions.assertEquals(length, integer(eat.length()).value());
            checkTypeName(eat.elementType(), symbol("int"));
        }
        {
            var len1 = randInt(10, 100);
            var len2 = randInt(10, 100);
            var fields = parseFields("a [%s][%s]int;".formatted(len1, len2));
            var at = (ArrayStructureType) fields.get(identifier("a")).type();
            Assertions.assertEquals(len1, integer(at.length()).value());
            var eat = (ArrayStructureType) at.elementType();
            Assertions.assertEquals(len2, integer(eat.length()).value());
            checkTypeName(eat.elementType(), symbol("int"));
        }
    }

    @Test
    public void testArrayField3() {
        {
            var fields = parseFields("a []struct{a int;};");
            var at = (ArrayStructureType) fields.get(identifier("a")).type();
            Assertions.assertInstanceOf(UnnamedStructureType.class, at.elementType());
            Assertions.assertTrue(at.length().none());
        }
        {
            var len = randInt(10, 100);
            var fields = parseFields("a [%d]struct{a int;};".formatted(len));
            var at = (ArrayStructureType) fields.get(identifier("a")).type();
            Assertions.assertInstanceOf(UnnamedStructureType.class, at.elementType());
            Assertions.assertEquals(len, integer(at.length()).value());
        }
    }


}
