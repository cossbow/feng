package org.cossbow.feng.c2feng.parse;

import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.mod.ModulePath;
import org.cossbow.feng.c2feng.convert.C2FengConverter;
import org.junit.jupiter.api.Test;

import java.io.StringWriter;
import java.nio.file.Path;

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

        var converter = new C2FengConverter(new ModulePath(new Identifier("c_test"), Path.of("")));
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

        var converter = new C2FengConverter(new ModulePath(new Identifier("c_test"), Path.of("")));
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

        var converter = new C2FengConverter(new ModulePath(new Identifier("c_test"), Path.of("")));
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

        var converter = new C2FengConverter(new ModulePath(new Identifier("c_test"), Path.of("")));
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

        var converter = new C2FengConverter(new ModulePath(new Identifier("c_test"), Path.of("")));
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

        var converter = new C2FengConverter(new ModulePath(new Identifier("c_my_lib"), Path.of("")));
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

    @Test
    public void testTrailingConstPointer() throws Exception {
        var json = """
                {
                  "kind": "TranslationUnitDecl",
                  "inner": [
                    {
                      "kind": "FunctionDecl",
                      "name": "register_cb",
                      "type": { "qualType": "void (char *const, int)" },
                      "inner": [
                        {
                          "kind": "ParmVarDecl",
                          "name": "name",
                          "type": { "qualType": "char *const" }
                        },
                        {
                          "kind": "ParmVarDecl",
                          "name": "flag",
                          "type": { "qualType": "int" }
                        }
                      ]
                    }
                  ]
                }
                """;

        var converter = new C2FengConverter(new ModulePath(new Identifier("c_test"), Path.of("")));
        new JsonAstParser(converter).parse(json);

        var out = new StringWriter();
        converter.write(out);
        var result = out.toString();

        System.out.println("=== testTrailingConstPointer ===");
        System.out.println(result);

        // char *const should be recognized as a pointer → uint64, not degraded to int
        assertTrue(result.contains("name uint64"), "char *const should map to uint64");
        assertTrue(result.contains("func register_cb("));
    }

    @Test
    public void testConstCharConstPointer() throws Exception {
        var json = """
                {
                  "kind": "TranslationUnitDecl",
                  "inner": [
                    {
                      "kind": "FunctionDecl",
                      "name": "foo",
                      "type": { "qualType": "int (const char *const)" },
                      "inner": [
                        {
                          "kind": "ParmVarDecl",
                          "name": "s",
                          "type": { "qualType": "const char *const" }
                        }
                      ]
                    }
                  ]
                }
                """;

        var converter = new C2FengConverter(new ModulePath(new Identifier("c_test"), Path.of("")));
        new JsonAstParser(converter).parse(json);

        var out = new StringWriter();
        converter.write(out);
        var result = out.toString();

        System.out.println("=== testConstCharConstPointer ===");
        System.out.println(result);

        // const char *const → strip leading const, then strip trailing const → char * → pointer → uint64
        assertTrue(result.contains("s uint64"), "const char *const should map to uint64");
    }

    @Test
    public void testTypedefAnonymousStruct() throws Exception {
        // Simulates real Clang output for:
        //   typedef struct { long long quot; long long rem; } lldiv_t;
        // RecordDecl is at TOP level with empty name; TypedefDecl links via
        // ElaboratedType.ownedTagDecl.id.
        var json = """
                {
                  "kind": "TranslationUnitDecl",
                  "inner": [
                    {
                      "id": "0xabc",
                      "kind": "RecordDecl",
                      "tagUsed": "struct",
                      "completeDefinition": true,
                      "inner": [
                        { "kind": "FieldDecl", "name": "quot", "type": { "qualType": "long long" } },
                        { "kind": "FieldDecl", "name": "rem", "type": { "qualType": "long long" } }
                      ]
                    },
                    {
                      "kind": "TypedefDecl",
                      "name": "lldiv_t",
                      "type": { "qualType": "struct lldiv_t" },
                      "inner": [
                        {
                          "kind": "ElaboratedType",
                          "type": { "qualType": "struct lldiv_t" },
                          "ownedTagDecl": {
                            "id": "0xabc",
                            "kind": "RecordDecl",
                            "name": ""
                          }
                        }
                      ]
                    },
                    {
                      "kind": "FunctionDecl",
                      "name": "ldiv",
                      "type": { "qualType": "lldiv_t (long long, long long)" },
                      "inner": [
                        { "kind": "ParmVarDecl", "name": "numer", "type": { "qualType": "long long" } },
                        { "kind": "ParmVarDecl", "name": "denom", "type": { "qualType": "long long" } }
                      ]
                    }
                  ]
                }
                """;

        var converter = new C2FengConverter(new ModulePath(new Identifier("m"), Path.of("")));
        new JsonAstParser(converter).parse(json);

        var out = new StringWriter();
        converter.write(out);
        var result = out.toString();

        System.out.println("=== testTypedefAnonymousStruct ===");
        System.out.println(result);

        // The struct definition should be generated
        assertTrue(result.contains("struct lldiv_t"), "struct lldiv_t should be in output");
        // The function should reference m$lldiv_t as return type
        assertTrue(result.contains("m$lldiv_t"), "m$lldiv_t should be referenced");
    }

    @Test
    public void testMultipleAnonymousStructs() throws Exception {
        // Verify that dedup key collision (all empty-named RecordDecls
        // share "RecordDecl:") does NOT skip anonymous structs after
        // the first one.
        var json = """
                {
                  "kind": "TranslationUnitDecl",
                  "inner": [
                    {
                      "id": "0xaaa",
                      "kind": "RecordDecl",
                      "tagUsed": "struct",
                      "completeDefinition": true,
                      "inner": [
                        { "kind": "FieldDecl", "name": "quot", "type": { "qualType": "int" } },
                        { "kind": "FieldDecl", "name": "rem", "type": { "qualType": "int" } }
                      ]
                    },
                    {
                      "kind": "TypedefDecl",
                      "name": "div_t",
                      "type": { "qualType": "struct div_t" },
                      "inner": [
                        {
                          "kind": "ElaboratedType",
                          "type": { "qualType": "struct div_t" },
                          "ownedTagDecl": { "id": "0xaaa", "kind": "RecordDecl", "name": "" }
                        }
                      ]
                    },
                    {
                      "id": "0xbbb",
                      "kind": "RecordDecl",
                      "name": "",
                      "tagUsed": "struct",
                      "completeDefinition": true,
                      "inner": [
                        { "kind": "FieldDecl", "name": "quot", "type": { "qualType": "long long" } },
                        { "kind": "FieldDecl", "name": "rem", "type": { "qualType": "long long" } }
                      ]
                    },
                    {
                      "kind": "TypedefDecl",
                      "name": "lldiv_t",
                      "type": { "qualType": "struct lldiv_t" },
                      "inner": [
                        {
                          "kind": "ElaboratedType",
                          "type": { "qualType": "struct lldiv_t" },
                          "ownedTagDecl": { "id": "0xbbb", "kind": "RecordDecl", "name": "" }
                        }
                      ]
                    }
                  ]
                }
                """;

        var converter = new C2FengConverter(new ModulePath(new Identifier("m"), Path.of("")));
        new JsonAstParser(converter).parse(json);

        var out = new StringWriter();
        converter.write(out);
        var result = out.toString();

        System.out.println("=== testMultipleAnonymousStructs ===");
        System.out.println(result);

        // Both structs should appear — the second one must not be dedup'd
        assertTrue(result.contains("struct div_t"), "struct div_t should be in output");
        assertTrue(result.contains("struct lldiv_t"), "struct lldiv_t should be in output");
    }
}
