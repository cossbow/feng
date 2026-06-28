package org.cossbow.feng.c2feng.convert;

import org.cossbow.feng.ast.*;
import org.cossbow.feng.ast.attr.Modifier;
import org.cossbow.feng.ast.dcl.*;
import org.cossbow.feng.ast.expr.Expression;
import org.cossbow.feng.ast.gen.DerivedType;
import org.cossbow.feng.ast.gen.TypeArguments;
import org.cossbow.feng.ast.gen.TypeParameters;
import org.cossbow.feng.ast.lit.IntegerLiteral;
import org.cossbow.feng.ast.mod.FModule;
import org.cossbow.feng.ast.mod.ModulePath;
import org.cossbow.feng.ast.proc.*;
import org.cossbow.feng.ast.struct.StructureDefinition;
import org.cossbow.feng.ast.struct.StructureField;
import org.cossbow.feng.c2feng.model.*;
import org.cossbow.feng.mod.MetaDataExtractor;
import org.cossbow.feng.parser.ParseSymbolTable;
import org.cossbow.feng.util.DedupCache;
import org.cossbow.feng.util.ErrorUtil;
import org.cossbow.feng.util.Lazy;
import org.cossbow.feng.util.Optional;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.cossbow.feng.ast.Position.ZERO;

/**
 * Converts C declaration IR (CAstModel) into Fēng AST nodes and
 * writes the result as metadata via {@link MetaDataExtractor}.
 */
public class C2FengConverter {
    private final ModulePath path;
    private final ParseSymbolTable table;

    private final Map<String, CType> typedefs = new HashMap<>();

    public C2FengConverter(ModulePath path) {
        this.path = path;
        this.table = new ParseSymbolTable(
                Optional.of(path), new DedupCache<>());
    }

    // ========== Typedef registration ==========

    public void addTypedef(CTypedef typedef) {
        typedefs.put(typedef.name(), typedef.underlyingType());
    }

    // ========== Struct ==========

    public void addStruct(CStructType struct) {
        if (!struct.isComplete()) return;

        var fields = new IdentifierMap<StructureField>();
        for (var cf : struct.fields()) {
            var id = new Identifier(cf.name());
            fields.add(id, new StructureField(ZERO, id,
                    cf.bitfieldWidth().<Expression>map(w ->
                            new IntegerLiteral(ZERO, w).expr()),
                    convertType(cf.type())));
        }

        table.add(new StructureDefinition(ZERO,
                modifier(true),
                symbol(new Identifier(struct.tagName())),
                TypeParameters.empty(),
                TypeDomain.STRUCT,
                fields, true));
    }

    // ========== Union ==========

    public void addUnion(CUnionType union) {
        if (!union.isComplete()) return;

        var fields = new IdentifierMap<StructureField>();
        for (var cf : union.fields()) {
            var id = new Identifier(cf.name());
            fields.add(id, new StructureField(ZERO, id,
                    cf.bitfieldWidth().<Expression>map(w ->
                            new IntegerLiteral(ZERO, w).expr()),
                    convertType(cf.type())));
        }

        table.add(new StructureDefinition(ZERO,
                modifier(true),
                symbol(new Identifier(union.tagName())),
                TypeParameters.empty(),
                TypeDomain.UNION,
                fields, true));
    }

    // ========== Enum → const int constants ==========

    public void addEnum(CEnumType enumType) {
        long nextVal = 0;
        for (var c : enumType.constants()) {
            long val = c.value().getOrElse(nextVal);
            nextVal = val + 1;

            var constName = enumType.tagName() + "$" + c.name();
            var v = new Variable(ZERO,
                    modifier(true),
                    Declare.CONST,
                    new Identifier(constName),
                    Lazy.of(Primitive.INT.declarer()),
                    Lazy.of(new IntegerLiteral(ZERO, val).expr()));
            table.variables.add(new Identifier(constName),
                    new GlobalVariable(true, v,
                            symbol(new Identifier(constName))));
        }
    }

    // ========== Function declaration ==========

    public void addFunction(CFunction func) {
        if (func.linkage() == CLinkage.STATIC) return;
        if (func.linkage() == CLinkage.EXTERN) return;

        var params = new ArrayList<Parameter>();
        for (var p : func.parameters()) {
            params.add(new FixedParameter(ZERO,
                    Modifier.empty(),
                    new Identifier(p.name()),
                    convertType(p.type())));
        }
        if (func.variadic()) {
            return; // C variadic incompatible with Feng, skip silently
        }

        var paramSet = new ParameterSet(ZERO, params);

        var proto = func.isReturnVoid()
                ? new Prototype(ZERO, paramSet)
                : new Prototype(ZERO, paramSet, convertType(func.returnType()));

        table.add(new FunctionDefinition(ZERO,
                modifier(true),
                symbol(new Identifier(func.name())),
                TypeParameters.empty(),
                proto));
    }

