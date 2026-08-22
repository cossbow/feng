package org.cossbow.feng.mcp;

import com.google.gson.*;
import org.cossbow.feng.analysis.AnalyseSymbolTable;
import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.Source;
import org.cossbow.feng.ast.dcl.Variable;
import org.cossbow.feng.ast.proc.FunctionDefinition;
import org.cossbow.feng.lsp.AnalyzeResult;
import org.cossbow.feng.lsp.FengAnalyzer;
import org.cossbow.feng.lsp.FengDocumentStore;

import java.util.*;

/**
 * MCP tool registry and execution engine.
 * Reuses the existing {@link FengAnalyzer} and {@link AnalyseSymbolTable}.
 */
public class FengMcpTools {

    private static final Gson GSON = new GsonBuilder().serializeNulls().create();

    private final FengDocumentStore documents = new FengDocumentStore();
    private final FengAnalyzer analyzer = new FengAnalyzer(documents);

    // ---- Tool registry ----

    record ToolDef(String name, String description, JsonObject inputSchema) {}

    public List<ToolDef> listTools() {
        return List.of(
                new ToolDef("feng_parse",
                        "Parse a Fēng source file and return syntax/semantic errors",
                        schema(properties(
                                required("uri", "Source file URI"),
                                required("source", "Fēng source code text")
                        ))),
                new ToolDef("feng_symbols",
                        "List all symbols (types, functions, variables) in a source file",
                        schema(properties(
                                required("uri", "Source file URI"),
                                required("source", "Fēng source code text")
                        ))),
                new ToolDef("feng_hover",
                        "Get type/signature info for the symbol at a given position",
                        schema(properties(
                                required("uri", "Source file URI"),
                                required("source", "Fēng source code text"),
                                required("line", "0-based line number"),
                                required("character", "0-based character offset")
                        ))),
                new ToolDef("feng_definition",
                        "Get the definition location of the symbol at a given position",
                        schema(properties(
                                required("uri", "Source file URI"),
                                required("source", "Fēng source code text"),
                                required("line", "0-based line number"),
                                required("character", "0-based character offset")
                        ))),
                new ToolDef("feng_complete",
                        "Get code completion suggestions at a given position",
                        schema(properties(
                                required("uri", "Source file URI"),
                                required("source", "Fēng source code text"),
                                required("line", "0-based line number"),
                                required("character", "0-based character offset")
                        ))),
                new ToolDef("feng_keywords",
                        "Return Fēng language keywords and built-in type reference",
                        schema(properties()))
        );
    }

    public Object callTool(String name, JsonObject args) {
        return switch (name) {
            case "feng_parse" -> fengParse(args);
            case "feng_symbols" -> fengSymbols(args);
            case "feng_hover" -> fengHover(args);
            case "feng_definition" -> fengDefinition(args);
            case "feng_complete" -> fengComplete(args);
            case "feng_keywords" -> fengKeywords();
            default -> error("Unknown tool: " + name);
        };
    }

    // ---- Tool implementations ----

    private Object fengParse(JsonObject args) {
        var uri = args.get("uri").getAsString();
        var source = args.get("source").getAsString();
        ensureDoc(uri, source);

        var result = analyzer.analyze(uri, null);
        var errors = new ArrayList<Map<String, Object>>();
        for (var e : result.syntaxErrors()) {
            errors.add(Map.of("line", e.line(), "column", e.charPositionInLine(), "message", e.message()));
        }
        for (var e : result.semanticErrors()) {
            errors.add(Map.of("message", e));
        }
        return textResult(errors.isEmpty()
                ? "No errors found. Parse successful."
                : "Found " + errors.size() + " issue(s):\n" + formatErrors(errors));
    }

    private Object fengSymbols(JsonObject args) {
        var uri = args.get("uri").getAsString();
        var source = args.get("source").getAsString();
        var result = ensureAnalyzed(uri, source);

        if (result == null || result.source() == null)
            return textResult("No symbols found (parse failed).");

        var table = result.source().table();
        var sb = new StringBuilder();

        appendSection(sb, "Types", table.types.values().stream()
                .map(t -> t.symbol().name().value() + " → " + t.symbol()).toList());
        appendSection(sb, "Functions", table.functions.values().stream()
                .map(FengMcpTools::formatFunc).toList());
        appendSection(sb, "Global Variables", table.variables.values().stream()
                .map(v -> v.symbol().name().value() + ": " + varType(v)).toList());

        return textResult(sb.isEmpty() ? "No top-level symbols found." : sb.toString().trim());
    }

    private Object fengHover(JsonObject args) {
        var uri = args.get("uri").getAsString();
        var source = args.get("source").getAsString();
        int line = args.get("line").getAsInt();
        int character = args.get("character").getAsInt();
        ensureDoc(uri, source);

        var hover = analyzer.hover(uri, new org.eclipse.lsp4j.Position(line, character));
        if (hover == null)
            return textResult("No symbol found at position (" + line + ", " + character + ").");

        var contents = hover.getContents();
        String text = contents.isLeft() ? contents.getLeft().stream()
                .map(se -> {
                    if (se.isLeft()) return se.getLeft();
                    else return se.getRight().getValue();
                })
                .reduce("", (a, b) -> a + b) :
                contents.getRight().getValue();

        return textResult(text);
    }

