package org.cossbow.feng.mcp;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * MCP JSON-RPC server using stdio transport.
 * <p>
 * Implements the minimal MCP protocol surface:
 * <ul>
 *   <li>{@code initialize} — capability handshake</li>
 *   <li>{@code tools/list} — list available tools</li>
 *   <li>{@code tools/call} — execute a tool</li>
 *   <li>{@code notifications/initialized} — client ready</li>
 * </ul>
 */
public class FengMcpServer {

    private static final Gson GSON = new GsonBuilder().serializeNulls().create();
    private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>() {}.getType();

    private final FengMcpTools tools = new FengMcpTools();
    private volatile boolean running = true;

    public void start() throws IOException {
        var in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        var out = new PrintWriter(System.out, true, StandardCharsets.UTF_8);

        while (running) {
            var line = in.readLine();
            if (line == null) break; // EOF, shutdown

            var response = dispatch(line);
            if (response != null) {
                out.println(GSON.toJson(response));
                out.flush();
            }
        }
    }

    public void stop() {
        running = false;
    }

    // ---- Message dispatch ----

    @SuppressWarnings("unchecked")
    private Map<String, Object> dispatch(String rawLine) {
        JsonElement idElement;
        Map<String, Object> msg;
        try {
            var root = JsonParser.parseString(rawLine);
            if (!root.isJsonObject()) {
                return error(null, -32700, "Parse error: not a JSON object");
            }
            var obj = root.getAsJsonObject();
            idElement = obj.has("id") ? obj.get("id") : null;
            msg = GSON.fromJson(root, MAP_TYPE);
        } catch (JsonSyntaxException e) {
            return error(null, -32700, "Parse error: " + e.getMessage());
        }

        var method = (String) msg.get("method");
        // idElement preserves the original JSON type (int, string, etc.)
        var id = msg.get("id"); // can be String or Number, may be null for notifications

        // Notifications (no id) — handle and return null
        if (id == null && method != null) {
            handleNotification(method, (Map<String, Object>) msg.getOrDefault("params",
                    Collections.emptyMap()));
            return null;
        }

        // Request (has id)
        if (id != null && method != null) {
            try {
                var params = (Map<String, Object>) msg.getOrDefault("params",
                        Collections.emptyMap());
                var result = handleRequest(method, params);
                return response(idElement, result);
            } catch (Exception e) {
                return error(idElement, -32603, "Internal error: " + e.getMessage());
            }
        }

        // Unknown message shape
        return error(idElement, -32600, "Invalid request");
    }

    private void handleNotification(String method, Map<String, Object> params) {
        switch (method) {
            case "notifications/initialized":
                // Client is ready — nothing to do
                break;
            case "notifications/cancelled":
                // Best-effort cancellation — we process synchronously anyway
                break;
            default:
                // Unknown notification — silently ignore
        }
    }

    @SuppressWarnings("unchecked")
    private Object handleRequest(String method, Map<String, Object> params) {
        return switch (method) {
            case "initialize" -> initialize(params);
            case "tools/list" -> toolsList();
            case "tools/call" -> {
                var name = (String) params.get("name");
                var args = params.get("arguments");
                var jsonArgs = args instanceof Map<?, ?> m
                        ? GSON.toJsonTree(m).getAsJsonObject()
                        : new JsonObject();
                yield tools.callTool(name, jsonArgs);
            }
            default -> throw new IllegalArgumentException("Unknown method: " + method);
        };
    }

    // ---- MCP method handlers ----

    private Map<String, Object> initialize(Map<String, Object> params) {
        var capabilities = Map.of("tools", Map.of());
        var serverInfo = Map.of(
                "name", "feng-mcp-server",
                "version", "0.1.0"
        );
        return Map.of(
                "protocolVersion", "2024-11-05",
                "capabilities", capabilities,
                "serverInfo", serverInfo
        );
    }

    private Map<String, Object> toolsList() {
        var toolDefs = tools.listTools();
        var toolsJson = new ArrayList<Map<String, Object>>();
        for (var td : toolDefs) {
            // Parse schema JSON to Map for the response
            var schema = GSON.fromJson(td.inputSchema(), Map.class);
            toolsJson.add(Map.of(
                    "name", td.name(),
                    "description", td.description(),
                    "inputSchema", schema
            ));
        }
        return Map.of("tools", toolsJson);
    }

    // ---- JSON-RPC response helpers ----

    private static Map<String, Object> response(JsonElement id, Object result) {
        var r = new LinkedHashMap<String, Object>();
        r.put("jsonrpc", "2.0");
        r.put("id", normalizeId(id));
        r.put("result", result);
        return r;
    }

    private static Map<String, Object> error(JsonElement id, int code, String message) {
        var r = new LinkedHashMap<String, Object>();
        r.put("jsonrpc", "2.0");
        r.put("id", normalizeId(id));
        r.put("error", Map.of("code", code, "message", message));
        return r;
    }

    /**
     * Convert a JsonElement id back to a Java object that Gson will serialize
     * with the correct JSON type (int stays int, string stays string).
     */
    private static Object normalizeId(JsonElement idElement) {
        if (idElement == null) return null;
        if (idElement.isJsonPrimitive()) {
            var prim = idElement.getAsJsonPrimitive();
            if (prim.isNumber()) {
                var num = prim.getAsNumber();
                // If it fits in a long without loss, use long (no decimal point)
                if (num.doubleValue() == num.longValue()) {
                    return num.longValue();
                }
                return num.doubleValue();
            }
            if (prim.isString()) return prim.getAsString();
            if (prim.isBoolean()) return prim.getAsBoolean();
        }
        return idElement; // fallback: raw JsonElement (Gson handles it)
    }
}
