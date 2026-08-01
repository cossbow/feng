package org.cossbow.feng.c2feng.parse;

import org.cossbow.feng.c2feng.convert.C2FengConverter;
import org.cossbow.feng.c2feng.model.*;
import org.cossbow.feng.util.Optional;
import org.cossbow.feng.util.json.JsonNode;
import org.cossbow.feng.util.json.JsonParser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Parses clang's {@code -ast-dump=json} output and populates
 * a {@link C2FengConverter} with the extracted declarations.
 */
public class JsonAstParser {

    private final C2FengConverter converter;
    private final List<CTypedef> pendingTypedefs = new ArrayList<>();
    private final Set<String> processed = new HashSet<>();
    /**
     * Maps RecordDecl id → tagName for anonymous structs owned by typedefs.
     */
    private final Map<String, String> emptyRecordTags = new HashMap<>();

    public JsonAstParser(C2FengConverter converter) {
        this.converter = converter;
    }

    /**
     * Parse a clang JSON AST string and convert all declarations.
     */
    public void parse(String json) {
        var root = JsonParser.parse(json);
        if (!root.isObject()) {
            throw new RuntimeException("expected root object");
        }
        var inner = root.get("inner");
        if (inner == null || inner.isNull()) return;

        // First pass: collect typedefs so they're available for type expansion
        for (var child : inner.elements()) {
            if (child.isObject() && "TypedefDecl".equals(
                    child.get("kind").asText())) {
                processTypedef(child);
            }
        }
        for (var td : pendingTypedefs) converter.addTypedef(td);
        pendingTypedefs.clear();

        // Second pass: process all definitions
        for (var child : inner.elements()) {
            if (child.isObject()) processDecl(child);
        }
    }

    // ========== declaration dispatch ==========

    private void processDecl(JsonNode node) {
        var kind = node.get("kind");
        if (kind == null || kind.isNull()) return;

        var kindStr = kind.asText();
        var name = node.get("name");

        // RecordDecl may have no "name" field at all (anonymous struct).
        // Other declaration kinds require a name.
        if (!"RecordDecl".equals(kindStr)) {
            if (name == null || name.isNull()) return;
        }

        // Skip implicit declarations before dedup check,
        // otherwise they'd block the real declaration.
        if (isImplicit(node)) return;

        // Use name for dedup when available; fall back to id for anonymous
        // declarations (e.g. empty-named structs) which would otherwise
        // collide on e.g. "RecordDecl:" and skip all but the first.
        var nameStr = name != null ? name.asText() : "";
        var idNode = node.get("id");
        var key = kindStr + ":"
                + (nameStr.isEmpty() && idNode != null
                ? idNode.asText()
                : nameStr);
        if (!processed.add(key)) return; // skip duplicates

        switch (kindStr) {
            case "RecordDecl" -> processRecord(node);
            case "EnumDecl" -> processEnum(node);
            case "FunctionDecl" -> processFunction(node);
            case "VarDecl" -> processVarDecl(node);
            case "TypedefDecl" -> {
                // Recurse into nested RecordDecl/EnumDecl inside typedefs,
                // e.g. `typedef struct { ... } name;` or `typedef enum { ... } name;`
                var inner = node.get("inner");
                if (inner != null && inner.isArray()) {
                    for (var child : inner.elements()) {
                        if (child.isObject()) processDecl(child);
                    }
                }
            }
        }
    }

    // ========== struct / union ==========

    private void processRecord(JsonNode node) {
        var tagUsed = node.get("tagUsed");
        if (tagUsed == null || tagUsed.isNull()) return;
        var isStruct = "struct".equals(tagUsed.asText());
        var isUnion = "union".equals(tagUsed.asText());
        if (!isStruct && !isUnion) return;

        if (!isCompleteDefinition(node)) return;

        var nameNode = node.get("name");
        var name = nameNode != null ? nameNode.asText() : null;
        boolean anonymous;
        if (name != null && !name.isEmpty()) {
            // Named struct/union — not anonymous
            anonymous = false;
        } else {
            // Anonymous struct/union: try typedef mapping first (e.g.
            // `typedef struct { ... } lldiv_t`), then fall back to
            // location-based name for nested anonymous structs.
            var id = node.get("id");
            if (id != null && !id.isNull()) {
                name = emptyRecordTags.get(id.asText());
            }
            if (name == null || name.isEmpty()) {
                var tag = isStruct ? "struct" : "union";
                name = locToAnonName(node, tag);
            }
            if (name == null || name.isEmpty()) return;
            anonymous = true;
        }

        var fields = new ArrayList<CField>();
        var inner = node.get("inner");
        if (inner != null && inner.isArray()) {
            // First pass: process nested anonymous RecordDecl children
            // so their definitions exist before fields reference them
            for (var child : inner.elements()) {
                if (!child.isObject()) continue;
                var ckind = child.get("kind");
                if (ckind == null || ckind.isNull()) continue;
                if ("RecordDecl".equals(ckind.asText())) {
                    processRecord(child);
                }
            }
            // Second pass: process FieldDecl children
            for (var child : inner.elements()) {
                if (child.isObject() && "FieldDecl".equals(
                        child.get("kind").asText())) {
                    processField(child, fields);
                }
            }
        }

        if (isStruct) {
            converter.addStruct(new CStructType(name, fields, true, anonymous));
        } else {
            converter.addUnion(new CUnionType(name, fields, true, anonymous));
        }
    }

