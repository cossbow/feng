package org.cossbow.feng.c2feng.convert;

import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.mod.ModulePath;
import org.cossbow.feng.c2feng.model.*;
import org.junit.jupiter.api.Test;

import java.io.StringWriter;
import java.nio.file.Path;
import java.util.List;

import org.cossbow.feng.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link C2FengConverter}: C declaration → Fēng metadata conversion.
 */
public class C2FengConverterTest {

    private C2FengConverter newTestConverter(String name) {
        return new C2FengConverter(new ModulePath(
                new Identifier(name), Path.of("")));
    }

    @Test
    public void testStruct() throws Exception {
        var conv = newTestConverter("c_test");

        conv.addStruct(new CStructType("Point", List.of(
                new CField("x", new CPrimitiveType("int")),
                new CField("y", new CPrimitiveType("double")),
                new CField("data", new CPointerType(new CPrimitiveType("void"), false))
        ), true));

        var out = new StringWriter();
        conv.write(out);
        var result = out.toString();

        System.out.println("=== testStruct ===");
        System.out.println(result);

        assertTrue(result.contains("struct Point"));
        assertTrue(result.contains("x int"));
        assertTrue(result.contains("y float64"));
        assertTrue(result.contains("data uint64"));
    }

    @Test
    public void testUnion() throws Exception {
        var conv = newTestConverter("c_test");

        conv.addUnion(new CUnionType("Data", List.of(
                new CField("i", new CPrimitiveType("int")),
                new CField("f", new CPrimitiveType("float")),
                new CField("p", new CPointerType(new CPrimitiveType("void"), false))
        ), true));

        var out = new StringWriter();
        conv.write(out);
        var result = out.toString();

        System.out.println("=== testUnion ===");
        System.out.println(result);

        assertTrue(result.contains("union Data"));
        assertTrue(result.contains("i int"));
        assertTrue(result.contains("f float32"));
        assertTrue(result.contains("p uint64"));
    }

    @Test
    public void testEnum() throws Exception {
        var conv = newTestConverter("c_test");

        conv.addEnum(new CEnumType("Color", List.of(
                new CEnumConstant("RED", Optional.empty()),
                new CEnumConstant("GREEN", Optional.empty()),
                new CEnumConstant("BLUE", Optional.of(5L))
        )));

        var out = new StringWriter();
        conv.write(out);
        var result = out.toString();

        System.out.println("=== testEnum ===");
        System.out.println(result);

        // Enum values should be emitted as const int constants
        assertTrue(result.contains("const Color$RED int = 0;"));
        assertTrue(result.contains("const Color$GREEN int = 1;"));
        assertTrue(result.contains("const Color$BLUE int = 5;"));
    }

    @Test
    public void testFunction() throws Exception {
        var conv = newTestConverter("c_test");

        conv.addFunction(new CFunction("open",
                List.of(
                        new CField("path", new CPointerType(new CPrimitiveType("char"), true)),
                        new CField("flags", new CPrimitiveType("int"))
                ),
                new CPrimitiveType("int"),
                false,
                CLinkage.DEFAULT));

        var out = new StringWriter();
        conv.write(out);
        var result = out.toString();

        System.out.println("=== testFunction ===");
        System.out.println(result);

        // Pointer params → uint64, should include export
        assertTrue(result.contains("export"));
        assertTrue(result.contains("func open("));
        assertTrue(result.contains("uint64"));
        assertTrue(result.contains("int"));
    }

    @Test
    public void testVoidFunc() throws Exception {
        var conv = newTestConverter("c_test");

        conv.addFunction(new CFunction("exit",
                List.of(new CField("code", new CPrimitiveType("int"))),
                new CPrimitiveType("void"),
                false,
                CLinkage.DEFAULT));

        var out = new StringWriter();
        conv.write(out);
        var result = out.toString();

        System.out.println("=== testVoidFunc ===");
        System.out.println(result);

        // void return → no return type in output
        assertTrue(result.contains("func exit(code int);"));
        assertFalse(result.contains("void"));
    }

    @Test
    public void testVariadicFunc() {
        var conv = newTestConverter("c_test");

        assertThrows(UnsupportedOperationException.class, () ->
                conv.addFunction(new CFunction("printf",
                        List.of(
                                new CField("fmt", new CPointerType(new CPrimitiveType("char"), true))
                        ),
                        new CPrimitiveType("int"),
                        true,
                        CLinkage.DEFAULT)));
    }

