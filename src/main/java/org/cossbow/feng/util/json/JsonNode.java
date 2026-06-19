package org.cossbow.feng.util.json;

import java.util.*;

/**
 * A lightweight JSON node for reading clang AST dumps.
 * <p>
 * Only read-only navigation — no serialization support.
 */
public sealed interface JsonNode {

    // ---- type checks ----

    default boolean isObject() { return false; }

    default boolean isArray() { return false; }

    default boolean isText() { return false; }

    default boolean isNumber() { return false; }

    default boolean isBool() { return false; }

    default boolean isNull() { return false; }

    // ---- value extraction ----

    default String asText() { throw new UnsupportedOperationException(); }

    default long asInt() { throw new UnsupportedOperationException(); }

    default boolean asBool() { throw new UnsupportedOperationException(); }

    // ---- object navigation ----

    default JsonNode get(String key) { throw new UnsupportedOperationException(); }

    default Set<String> keys() { throw new UnsupportedOperationException(); }

    // ---- array navigation ----

    default List<JsonNode> elements() { throw new UnsupportedOperationException(); }

    default int size() { throw new UnsupportedOperationException(); }

    default JsonNode get(int index) { throw new UnsupportedOperationException(); }

    // ========== implementations ==========

    record JsonObject(Map<String, JsonNode> map) implements JsonNode {
        @Override
        public boolean isObject() { return true; }

        @Override
        public JsonNode get(String key) { return map.get(key); }

        @Override
        public Set<String> keys() { return map.keySet(); }

        @Override
        public String toString() { return map.toString(); }
    }

    record JsonArray(List<JsonNode> list) implements JsonNode {
        @Override
        public boolean isArray() { return true; }

        @Override
        public List<JsonNode> elements() { return list; }

        @Override
        public int size() { return list.size(); }

        @Override
        public JsonNode get(int index) { return list.get(index); }

        @Override
        public String toString() { return list.toString(); }
    }

    record JsonText(String value) implements JsonNode {
        @Override
        public boolean isText() { return true; }

        @Override
        public String asText() { return value; }

        @Override
        public String toString() { return '"' + value + '"'; }
    }

    record JsonNum(long value) implements JsonNode {
        @Override
        public boolean isNumber() { return true; }

        @Override
        public long asInt() { return value; }

        @Override
        public String toString() { return Long.toString(value); }

        public int asInt32() { return (int) value; }
    }

    record JsonBool(boolean value) implements JsonNode {
        @Override
        public boolean isBool() { return true; }

        @Override
        public boolean asBool() { return value; }

        @Override
        public String toString() { return Boolean.toString(value); }
    }

    final class JsonNull implements JsonNode {
        public static final JsonNull INSTANCE = new JsonNull();

        private JsonNull() {}

        @Override
        public boolean isNull() { return true; }

        @Override
        public String toString() { return "null"; }
    }
}