    private void processField(JsonNode node, List<CField> fields) {
        var name = node.get("name");
        if (name == null || name.isNull()) return;

        var qualType = resolveQualType(node);
        var bitWidth = node.get("bitfieldWidth");
        var ctype = qualTypeToCType(qualType);

        if (bitWidth != null && bitWidth.isNumber()) {
            fields.add(new CField(name.asText(), ctype,
                    Optional.of((int) bitWidth.asInt())));
        } else {
            fields.add(new CField(name.asText(), ctype));
        }
    }

    // ========== enum → const int ==========

    private void processEnum(JsonNode node) {
        if (!isCompleteDefinition(node)) return;

        var name = node.get("name");
        if (name == null || name.isNull()) return;
        var tagName = name.asText();

        var constants = new ArrayList<CEnumConstant>();
        var inner = node.get("inner");
        if (inner != null && inner.isArray()) {
            for (var child : inner.elements()) {
                if (child.isObject() && "EnumConstantDecl".equals(
                        child.get("kind").asText())) {
                    var cName = child.get("name").asText();
                    var valNode = child.get("value");
                    Optional<Long> optValue;
                    if (valNode != null && !valNode.isNull()) {
                        if (valNode.isNumber()) {
                            optValue = Optional.of(valNode.asInt());
                        } else if (valNode.isText()) {
                            optValue = Optional.of(
                                    Long.parseLong(valNode.asText()));
                        } else {
                            optValue = Optional.empty();
                        }
                    } else {
                        optValue = Optional.empty();
                    }
                    constants.add(new CEnumConstant(cName, optValue));
                }
            }
        }

        converter.addEnum(new CEnumType(tagName, constants));
    }

    // ========== function ==========

    private void processFunction(JsonNode node) {
        var name = node.get("name").asText();
        if (name == null || name.isEmpty()) return;
        if (isImplicit(node)) return;

        // Determine linkage: check storage class
        var linkage = CLinkage.DEFAULT;
        var storage = node.get("storageClass");
        if (storage != null && "static".equals(storage.asText())) {
            linkage = CLinkage.STATIC;
        }

        var qualType = resolveQualType(node);
        var funcType = parseFunctionQualType(qualType);

        var params = new ArrayList<CField>();
        var inner = node.get("inner");
        if (inner != null && inner.isArray()) {
            for (var child : inner.elements()) {
                if (child.isObject() && "ParmVarDecl".equals(
                        child.get("kind").asText())) {
                    var pName = child.get("name");
                    var pQualType = resolveQualType(child);
                    params.add(new CField(
                            pName != null ? pName.asText() : "",
                            qualTypeToCType(pQualType)));
                }
            }
        }

        converter.addFunction(new CFunction(
                name, params,
                funcType.returnType(),
                funcType.variadic(),
                linkage));
    }

    // ========== global variable ==========

    private void processVarDecl(JsonNode node) {
        var name = node.get("name");
        if (name == null || name.isNull()) return;
        if (isImplicit(node)) return;

        // Only emit file-scope variables, not locals
        if (!isFileScope(node)) return;

        var qualType = resolveQualType(node);
        var ctype = qualTypeToCType(qualType);

        var linkage = CLinkage.DEFAULT;
        var storage = node.get("storageClass");
        if (storage != null) {
            var s = storage.asText();
            if ("static".equals(s)) linkage = CLinkage.STATIC;
            if ("extern".equals(s)) linkage = CLinkage.EXTERN;
        }

        converter.addGlobalVar(new CGlobalVar(
                name.asText(), ctype, false, linkage));
    }

    // ========== typedef ==========

