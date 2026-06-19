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
import java.nio.file.Path;
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

    private final String moduleName;
    private final ParseSymbolTable table;

    private final Map<String, CType> typedefs = new HashMap<>();

    public C2FengConverter(String moduleName) {
        this.moduleName = moduleName;
        this.table = new ParseSymbolTable(
                Optional.empty(), new DedupCache<>());
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
            var id = new Identifier(ZERO, cf.name());
            fields.add(id, new StructureField(ZERO, id,
                    cf.bitfieldWidth().<Expression>map(w ->
                            new IntegerLiteral(ZERO, w).expr()),
                    convertType(cf.type())));
        }

        table.add(new StructureDefinition(ZERO,
                modifier(true),
                new Symbol(new Identifier(ZERO, struct.tagName())),
                TypeParameters.empty(),
                TypeDomain.STRUCT,
                fields));
    }

    // ========== Union ==========

    public void addUnion(CUnionType union) {
        if (!union.isComplete()) return;

        var fields = new IdentifierMap<StructureField>();
        for (var cf : union.fields()) {
            var id = new Identifier(ZERO, cf.name());
            fields.add(id, new StructureField(ZERO, id,
                    cf.bitfieldWidth().<Expression>map(w ->
                            new IntegerLiteral(ZERO, w).expr()),
                    convertType(cf.type())));
        }

        table.add(new StructureDefinition(ZERO,
                modifier(true),
                new Symbol(new Identifier(ZERO, union.tagName())),
                TypeParameters.empty(),
                TypeDomain.UNION,
                fields));
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
                    new Identifier(ZERO, constName),
                    Lazy.of(Primitive.INT.declarer()),
                    Lazy.of(new IntegerLiteral(ZERO, val).expr()));
            table.variables.add(new Identifier(ZERO, constName),
                    new GlobalVariable(true, v,
                            new Symbol(new Identifier(ZERO, constName))));
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
                    new Identifier(ZERO, p.name()),
                    convertType(p.type())));
        }
        if (func.variadic()) {
            ErrorUtil.unsupported(
                    "C variadic functions are not supported (incompatible with Fēng): %s",
                    func.name());
        }

        var paramSet = new ParameterSet(ZERO, params);

        var proto = func.isReturnVoid()
                ? new Prototype(ZERO, paramSet)
                : new Prototype(ZERO, paramSet, convertType(func.returnType()));

        table.add(new FunctionDefinition(ZERO,
                modifier(true),
                new Symbol(new Identifier(ZERO, func.name())),
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
                new Identifier(ZERO, gv.name()),
                Lazy.of(convertType(gv.type())),
                Lazy.nil());
        table.variables.add(new Identifier(ZERO, gv.name()),
                new GlobalVariable(export, v,
                        new Symbol(new Identifier(ZERO, gv.name()))));
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

    private TypeDeclarer mapPrimitiveType(CPrimitiveType pt) {
        if ("void".equals(pt.name())) {
            return Primitive.INT.declarer();
        }
        if ("char".equals(pt.name()) || "signed char".equals(pt.name())) {
            return Primitive.INT8.declarer();
        }
        if ("unsigned char".equals(pt.name())) {
            return Primitive.UINT8.declarer();
        }
        if ("short".equals(pt.name())) {
            return Primitive.INT16.declarer();
        }
        if ("unsigned short".equals(pt.name())) {
            return Primitive.UINT16.declarer();
        }
        if ("int".equals(pt.name())) {
            return Primitive.INT.declarer();
        }
        if ("unsigned int".equals(pt.name())) {
            return Primitive.UINT.declarer();
        }
        if ("long".equals(pt.name())) {
            return Primitive.INT64.declarer();
        }
        if ("unsigned long".equals(pt.name())) {
            return Primitive.UINT64.declarer();
        }
        if ("long long".equals(pt.name())) {
            return Primitive.INT64.declarer();
        }
        if ("float".equals(pt.name())) {
            return Primitive.FLOAT32.declarer();
        }
        if ("double".equals(pt.name())) {
            return Primitive.FLOAT64.declarer();
        }
        if ("_Bool".equals(pt.name())) {
            return Primitive.BOOL.declarer();
        }
        if ("size_t".equals(pt.name())) {
            return Primitive.UINT64.declarer();
        }
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
                        new Symbol(new Identifier(ZERO, st.tagName())),
                        TypeArguments.EMPTY));
    }

    private TypeDeclarer mapUnionRef(CUnionType ut) {
        return new DerivedTypeDeclarer(ZERO,
                new DerivedType(ZERO,
                        new Symbol(new Identifier(ZERO, ut.tagName())),
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

    // ========== Output ==========

    public void write(Appendable out) throws IOException {
        var mp = new ModulePath(new Identifier(ZERO, moduleName), Path.of(""));
        var module = new FModule(mp, List.of(), table);
        new MetaDataExtractor(module, out).write();
    }

    public ParseSymbolTable table() {
        return table;
    }
}