    // ========== Global variable ==========

    public void addGlobalVar(CGlobalVar gv) {
        if (gv.linkage() == CLinkage.EXTERN) return;

        var export = gv.linkage() != CLinkage.STATIC;
        var v = new Variable(ZERO,
                modifier(export),
                gv.isConst() ? Declare.CONST : Declare.VAR,
                new Identifier(gv.name()),
                Lazy.of(convertType(gv.type())),
                Lazy.nil());
        table.variables.add(new Identifier(gv.name()),
                new GlobalVariable(export, v,
                        symbol(new Identifier(gv.name()))));
    }

    // ========== Core type conversion ==========

    private TypeDeclarer convertType(CType type) {
        return switch (type) {
            case CPrimitiveType pt -> mapPrimitiveType(pt);
            case CPointerType pt -> Primitive.UINT64.declarer();
            case CArrayType at -> mapArrayType(at);
            case CEnumType et -> Primitive.INT.declarer();
            case CStructType st -> mapStructRef(st);
            case CUnionType ut -> mapUnionRef(ut);
            case CFunctionType ft -> Primitive.UINT64.declarer();
        };
    }

    // ---------- Primitive type mapping ----------

    private static final Map<String, Primitive> C_PRIMITIVE_MAP = Map.ofEntries(
            Map.entry("void", Primitive.INT32),
            Map.entry("char", Primitive.INT8),
            Map.entry("signed char", Primitive.INT8),
            Map.entry("unsigned char", Primitive.UINT8),
            Map.entry("short", Primitive.INT16),
            Map.entry("unsigned short", Primitive.UINT16),
            Map.entry("int", Primitive.INT32),
            Map.entry("unsigned int", Primitive.UINT32),
            Map.entry("long", Primitive.INT64),
            Map.entry("unsigned long", Primitive.UINT64),
            Map.entry("long long", Primitive.INT64),
            Map.entry("float", Primitive.FLOAT32),
            Map.entry("double", Primitive.FLOAT64),
            Map.entry("_Bool", Primitive.BOOL),
            Map.entry("size_t", Primitive.UINT64)
    );

    private TypeDeclarer mapPrimitiveType(CPrimitiveType pt) {
        var p = C_PRIMITIVE_MAP.get(pt.name());
        if (p != null) return p.declarer();
        // Try typedef expansion
        var resolved = resolveTypedef(pt.name());
        if (resolved != null) return convertType(resolved);
        // Unknown type — default to int
        return Primitive.INT.declarer();
    }

    // ---------- Array mapping ----------

    private TypeDeclarer mapArrayType(CArrayType at) {
        if (at.length().has()) {
            var td = new ArrayTypeDeclarer(ZERO,
                    convertType(at.elementType()),
                    Optional.of(new IntegerLiteral(ZERO, at.length().get()).expr()),
                    Optional.empty(), true);
            td.len(at.length().get()); // cached length required by MetaDataExtractor
            return td;
        }
        return Primitive.UINT64.declarer();
    }

    // ---------- Struct / union reference ----------

    private TypeDeclarer mapStructRef(CStructType st) {
        return new DerivedTypeDeclarer(ZERO,
                new DerivedType(ZERO,
                        symbol(new Identifier(st.tagName())),
                        TypeArguments.EMPTY));
    }

    private TypeDeclarer mapUnionRef(CUnionType ut) {
        return new DerivedTypeDeclarer(ZERO,
                new DerivedType(ZERO,
                        symbol(new Identifier(ut.tagName())),
                        TypeArguments.EMPTY));
    }

    // ---------- Typedef expansion ----------

    private CType resolveTypedef(String name) {
        return typedefs.get(name);
    }

    // ========== Utilities ==========

    private static Modifier modifier(boolean export) {
        return new Modifier(ZERO, export, new SymbolMap<>());
    }

    private Symbol symbol(Identifier id) {
        return new Symbol(ZERO, Optional.of(path), id);
    }

    // ========== Output ==========

    public void write(Appendable out) throws IOException {
        var module = new FModule(path, List.of(), table);
        new MetaDataExtractor(module, out).write();
    }

    public ParseSymbolTable table() {
        return table;
    }
}