    private void processAnonymous(JsonNode node, CType underlying) {
        // If this typedef wraps an anonymous struct/union, record its
        // RecordDecl id so processRecord can recover the tag name later.
        if (!(underlying instanceof CStructType st)
                && !(underlying instanceof CUnionType ut))
            return;

        var inner = node.get("inner");
        if (inner == null || !inner.isArray())
            return;

        for (var child : inner.elements()) {
            if (child.isObject()
                    && "ElaboratedType".equals(child.get("kind").asText())) {
                var owned = child.get("ownedTagDecl");
                if (owned == null || !owned.isObject()) {
                    continue;
                }
                var recordId = owned.get("id");
                if (recordId != null && !recordId.isNull()) {
                    var tagName = switch (underlying) {
                        case CStructType s -> s.tagName();
                        case CUnionType u -> u.tagName();
                        default -> null;
                    };
                    if (tagName != null && !tagName.isEmpty()) {
                        emptyRecordTags.put(recordId.asText(), tagName);
                    }
                }
            }
        }
    }

    private void processTypedef(JsonNode node) {
        var name = node.get("name");
        if (name == null || name.isNull()) return;
        if (isImplicit(node)) return;

        var qualType = resolveQualType(node);
        if (qualType == null || qualType.isEmpty()) return;

        // Expand the underlying type from qualType
        var underlying = qualTypeToCType(qualType);
        pendingTypedefs.add(new CTypedef(name.asText(), underlying));

        processAnonymous(node, underlying);
    }

    // ========== qualType → CType ==========

    private CType qualTypeToCType(String qualType) {
        if (qualType == null || qualType.isEmpty()) {
            return new CPrimitiveType("int");
        }

        // Strip outer const/volatile/restrict
        var type = stripQualifiers(qualType).trim();

        // Pointer: ends with *
        if (type.endsWith("*")) {
            var baseType = type.substring(0, type.length() - 1).trim();
            boolean isConst = baseType.endsWith("const");
            if (isConst) baseType = baseType.substring(0, baseType.length() - 5).trim();
            return new CPointerType(qualTypeToCType(baseType), isConst);
        }

        // Array: contains [N]
        if (type.contains("[")) {
            var bracket = type.indexOf('[');
            var elemType = type.substring(0, bracket).trim();
            var rest = type.substring(bracket + 1);
            var endBracket = rest.indexOf(']');
            if (endBracket > 0) {
                var lenStr = rest.substring(0, endBracket).trim();
                try {
                    var len = Integer.parseInt(lenStr);
                    return new CArrayType(qualTypeToCType(elemType),
                            Optional.of(len));
                } catch (NumberFormatException e) {
                    // Variable-length or unknown array → pointer decay
                    return new CPointerType(qualTypeToCType(elemType), false);
                }
            }
            // No closing bracket → treat as pointer
            return new CPointerType(qualTypeToCType(elemType), false);
        }

        // Struct/union/enum reference
        if (type.startsWith("struct ") || type.startsWith("union ") || type.startsWith("enum ")) {
            var space = type.indexOf(' ');
            var tagName = type.substring(space + 1).trim();
            // Normalize anonymous struct/union names so they match nested definitions
            var anonName = normalizeAnonTypeName(tagName);
            var anonymous = !anonName.equals(tagName);
            // Strip possible "struct S &" -> we don't care about the address
            // for the metadata, we just need the tag name
            return switch (type.substring(0, space)) {
                case "struct" -> new CStructType(anonName, List.of(), true, anonymous);
                case "union" -> new CUnionType(anonName, List.of(), true, anonymous);
                case "enum" -> new CEnumType(tagName, List.of());
                default -> new CPrimitiveType(tagName);
            };
        }

        // Primitive types
        return parsePrimitiveType(type);
    }

    private CType parsePrimitiveType(String name) {
        return switch (name) {
            case "void" -> new CPrimitiveType("void");
            case "char", "signed char" -> new CPrimitiveType("char");
            case "unsigned char" -> new CPrimitiveType("unsigned char");
            case "short", "signed short", "short int", "signed short int" -> new CPrimitiveType("short");
            case "unsigned short", "unsigned short int" -> new CPrimitiveType("unsigned short");
            case "int", "signed", "signed int" -> new CPrimitiveType("int");
            case "unsigned", "unsigned int" -> new CPrimitiveType("unsigned int");
            case "long", "signed long", "long int", "signed long int" -> new CPrimitiveType("long");
            case "unsigned long", "unsigned long int" -> new CPrimitiveType("unsigned long");
            case "long long", "signed long long", "long long int",
                 "signed long long int" -> new CPrimitiveType("long long");
            case "unsigned long long", "unsigned long long int" -> new CPrimitiveType("unsigned long long");
            case "float" -> new CPrimitiveType("float");
            case "double" -> new CPrimitiveType("double");
            case "_Bool", "bool" -> new CPrimitiveType("_Bool");
            case "__int128" -> new CPrimitiveType("int"); // fallback
            default -> {
                // Could be a typedef — let the converter resolve it
                yield new CPrimitiveType(name);
            }
        };
    }

