package org.cossbow.feng.util.json;

import java.util.*;

/**
 * Minimal JSON parser for clang AST dump format.
 * <p>
 * Only parses the subset of JSON that clang produces:
 * objects, arrays, strings, integers, true, false, null.
 * No float/exponent support, minimal escape handling.
 */
public class JsonParser {

    private final String src;
    private int pos;

    private JsonParser(String src) {
        this.src = src;
        this.pos = 0;
    }

    /**
     * Parse a JSON string and return the root node.
     */
    public static JsonNode parse(String src) {
        var p = new JsonParser(src);
        var val = p.readValue();
        p.skipWS();
        if (p.pos < src.length()) {
            throw error("unexpected trailing content at " + p.pos);
        }
        return val;
    }

    // ========== value dispatch ==========

    private JsonNode readValue() {
        skipWS();
        if (pos >= src.length()) throw error("unexpected end");
        return switch (src.charAt(pos)) {
            case '{' -> readObject();
            case '[' -> readArray();
            case '"' -> readString();
            case 't', 'f' -> readBool();
            case 'n' -> readNull();
            default -> {
                if (src.charAt(pos) == '-' || isDigit(src.charAt(pos))) {
                    yield readNumber();
                }
                throw error("unexpected character '%c' at %d"
                        .formatted(src.charAt(pos), pos));
            }
        };
    }

    // ========== object ==========

    private JsonNode readObject() {
        pos++; // skip '{'
        var map = new LinkedHashMap<String, JsonNode>();

        skipWS();
        if (pos < src.length() && src.charAt(pos) == '}') {
            pos++;
            return new JsonNode.JsonObject(map);
        }

        while (true) {
            skipWS();
            var key = readString();
            skipWS();
            expect(':');
            skipWS();
            var val = readValue();
            map.put(key.asText(), val);

            skipWS();
            if (pos < src.length() && src.charAt(pos) == ',') {
                pos++;
                continue;
            }
            if (pos < src.length() && src.charAt(pos) == '}') {
                pos++;
                break;
            }
            throw error("expected ',' or '}' in object at " + pos);
        }
        return new JsonNode.JsonObject(map);
    }

    // ========== array ==========

    private JsonNode readArray() {
        pos++; // skip '['
        var list = new ArrayList<JsonNode>();

        skipWS();
        if (pos < src.length() && src.charAt(pos) == ']') {
            pos++;
            return new JsonNode.JsonArray(list);
        }

        while (true) {
            skipWS();
            list.add(readValue());
            skipWS();
            if (pos < src.length() && src.charAt(pos) == ',') {
                pos++;
                continue;
            }
            if (pos < src.length() && src.charAt(pos) == ']') {
                pos++;
                break;
            }
            throw error("expected ',' or ']' in array at " + pos);
        }
        return new JsonNode.JsonArray(list);
    }

    // ========== string ==========

    private JsonNode readString() {
        pos++; // skip opening '"'
        var sb = new StringBuilder();
        while (pos < src.length()) {
            char c = src.charAt(pos);
            if (c == '"') {
                pos++;
                return new JsonNode.JsonText(sb.toString());
            }
            if (c == '\\') {
                pos++;
                if (pos >= src.length()) throw error("unexpected end in string escape");
                char e = src.charAt(pos);
                switch (e) {
                    case '"', '\\', '/' -> sb.append(e);
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'u' -> sb.append(readUnicodeEscape());
                    default -> sb.append(e);
                }
            } else {
                sb.append(c);
            }
            pos++;
        }
        throw error("unterminated string");
    }

    private char readUnicodeEscape() {
        int val = 0;
        for (int i = 0; i < 4; i++) {
            pos++;
            if (pos >= src.length()) throw error("unexpected end in unicode escape");
            char c = src.charAt(pos);
            val = (val << 4) + hexVal(c);
        }
        return (char) val;
    }

    // ========== number ==========

    private JsonNode readNumber() {
        int start = pos;
        if (src.charAt(pos) == '-') pos++;
        while (pos < src.length() && isDigit(src.charAt(pos))) pos++;
        try {
            return new JsonNode.JsonNum(Long.parseLong(src.substring(start, pos)));
        } catch (NumberFormatException e) {
            throw error("invalid number at " + start);
        }
    }

    // ========== bool / null ==========

    private JsonNode readBool() {
        if (src.startsWith("true", pos)) {
            pos += 4;
            return new JsonNode.JsonBool(true);
        }
        if (src.startsWith("false", pos)) {
            pos += 5;
            return new JsonNode.JsonBool(false);
        }
        throw error("expected boolean at " + pos);
    }

    private JsonNode readNull() {
        if (src.startsWith("null", pos)) {
            pos += 4;
            return JsonNode.JsonNull.INSTANCE;
        }
        throw error("expected null at " + pos);
    }

    // ========== helpers ==========

    private void skipWS() {
        while (pos < src.length()) {
            char c = src.charAt(pos);
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                pos++;
            } else {
                break;
            }
        }
    }

    private void expect(char c) {
        if (pos >= src.length() || src.charAt(pos) != c) {
            throw error("expected '%c' at %d".formatted(c, pos));
        }
        pos++;
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private static int hexVal(char c) {
        if (c >= '0' && c <= '9') return c - '0';
        if (c >= 'a' && c <= 'f') return c - 'a' + 10;
        if (c >= 'A' && c <= 'F') return c - 'A' + 10;
        throw new IllegalArgumentException("invalid hex char: " + c);
    }

    private static RuntimeException error(String msg) {
        throw new RuntimeException("JSON parse error: " + msg);
    }
}
