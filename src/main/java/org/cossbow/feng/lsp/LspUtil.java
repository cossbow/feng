package org.cossbow.feng.lsp;

import org.cossbow.feng.ast.TypeDefinition;
import org.cossbow.feng.ast.dcl.Declare;
import org.cossbow.feng.ast.dcl.Variable;
import org.cossbow.feng.ast.proc.FixedParameter;
import org.cossbow.feng.ast.proc.FunctionDefinition;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolKind;

final class LspUtil {
    private LspUtil() {}

    // ---- Position / Range conversion ----

    /**
     * Convert Fēng Position (ANTLR4 Token-based, 1-based lines) to LSP Range (0-based).
     */
    static Range toRange(org.cossbow.feng.ast.Position pos) {
        if (pos == null || pos == org.cossbow.feng.ast.Position.ZERO || pos.start() == null) {
            return new Range(new org.eclipse.lsp4j.Position(0, 0),
                    new org.eclipse.lsp4j.Position(0, 0));
        }
        var start = new org.eclipse.lsp4j.Position(pos.start().getLine() - 1,
                pos.start().getCharPositionInLine());
        var stop = pos.stop();
        var end = (stop != null)
                ? new org.eclipse.lsp4j.Position(stop.getLine() - 1,
                stop.getCharPositionInLine() + Math.max(stop.getText().length(), 1))
                : start;
        return new Range(start, end);
    }

    static Range toRange(int line, int charPos, int length) {
        return new Range(new org.eclipse.lsp4j.Position(line - 1, charPos),
                new org.eclipse.lsp4j.Position(line - 1, charPos + length));
    }

    static String extractFileName(String uri) {
        int idx = uri.lastIndexOf('/');
        return idx >= 0 ? uri.substring(idx + 1) : uri;
    }

    // ---- Identifier extraction from text ----

    static String identifierAt(String text, org.eclipse.lsp4j.Position position) {
        var lines = text.split("\n", -1);
        if (position.getLine() >= lines.length) return null;
        var line = lines[position.getLine()];
        var col = position.getCharacter();
        if (col >= line.length()) return null;

        int start = col;
        while (start > 0 && isIdentifierChar(line.charAt(start - 1))) start--;
        int end = col;
        while (end < line.length() && isIdentifierChar(line.charAt(end))) end++;

        if (start == end) return null;
        return line.substring(start, end);
    }

    private static boolean isIdentifierChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    // ---- DocumentSymbol conversion ----

    static DocumentSymbol toDocumentSymbol(TypeDefinition def) {
        var kind = switch (def.domain()) {
            case CLASS -> SymbolKind.Class;
            case INTERFACE -> SymbolKind.Interface;
            case ENUM -> SymbolKind.Enum;
            case STRUCT, UNION -> SymbolKind.Struct;
            case ATTRIBUTE -> SymbolKind.Property;
            case FUNC -> SymbolKind.Function;
            case PRIMITIVE -> SymbolKind.TypeParameter;
        };
        return new DocumentSymbol(
                def.symbol().name().value(),
                kind,
                toRange(def.pos()),
                toRange(def.pos()));
    }

    static DocumentSymbol toDocumentSymbol(FunctionDefinition def) {
        return new DocumentSymbol(
                def.symbol().name().value(),
                SymbolKind.Function,
                toRange(def.pos()),
                toRange(def.pos()));
    }

    static DocumentSymbol toDocumentSymbol(Variable v) {
        return new DocumentSymbol(
                v.name().value(),
                v.isConst() ? SymbolKind.Constant : SymbolKind.Variable,
                toRange(v.pos()),
                toRange(v.pos()));
    }

    // ---- Signature formatting for hover ----

    static String formatTypeDefinition(TypeDefinition def) {
        return def.domain().name().toLowerCase() + " " +
                def.symbol().name().value() +
                (def.generic().isEmpty() ? "" : def.generic().toString());
    }

    static String formatFunctionSignature(FunctionDefinition def) {
        var sb = new StringBuilder("func ");
        sb.append(def.symbol().name().value());
        if (!def.generic().isEmpty()) sb.append(def.generic());
        sb.append(formatPrototype(def.prototype()));
        return sb.toString();
    }

    static String formatVariableSignature(Variable v) {
        var sb = new StringBuilder();
        sb.append(v.declare() == Declare.CONST ? "const " : "var ");
        sb.append(v.name().value());
        var type = v.type();
        if (type != null && type.has()) {
            sb.append(' ').append(type.get().get());
        }
        return sb.toString();
    }

    private static String formatPrototype(org.cossbow.feng.ast.proc.Prototype pt) {
        var sb = new StringBuilder("(");
        var params = pt.parameterSet();
        if (params != null && !params.isEmpty()) {
            boolean first = true;
            for (var p : params) {
                if (!first) sb.append(", ");
                first = false;
                if (p instanceof FixedParameter fp) {
                    sb.append(fp.name().get().value()).append(' ').append(fp.type());
                }
            }
        }
        sb.append(')');
        var ret = pt.returnSet();
        if (ret != null && ret.has()) {
            sb.append(' ').append(ret.get());
        }
        return sb.toString();
    }
}