    // ========== function qualType parser ==========
    // clang format: "int (const char *, int, ...)"

    private record FuncTypeInfo(CType returnType, boolean variadic) {
    }

    private FuncTypeInfo parseFunctionQualType(String qualType) {
        if (qualType == null || qualType.isEmpty()) {
            return new FuncTypeInfo(new CPrimitiveType("int"), false);
        }

        // Remove outer qualifiers
        qualType = stripQualifiers(qualType).trim();

        // Find the parameter list: find matching parens around the params
        var parenIdx = qualType.indexOf('(');
        if (parenIdx < 0) {
            return new FuncTypeInfo(qualTypeToCType(qualType), false);
        }

        var returnTypeStr = qualType.substring(0, parenIdx).trim();
        var returnType = qualTypeToCType(returnTypeStr);

        // Check for variadic "..."
        var variadic = qualType.contains("...");

        return new FuncTypeInfo(returnType, variadic);
    }

    // ========== helpers ==========

    private static String resolveQualType(JsonNode node) {
        var type = node.get("type");
        if (type == null || type.isNull()) return null;
        var qt = type.get("qualType");
        if (qt == null || qt.isNull()) return null;
        var text = qt.asText();
        return text != null && !text.isEmpty() ? text : null;
    }

    private static String stripQualifiers(String type) {
        var result = type.replace("const ", "")
                .replace(" volatile ", " ")
                .replace("volatile ", "")
                .replace(" restrict ", " ")
                .replace("restrict ", "")
                .trim();
        // Handle leading qualifiers like "const int" → "int"
        while (result.startsWith("const ")) result = result.substring(6).trim();
        while (result.startsWith("volatile ")) result = result.substring(9).trim();
        while (result.startsWith("restrict ")) result = result.substring(9).trim();
        // Handle trailing qualifiers after *, e.g. "char *const" → "char *"
        while (result.endsWith("const") || result.endsWith("volatile") || result.endsWith("restrict")) {
            if (result.endsWith("const")) result = result.substring(0, result.length() - 5).trim();
            else if (result.endsWith("volatile")) result = result.substring(0, result.length() - 8).trim();
            else result = result.substring(0, result.length() - 8).trim(); // restrict
        }
        return result;
    }

    private static final Pattern ANON_TYPE_PATTERN =
            Pattern.compile("\\(unnamed (struct|union) at .*?:(\\d+):(\\d+)\\)");

    /**
     * Normalize an anonymous struct/union type name from clang's qualType.
     * E.g. "(unnamed struct at D:/path/file.h:120:20)" → "_anon_struct_120_20"
     */
    private static String normalizeAnonTypeName(String qualTypeTag) {
        var m = ANON_TYPE_PATTERN.matcher(qualTypeTag);
        if (m.find()) {
            return "_anon_" + m.group(1) + "_" + m.group(2) + "_" + m.group(3);
        }
        return qualTypeTag;
    }

    /**
     * Generate a normalized name for an anonymous struct/union from its location.
     * Format: "_anon_struct_LINE_COL" or "_anon_union_LINE_COL"
     */
    private static String locToAnonName(JsonNode node, String tagUsed) {
        var loc = node.get("loc");
        if (loc == null || loc.isNull()) return null;
        var line = loc.get("line");
        var col = loc.get("col");
        if (line == null || col == null || line.isNull() || col.isNull()) return null;
        return "_anon_" + tagUsed + "_" + line.asInt() + "_" + col.asInt();
    }

    private static boolean isCompleteDefinition(JsonNode node) {
        var complete = node.get("completeDefinition");
        // Absent is treated as complete (clang omits this for some decl kinds)
        return complete == null || complete.isNull() || complete.asBool();
    }

    private static boolean isImplicit(JsonNode node) {
        var implicit = node.get("isImplicit");
        return implicit != null && implicit.asBool();
    }

    private static boolean isFileScope(JsonNode node) {
        // File-scope vars have parent as TranslationUnitDecl (no parentRecord)
        // We check this indirectly: a non-parameter, non-field top-level decl
        var parent = node.get("parent");
        return parent == null || parent.isNull();
    }
}
