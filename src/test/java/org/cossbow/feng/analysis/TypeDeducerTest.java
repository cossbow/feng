package org.cossbow.feng.analysis;

import org.cossbow.feng.ast.Source;
import org.cossbow.feng.ast.dcl.*;
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

public class TypeDeducerTest {

    final TypeDeducer deducer = new TypeDeducer(EmptySymbolContext.EMPTY);

    Primitive getPri(TypeDeclarer td) {
        return ((PrimitiveTypeDeclarer) td).primitive();
    }

    @Test
    public void testPrimitive() {
        var code = "var i, j, ok = uint32(1), float64(1), true;";
        var stmt = (DeclarationStatement) BaseParseTest.doParseLocal(code);
        var td = deducer.visit(stmt.init().must());
        var ps = td.tuple().stream().map(this::getPri).toList();
        Assertions.assertEquals(List.of(UINT32, FLOAT64, BOOL), ps);
    }

    @Test
    public void testDefinedType() {
        var src = parseFile("define_type.feng");
        var gd = src.variables().getFirst();
        var dtd = (DefinedTypeDeclarer) deducer.visit(gd.init().must());
        var def = src.types().getFirst();
        Assertions.assertEquals(dtd.definedType().symbol(), def.symbol());
        Assertions.assertTrue(dtd.definedType().symbol().module().none());
        Assertions.assertTrue(dtd.definedType().generic().isEmpty());
        Assertions.assertTrue(dtd.refer().has());
        Assertions.assertEquals(ReferKind.STRONG, dtd.refer().must().kind());
        Assertions.assertFalse(dtd.refer().must().required());
        Assertions.assertFalse(dtd.refer().must().immutable());
    }

    @Test
    public void testReturnType() {
        var src = parseFile("return_type.feng");
        var f = src.table();
    }

    //

    Source parseFile(String name) {
        try (var is = getFile(name)) {
            Assertions.assertNotNull(is);
            return BaseParseTest.doParseFile(is);
        } catch (IOException e) {
            return ErrorUtil.io(e);
        }
    }

    InputStream getFile(String name) {
        return SampleParseTest.class.getResourceAsStream(
                "/analysis/deduce/" + name);
    }

}
