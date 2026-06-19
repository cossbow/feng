package org.cossbow.feng.c2feng.parse;

import org.cossbow.feng.c2feng.convert.C2FengConverter;
import org.junit.jupiter.api.Test;

import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test {@link JsonAstParser} with simulated clang JSON AST dumps.
 */
public class JsonAstParserTest {

    @Test
    public void testStructFromClang() throws Exception {
        var json = """
                {
                  "kind": "TranslationUnitDecl",
                  "inner": [
                    {
                      "kind": "RecordDecl",
                      "name": "Point",
                      "tagUsed": "struct",
                      "completeDefinition": true,
                      "inner": [
                        {
                          "kind": "FieldDecl",
                          "name": "x",
                          "type": { "qualType": "int" }
                        },
                        {
                          "kind": "FieldDecl",
                          "name": "y",
                          "type": { "qualType": "double" }
                        }
                      ]
                    }
                  ]
                }
                """;

        var converter = new C2FengConverter("c_test");
        new JsonAstParser(converter).parse(json);

        var out = new StringWriter();
        converter.write(out);
        var result = out.toString();

        System.out.println("=== testStructFromClang ===");
        System.out.println(result);

        assertTrue(result.contains("struct Point"));
        assertTrue(result.contains("x int"));
        assertTrue(result.contains("y float64"));
    }

    @Test
    public void testFunctionFromClang() throws Exception {
        var json = """
                {
                  "kind": "TranslationUnitDecl",
                  "inner": [
                    {
                      "kind": "FunctionDecl",
                      "name": "open",
                      "type": { "qualType": "int (const char *, int)" },
                      "inner": [
                        {
                          "kind": "ParmVarDecl",
                          "name": "path",
                          "type": { "qualType": "const char *" }
                        },
                        {
                          "kind": "ParmVarDecl",
                          "name": "flags",
                          "type": { "qualType": "int" }
                        }
                      ]
                    }
                  ]
                }
                """;

        var converter = new C2FengConverter("c_test");
        new JsonAstParser(converter).parse(json);

        var out = new StringWriter();
        converter.write(out);
        var result = out.toString();

        System.out.println("=== testFunctionFromClang ===");
        System.out.println(result);

        assertTrue(result.contains("func open("));
        // path: const char * → pointer → uint64
        // flags: int → int
        assertTrue(result.contains("uint64"));
        assertTrue(result.contains("int"));
    }

    @Test
    public void testEnumFromClang() throws Exception {
        var json = """
                {
                  "kind": "TranslationUnitDecl",
                  "inner": [
                    {
                      "kind": "EnumDecl",
                      "name": "Color",
                      "completeDefinition": true,
                      "inner": [
                        {
                          "kind": "EnumConstantDecl",
                          "name": "RED",
                          "value": 0
                        },
                        {
                          "kind": "EnumConstantDecl",
                          "name": "GREEN",
                          "value": 1
                        },
                        {
                          "kind": "EnumConstantDecl",
                          "name": "BLUE",
                          "value": 5
                        }
                      ]
                    }
                  ]
                }
                """;

        var converter = new C2FengConverter("c_test");
        new JsonAstParser(converter).parse(json);

        var out = new StringWriter();
        converter.write(out);
        var result = out.toString();

        System.out.println("=== testEnumFromClang ===");
        System.out.println(result);

        assertTrue(result.contains("const Color$RED int = 0;"));
        assertTrue(result.contains("const Color$GREEN int = 1;"));
        assertTrue(result.contains("const Color$BLUE int = 5;"));
    }

    @Test
    public void testTypedefExpansion() throws Exception {
        var json = """
                {
                  "kind": "TranslationUnitDecl",
                  "inner": [
                    {
                      "kind": "TypedefDecl",
                      "name": "size_t",
                      "type": { "qualType": "unsigned long" }
                    },
                    {
                      "kind": "FunctionDecl",
                      "name": "malloc",
                      "type": { "qualType": "void *(unsigned long)" },
                      "inner": [
                        {
                          "kind": "ParmVarDecl",
                          "name": "size",
                          "type": { "qualType": "size_t" }
                        }
                      ]
                    }
                  ]
                }
                """;

        var converter = new C2FengConverter("c_test");
        new JsonAstParser(converter).parse(json);

        var out = new StringWriter();
        converter.write(out);
        var result = out.toString();

        System.out.println("=== testTypedefExpansion ===");
        System.out.println(result);

        // size_t → unsigned long → uint64
        // Pointer return: void* → uint64
        assertTrue(result.contains("uint64"));
        assertTrue(result.contains("size") && result.contains("func malloc("));
        assertTrue(result.contains("export"));
    }

    @Test
    public void testStaticFunctionSkipped() throws Exception {
        var json = """
                {
                  "kind": "TranslationUnitDecl",
                  "inner": [
                    {
                      "kind": "FunctionDecl",
                      "name": "helper",
                      "storageClass": "static",
                      "type": { "qualType": "void (int)" },
                      "inner": [
                        {
                          "kind": "ParmVarDecl",
                          "name": "x",
                          "type": { "qualType": "int" }
                        }
                      ]
                    }
                  ]
                }
                """;

        var converter = new C2FengConverter("c_test");
        new JsonAstParser(converter).parse(json);

        var out = new StringWriter();
        converter.write(out);
        var result = out.toString();

        System.out.println("=== testStaticFunctionSkipped ===");
        System.out.println(result);

        assertFalse(result.contains("helper"));
    }

    @Test
    public void testCombinedHeader() throws Exception {
        var json = """
                {
                  "kind": "TranslationUnitDecl",
                  "inner": [
                    {
                      "kind": "RecordDecl",
                      "name": "Point",
                      "tagUsed": "struct",
                      "completeDefinition": true,
                      "inner": [
                        { "kind": "FieldDecl", "name": "x", "type": { "qualType": "int" } },
                        { "kind": "FieldDecl", "name": "y", "type": { "qualType": "int" } }
                      ]
                    },
                    {
                      "kind": "EnumDecl",
                      "name": "Color",
                      "completeDefinition": true,
                      "inner": [
                        { "kind": "EnumConstantDecl", "name": "RED", "value": 0 },
                        { "kind": "EnumConstantDecl", "name": "GREEN", "value": 1 },
                        { "kind": "EnumConstantDecl", "name": "BLUE", "value": 2 }
                      ]
                    },
                    {
                      "kind": "FunctionDecl",
                      "name": "create_point",
                      "type": { "qualType": "struct Point (int, int)" },
                      "inner": [
                        { "kind": "ParmVarDecl", "name": "x", "type": { "qualType": "int" } },
                        { "kind": "ParmVarDecl", "name": "y", "type": { "qualType": "int" } }
                      ]
                    },
                    {
                      "kind": "FunctionDecl",
                      "name": "destroy_point",
                      "type": { "qualType": "void (struct Point *)" },
                      "inner": [
                        { "kind": "ParmVarDecl", "name": "p", "type": { "qualType": "struct Point *" } }
                      ]
                    }
                  ]
                }
                """;

        var converter = new C2FengConverter("c_my_lib");
        new JsonAstParser(converter).parse(json);

        var out = new StringWriter();
        converter.write(out);
        var result = out.toString();

        System.out.println("=== testCombinedHeader ===");
        System.out.println(result);

        assertTrue(result.contains("struct Point"));
        assertTrue(result.contains("const Color$RED int = 0;"));
        assertTrue(result.contains("func create_point("));
        assertTrue(result.contains("func destroy_point(p uint64)"));
    }
}