    @Test
    public void testGlobalVar() throws Exception {
        var conv = newTestConverter("c_test");

        // Default global → export
        conv.addGlobalVar(new CGlobalVar("errno",
                new CPrimitiveType("int"), false, CLinkage.DEFAULT));
        // static → no export
        conv.addGlobalVar(new CGlobalVar("internal",
                new CPrimitiveType("int"), false, CLinkage.STATIC));
        // extern → skipped
        conv.addGlobalVar(new CGlobalVar("external_var",
                new CPrimitiveType("int"), false, CLinkage.EXTERN));

        var out = new StringWriter();
        conv.write(out);
        var result = out.toString();

        System.out.println("=== testGlobalVar ===");
        System.out.println(result);

        assertTrue(result.contains("export var errno int;"));
        assertTrue(result.contains("var internal int;"));   // no export
        assertFalse(result.contains("external_var"));       // extern skipped
    }

    @Test
    public void testFixedArray() throws Exception {
        var conv = newTestConverter("c_test");

        conv.addStruct(new CStructType("Buffer", List.of(
                new CField("data", new CArrayType(new CPrimitiveType("char"), Optional.of(256))),
                new CField("len", new CPrimitiveType("int"))
        ), true));

        var out = new StringWriter();
        conv.write(out);
        var result = out.toString();

        System.out.println("=== testFixedArray ===");
        System.out.println(result);

        assertTrue(result.contains("[256]int8"));
    }

    @Test
    public void testBitfield() throws Exception {
        var conv = newTestConverter("c_test");

        conv.addStruct(new CStructType("Flags", List.of(
                new CField("a", new CPrimitiveType("unsigned int"), Optional.of(1)),
                new CField("b", new CPrimitiveType("unsigned int"), Optional.of(3))
        ), true));

        var out = new StringWriter();
        conv.write(out);
        var result = out.toString();

        System.out.println("=== testBitfield ===");
        System.out.println(result);

        assertTrue(result.contains("struct Flags"));
    }

    @Test
    public void testStaticFuncSkipped() throws Exception {
        var conv = newTestConverter("c_test");

        conv.addFunction(new CFunction("helper",
                List.of(new CField("x", new CPrimitiveType("int"))),
                new CPrimitiveType("void"),
                false,
                CLinkage.STATIC));

        var out = new StringWriter();
        conv.write(out);
        var result = out.toString();

        System.out.println("=== testStaticFuncSkipped ===");
        System.out.println(result);

        // static function should not appear in metadata
        assertFalse(result.contains("helper"));
    }

    @Test
    public void testCombinedHeader() throws Exception {
        var conv = newTestConverter("c_my_lib");

        // Simulate the full content of a simple C header
        conv.addStruct(new CStructType("Point", List.of(
                new CField("x", new CPrimitiveType("int")),
                new CField("y", new CPrimitiveType("int"))
        ), true));

        conv.addEnum(new CEnumType("Color", List.of(
                new CEnumConstant("RED", Optional.empty()),
                new CEnumConstant("GREEN", Optional.empty()),
                new CEnumConstant("BLUE", Optional.empty())
        )));

        conv.addFunction(new CFunction("create_point",
                List.of(
                        new CField("x", new CPrimitiveType("int")),
                        new CField("y", new CPrimitiveType("int"))
                ),
                new CStructType("Point", List.of(), true), // returns struct
                false,
                CLinkage.DEFAULT));

        conv.addFunction(new CFunction("destroy_point",
                List.of(new CField("p", new CPointerType(new CStructType("Point", List.of(), true), false))),
                new CPrimitiveType("void"),
                false,
                CLinkage.DEFAULT));

        conv.addGlobalVar(new CGlobalVar("version",
                new CPrimitiveType("int"), true, CLinkage.DEFAULT));

        var out = new StringWriter();
        conv.write(out);
        var result = out.toString();

        System.out.println("=== testCombinedHeader ===");
        System.out.println(result);

        // Verify complete metadata output
        assertTrue(result.contains("struct Point"));
        assertTrue(result.contains("const Color$RED int = 0;"));
        assertTrue(result.contains("func create_point("));
        assertTrue(result.contains("Point;") || result.contains("Point\n"));
        assertTrue(result.contains("func destroy_point(p uint64)"));
        assertTrue(result.contains("export const version int"));
        assertFalse(result.contains("func main"));
    }
}
