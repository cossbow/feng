package org.cossbow.feng.util.json;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test the minimal JSON parser with clang AST examples.
 */
public class JsonParserTest {

    @Test
    public void testSimpleObject() {
        var r = JsonParser.parse("""
                {"kind": "FunctionDecl", "name": "printf"}
                """);
        assertTrue(r.isObject());
        assertEquals("FunctionDecl", r.get("kind").asText());
        assertEquals("printf", r.get("name").asText());
    }

    @Test
    public void testArray() {
        var r = JsonParser.parse("""
                [1, 2, 3]
                """);
        assertTrue(r.isArray());
        assertEquals(3, r.size());
        assertEquals(1, r.get(0).asInt());
    }

    @Test
    public void testNested() {
        var r = JsonParser.parse("""
                {
                    "inner": [
                        {"kind": "ParmVarDecl", "name": "fmt"},
                        {"kind": "ParmVarDecl", "name": "..."}
                    ]
                }
                """);

        var inner = r.get("inner");
        assertTrue(inner.isArray());
        assertEquals(2, inner.size());

        var first = inner.get(0);
        assertEquals("ParmVarDecl", first.get("kind").asText());
        assertEquals("fmt", first.get("name").asText());
    }

    @Test
    public void testQualType() {
        var r = JsonParser.parse("""
                {"type": {"qualType": "int (const char *, ...)"}}
                """);
        assertEquals("int (const char *, ...)",
                r.get("type").get("qualType").asText());
    }

    @Test
    public void testBoolAndNull() {
        var r = JsonParser.parse("""
                {"isImplicit": true, "loc": null, "isUsed": false}
                """);
        assertTrue(r.get("isImplicit").asBool());
        assertFalse(r.get("isUsed").asBool());
        assertTrue(r.get("loc").isNull());
    }

    @Test
    public void testIntegerValues() {
        var r = JsonParser.parse("""
                {"line": 42, "col": 7, "negative": -1}
                """);
        assertEquals(42, r.get("line").asInt());
        assertEquals(7, r.get("col").asInt());
        assertEquals(-1, r.get("negative").asInt());
    }

    @Test
    public void testClangFunctionDecl() {
        // A stripped-down clang AST fragment
        var json = """
                {
                  "kind": "FunctionDecl",
                  "name": "open",
                  "type": {
                    "qualType": "int (const char *, int, ...)"
                  },
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
                """;

        var r = JsonParser.parse(json);
        assertEquals("FunctionDecl", r.get("kind").asText());
        assertEquals("open", r.get("name").asText());
        assertEquals("int (const char *, int, ...)",
                r.get("type").get("qualType").asText());

        var params = r.get("inner");
        assertEquals(2, params.size());
        assertEquals("path", params.get(0).get("name").asText());
        assertEquals("const char *",
                params.get(0).get("type").get("qualType").asText());
    }

    @Test
    public void testUnicodeEscape() {
        var r = JsonParser.parse("""
                {"name": "\\u0041BC"}
                """);
        assertEquals("ABC", r.get("name").asText());
    }

    @Test
    public void testTrailingContentRejected() {
        assertThrows(RuntimeException.class, () ->
                JsonParser.parse("{} extra"));
    }

    @Test
    public void testEmpty() {
        assertThrows(RuntimeException.class, () ->
                JsonParser.parse(""));
    }

    @Test
    public void testAllPrimitives() {
        var r = JsonParser.parse("""
                {"s":"hello", "n": 42, "b": true, "x": null}
                """);
        assertEquals("hello", r.get("s").asText());
        assertEquals(42, r.get("n").asInt());
        assertTrue(r.get("b").asBool());
        assertTrue(r.get("x").isNull());
    }

    @Test
    public void testNestedArrayNavigation() {
        var r = JsonParser.parse("""
                {"outer": [{"val": 1}, {"val": 2}]}
                """);
        var outer = r.get("outer");
        assertEquals(1, outer.get(0).get("val").asInt());
        assertEquals(2, outer.get(1).get("val").asInt());
    }
}
