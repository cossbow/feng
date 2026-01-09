package org.cossbow.feng.analysis;

import org.cossbow.feng.ast.Definition;
import org.cossbow.feng.ast.Source;
import org.cossbow.feng.ast.dcl.*;
import org.cossbow.feng.ast.mod.GlobalDeclaration;
import org.cossbow.feng.ast.mod.GlobalDefinition;
import org.cossbow.feng.ast.oop.ClassDefinition;
import org.cossbow.feng.ast.stmt.ArrayTuple;
import org.cossbow.feng.ast.stmt.DeclarationStatement;
import org.cossbow.feng.parser.BaseParseTest;
import org.cossbow.feng.parser.SampleParseTest;
import org.cossbow.feng.util.ErrorUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static org.cossbow.feng.ast.dcl.Primitive.*;

public class TestTypeDeducer {

    final TypeDeducer deducer = new TypeDeducer(EmptySymbolContext.EMPTY);

    Primitive getPri(TypeDeclarer td) {
        return ((PrimitiveTypeDeclarer) td).primitive();
    }

    @Test
    public void testPrimitive() {
        var code = "var i, j, ok = uint32(1), float64(1), true;";
        var stmt = (DeclarationStatement) BaseParseTest.doParseLocal(code);
        var td = (TupleTypeDeclarer) deducer.visit(stmt.init().must());
        var ps = td.tuple().stream().map(this::getPri).toList();
        Assertions.assertEquals(List.of(UINT32, FLOAT64, BOOL), ps);
    }

    @Test
    public void testDefinedType() {
        var src = parseFile("define_type.feng");
        System.out.println(src);
        var gd = src.declarations().getFirst();
        var tpd = (TupleTypeDeclarer) deducer.visit(gd.init().must());
        Assertions.assertEquals(1, tpd.tuple().size());
        var dtd = ((DefinedTypeDeclarer) tpd.tuple().getFirst());
        var def = src.definitions().getFirst();
        Assertions.assertEquals(dtd.definedType().symbol().name(), def.name());
        Assertions.assertTrue(dtd.definedType().symbol().module().none());
        Assertions.assertTrue(dtd.definedType().generic().isEmpty());
        Assertions.assertTrue(dtd.reference().has());
        Assertions.assertEquals(ReferenceType.STRONG, dtd.reference().must().type());
        Assertions.assertFalse(dtd.reference().must().required());
        Assertions.assertFalse(dtd.reference().must().immutable());
    }

    //

    Source parseFile(String name) {
        try (var is = getFile(name)) {
            Assertions.assertNotNull(is);
            return BaseParseTest.doParseFile(is, name);
        } catch (IOException e) {
            return ErrorUtil.io(e);
        }
    }

    InputStream getFile(String name) {
        return SampleParseTest.class.getResourceAsStream("/analysis/" + name);
    }

}
