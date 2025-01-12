package org.cossbow.feng.parser;

import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.struct.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public class StructureParseTest extends BaseParseTest {

    @Test
    public void testTypeDefine() {
        for (var domain : Domain.values()) {
            var name = randTypeName(32);
            var code = "%s %s{}".formatted(domain, name);
            var def = (StructureDefinition) doParseDefinition(code);
            Assertions.assertEquals(name, def.name().orElseThrow());
            Assertions.assertEquals(domain == Domain.union, def.union());
            Assertions.assertTrue(def.members().isEmpty());
            Assertions.assertTrue(def.generic().isEmpty());
        }
    }

    List<StructureMember> parseMembers(String members) {
        var code = "struct Foo { %s }".formatted(members);
        var def = (StructureDefinition) doParseDefinition(code);
        return def.members();
    }

    @Test
    public void testFieldName() {
        var names = anyNames(RandVarFuncName, 8, 10);
        var members = parseMembers("%s int;".formatted(idList(names)));
        for (int i = 0; i < names.size(); i++) {
            var field = (StructureField) members.get(i);
            Assertions.assertEquals(names.get(i), field.name());
        }
    }

    @Test
    public void testFieldBit() {
        for (int i = 1; i < 64; i++) {
            var field = (StructureField) parseMembers("a:%d int64;".formatted(i)).getFirst();
            var bitfield = field.bitfield().orElseThrow();
            Assertions.assertEquals(BigInteger.valueOf(i), integer(bitfield).value());
        }
    }

    @Test
    public void testFieldBitMix() {
        var a = randVarFuncName(8);
        var bit = ThreadLocalRandom.current().nextInt(0, 64) + 1;
        var b = randVarFuncName(8);
        var members = parseMembers("%s:%d, %s int64;".formatted(a, bit, b));
        var af = (StructureField) members.getFirst();
        Assertions.assertEquals(a, af.name());
        Assertions.assertEquals(BigInteger.valueOf(bit), integer(af.bitfield().orElseThrow()).value());
        var bf = (StructureField) members.getLast();
        Assertions.assertEquals(b, bf.name());
        Assertions.assertTrue(bf.bitfield().isEmpty());
    }

    @Test
    public void testFieldReferenceType() {
        final int n = 8;
        var names = anyNames(RandVarFuncName, 6, n);
        var types = anyNames(RandTypeName, 10, n);
        var code = new StringBuilder();
        for (int i = 0; i < n; i++) {
            code.append(names.get(i)).append(" ").append(types.get(i)).append(";");
        }
        var members = parseMembers(code.toString());
        for (int i = 0; i < n; i++) {
            var f = (StructureField) members.get(i);
            Assertions.assertEquals(names.get(i), f.name());
            Assertions.assertEquals(types.get(i), ((DefinedStructureType) f.type()).type().name());
        }
    }

    @Test
    public void testFieldLocalType() {
        var a = randVarFuncName(6);
        var b = randVarFuncName(6);
        var bt = randTypeName(12);
        var code = "%s struct{%s %s;};".formatted(a, b, bt);
        var members = parseMembers(code);
        Assertions.assertEquals(1, members.size());
        var f = (StructureField) members.getFirst();
        Assertions.assertEquals(a, f.name());

        var def = ((UnnamedStructureType) f.type()).definition();
        Assertions.assertTrue(def.name().isEmpty());
        Assertions.assertFalse(def.union());

        var anonMembers = def.members();
        Assertions.assertEquals(1, anonMembers.size());
        var anonField = (StructureField) anonMembers.getFirst();
        Assertions.assertEquals(b, anonField.name());
        Assertions.assertEquals(bt, ((DefinedStructureType) anonField.type()).type().name());
    }

    @Test
    public void testArrayField1() {
        {
            var member = (StructureField) parseMembers("a []int;").getFirst();
            var at = (ArrayStructureType) member.type();
            Assertions.assertInstanceOf(DefinedStructureType.class, at.elementType());
            Assertions.assertTrue(at.length().isEmpty());
        }
        {
            var length = randInt(10, 100);
            var member = (StructureField) parseMembers("a [%d]int;".formatted(length)).getFirst();
            var at = (ArrayStructureType) member.type();
            Assertions.assertInstanceOf(DefinedStructureType.class, at.elementType());
            Assertions.assertEquals(length, integer(at.length().orElseThrow()).value());
        }
    }

    @Test
    public void testArrayField2() {
        {
            var length = randInt(10, 100);
            var member = (StructureField) parseMembers("a [][%d]int;".formatted(length)).getFirst();
            var at = (ArrayStructureType) member.type();
            Assertions.assertTrue(at.length().isEmpty());
            var eat = (ArrayStructureType) at.elementType();
            Assertions.assertEquals(length, integer(eat.length().orElseThrow()).value());
        }
        {
            var len1 = randInt(10, 100);
            var len2 = randInt(10, 100);
            var member = (StructureField) parseMembers("a [%s][%s]int;".formatted(len1, len2)).getFirst();
            var at = (ArrayStructureType) member.type();
            Assertions.assertEquals(len1, integer(at.length().orElseThrow()).value());
            var eat = (ArrayStructureType) at.elementType();
            Assertions.assertEquals(len2, integer(eat.length().orElseThrow()).value());

        }
    }

    @Test
    public void testArrayField3() {
        {
            var member = (StructureField) parseMembers("a []struct{a int;};").getFirst();
            var at = (ArrayStructureType) member.type();
            Assertions.assertInstanceOf(UnnamedStructureType.class, at.elementType());
            Assertions.assertTrue(at.length().isEmpty());
        }
        {
            var len = randInt(10, 100);
            var member = (StructureField) parseMembers("a [%d]struct{a int;};".formatted(len)).getFirst();
            var at = (ArrayStructureType) member.type();
            Assertions.assertInstanceOf(UnnamedStructureType.class, at.elementType());
            Assertions.assertEquals(len, integer(at.length().get()).value());
        }
    }

    @Test
    public void testPart() {
        var types = anyNames(RandTypeName, 8, 20);
        var code = types.stream().map(Identifier::value)
                .collect(Collectors.joining(";", "", ";"));
        var members = parseMembers(code);
        for (int i = 0; i < types.size(); i++) {
            var type = types.get(i);
            var part = (StructurePart) members.get(i);
            Assertions.assertEquals(type, part.type().name());
        }
    }

    enum Domain {struct, union,}

}