    private Object fengDefinition(JsonObject args) {
        var uri = args.get("uri").getAsString();
        var source = args.get("source").getAsString();
        int line = args.get("line").getAsInt();
        int character = args.get("character").getAsInt();
        ensureDoc(uri, source);

        var locations = analyzer.definition(uri,
                new org.eclipse.lsp4j.Position(line, character));
        if (locations.isEmpty())
            return textResult("No definition found for symbol at (" + line + ", " + character + ").");

        var sb = new StringBuilder();
        for (var loc : locations) {
            sb.append("Defined at ").append(loc.getUri())
                    .append(" line ").append(loc.getRange().getStart().getLine() + 1)
                    .append(", col ").append(loc.getRange().getStart().getCharacter() + 1)
                    .append("\n");
        }
        return textResult(sb.toString().trim());
    }

    private Object fengComplete(JsonObject args) {
        var uri = args.get("uri").getAsString();
        var source = args.get("source").getAsString();
        int line = args.get("line").getAsInt();
        int character = args.get("character").getAsInt();
        ensureDoc(uri, source);

        var list = analyzer.completion(uri,
                new org.eclipse.lsp4j.Position(line, character));
        var sb = new StringBuilder();
        for (var item : list.getItems()) {
            sb.append(item.getLabel());
            if (item.getDetail() != null) sb.append(" — ").append(item.getDetail());
            sb.append("\n");
        }
        return textResult(sb.isEmpty() ? "No completions available." : sb.toString().trim());
    }

    private Object fengKeywords() {
        var sb = new StringBuilder();
        sb.append("=== Fēng Keywords ===\n\n");

        sb.append("# Module: import, export\n");
        sb.append("# Declaration: var, const\n");
        sb.append("# Types: struct, union, enum, class, interface, attribute\n");
        sb.append("# Function: func, return, macro\n");
        sb.append("# Control: if, else, for, switch, case, default, break, continue\n");
        sb.append("# Exception: throw, try, catch, final, assert\n");
        sb.append("# OOP: this, super\n");
        sb.append("# Memory: new, sizeof, nil\n");
        sb.append("# Boolean: true, false\n\n");

        sb.append("=== Built-in Types ===\n");
        sb.append("int8, int16, int32, int64, int, uint8, uint16, uint32, uint64, uint\n");
        sb.append("float32, float64, bool, void\n");

        return textResult(sb.toString().trim());
    }

    // ---- Helpers ----

    private void ensureDoc(String uri, String text) {
        var existing = documents.get(uri);
        if (existing == null || !existing.text().equals(text)) {
            documents.open(uri, text, 0);
        }
    }

    private AnalyzeResult ensureAnalyzed(String uri, String source) {
        ensureDoc(uri, source);
        return analyzer.analyze(uri, null);
    }

    private static String formatFunc(FunctionDefinition f) {
        var params = f.prototype().parameterSet().params();
        var paramsStr = params.stream()
                .map(p -> {
                    if (p instanceof org.cossbow.feng.ast.proc.FixedParameter fp) {
                        return fp.name().get().value() + ": " + fp.type();
                    }
                    return p.toString();
                })
                .reduce((a, b) -> a + ", " + b).orElse("");
        return f.symbol().name().value() + "(" + paramsStr + ")";
    }

    private static String varType(Variable v) {
        return v.type() == null ? "?" : v.type().toString();
    }

    private static String formatErrors(List<Map<String, Object>> errors) {
        var sb = new StringBuilder();
        for (var e : errors) {
            if (e.containsKey("line")) {
                sb.append("  Line ").append(e.get("line"))
                        .append(":").append(e.get("column"))
                        .append(" — ").append(e.get("message")).append("\n");
            } else {
                sb.append("  ").append(e.get("message")).append("\n");
            }
        }
        return sb.toString().trim();
    }

    private static void appendSection(StringBuilder sb, String title, List<String> items) {
        if (items.isEmpty()) return;
        sb.append("## ").append(title).append("\n");
        for (var item : items) {
            sb.append("- ").append(item).append("\n");
        }
        sb.append("\n");
    }

    private static Map<String, Object> textResult(String text) {
        return Map.of("content", List.of(
                Map.of("type", "text", "text", text)));
    }

    private static Map<String, Object> error(String message) {
        return Map.of("content", List.of(
                Map.of("type", "text", "text", "Error: " + message)),
                "isError", true);
    }

    // ---- JSON schema helpers ----

    @SafeVarargs
    @SuppressWarnings("unchecked")
    private static JsonObject properties(Map.Entry<String, String>... props) {
        var p = new JsonObject();
        for (var e : props) {
            var def = new JsonObject();
            def.addProperty("description", e.getValue());
            def.addProperty("type", "string");
            p.add(e.getKey(), def);
        }
        // override line/character to number
        if (p.has("line")) {
            p.getAsJsonObject("line").addProperty("type", "number");
        }
        if (p.has("character")) {
            p.getAsJsonObject("character").addProperty("type", "number");
        }
        return p;
    }

    private static JsonObject schema(JsonObject properties) {
        var s = new JsonObject();
        s.addProperty("type", "object");
        s.add("properties", properties);

        var required = new JsonArray();
        for (var key : properties.keySet()) {
            required.add(key);
        }
        if (!required.isEmpty()) {
            s.add("required", required);
        }
        return s;
    }

    private static Map.Entry<String, String> required(String name, String desc) {
        return Map.entry(name, desc);
    }
}
