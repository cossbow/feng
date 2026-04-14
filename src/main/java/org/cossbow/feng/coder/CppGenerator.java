package org.cossbow.feng.coder;

import org.cossbow.feng.analysis.AnalyseSymbolTable;
import org.cossbow.feng.ast.*;
import org.cossbow.feng.ast.dcl.*;
import org.cossbow.feng.ast.expr.*;
import org.cossbow.feng.ast.gen.*;
import org.cossbow.feng.ast.lit.*;
import org.cossbow.feng.ast.oop.*;
import org.cossbow.feng.ast.proc.*;
import org.cossbow.feng.ast.stmt.*;
import org.cossbow.feng.ast.struct.StructureDefinition;
import org.cossbow.feng.ast.struct.StructureField;
import org.cossbow.feng.ast.var.*;
import org.cossbow.feng.dag.DAGGraph;
import org.cossbow.feng.util.CommonUtil;
import org.cossbow.feng.util.Groups;
import org.cossbow.feng.util.Optional;
import org.cossbow.feng.util.RepeatList;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.cossbow.feng.ast.dcl.ReferKind.PHANTOM;
import static org.cossbow.feng.ast.dcl.ReferKind.STRONG;
import static org.cossbow.feng.util.ErrorUtil.*;

public class CppGenerator {
    private final AnalyseSymbolTable table;
    private final Appendable out;
    private final boolean header;
    private final boolean debug;

    public CppGenerator(AnalyseSymbolTable table,
                        Appendable out, boolean header,
                        boolean debug) {
        this.table = table;
        this.out = out;
        this.header = header;
        this.debug = debug;
    }

    public CppGenerator(AnalyseSymbolTable table,
                        Appendable out,
                        boolean debug) {
        this(table, out, false, debug);
    }

    //

    static final String baseHeader = "cpp11/Header.h";
    static final String mainFile = "cpp11/Main.cpp";

    private static InputStream getResource(String res) {
        var cl = Thread.currentThread().getContextClassLoader();
        return new BufferedInputStream(Objects.requireNonNull(
                cl.getResourceAsStream(res)));
    }

    public static void copyBaseHeader(Path dir) {
        try (var is = getResource(baseHeader)) {
            Files.copy(is, dir.resolve("Header.h"),
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }


    private void includeHeader(String file) {
        write("#include ").write('"').write(file)
                .write(".h").write('"').newLine();
    }

    private void includeHeaders() {
        writeComment("base inner headers");
        includeHeader("Header");

        table.module.use(fm -> {
            if (!header) {
                includeHeader(fm.path().filename());
                return;
            }
            if (fm.imports().isEmpty()) return;
            writeComment("import headers");
            for (var i : fm.imports()) {
                includeHeader(i.filename());
            }
        });
    }

    private CppGenerator write(char c) {
        try {
            out.append(c);
        } catch (IOException e) {
            io(e);
        }
        return this;
    }

    private CppGenerator write(CharSequence cs) {
        try {
            out.append(cs);
        } catch (IOException e) {
            io(e);
        }
        return this;
    }

    private CppGenerator write(int b) {
        return write(Integer.toString(b));
    }

    private CppGenerator write(long b) {
        return write(Long.toString(b));
    }

    private CppGenerator write(Identifier name) {
        if (!name.unnamed()) write('$');
        return write(name.value());
    }

    private CppGenerator write(Label label) {
        return write(label.name()).write('_').write(label.id());
    }

    private static final String COMMA = ", ";

    private int indentValue;

    CppGenerator indent() {
        indentValue++;
        return this;
    }

    CppGenerator dedent() {
        indentValue--;
        return this;
    }

    CppGenerator newLine() {
        write('\n');
//        for (int i = 0; i < indentValue; i++) {
//            write('\t');
//        }
        return this;
    }

    void writeComment(String text) {
        write("// ").write(text).newLine();
    }

    // global

    private void definePre() {
        if (header) {
            var name = table.module.must()
                    .path().filename().toUpperCase();
            write("#ifndef __HEADER_").write(name).newLine();
            write("#define __HEADER_").write(name).newLine();
            return;
        }
        if (debug) write("#define FENG_DEBUG_MEMORY").newLine();

/*
        write("#define FENG_MAX_CLASS_NUM ").write(ClassDefinition.maxId()).newLine();
        var ma = table.dagClasses.all().stream()
                .mapToInt(d -> d.ancestors().size()).max();
        ma.ifPresent(s -> {
            write("#define FENG_MAX_INHERIT_SIZE ").write(s).newLine();
        });
        var mi = table.dagClasses.all().stream()
                .mapToInt(d -> d.allImpls().size()).max();
        mi.ifPresent(s -> {
            write("#define FENG_MAX_IMPLS_SIZE ").write(s).newLine();
        });
*/

/*
        for (var d : TypeDomain.values()) {
            write("#define ").domain(d).write(' ')
                    .write(d.ordinal()).newLine();
        }
        newLine();
*/
    }

    private void endFile() {
        if (!header) return;
        write("#endif ").newLine();
    }

    private void declareType() {
        if (table.module.has()) {
            if (!header) return;
        }
        writeComment("type declarations");
//        declarePrimitive();
        writeComment("declare primitive");
        for (var t : table.enumList) declareType(t);
        writeComment("declare structure");
        for (var t : table.dagStructures) declareType(t);
        writeComment("declare interface");
        for (var t : table.dagInterfaces) declareType(t);
        writeComment("declare class");
        for (var t : table.dagClasses) declareType(t);
        newLine();
    }

    public void write() {
        definePre();
        includeHeaders();

        declareType();

        declareFunction();

        writeComment("global const");
        declareGlobalVar(table.constVars);

        literalStringCache();
        enumDefinition();
        structureDefinition();
        classesDefinition();

        writeComment("global variable");
        declareGlobalVar(table.dagVars);

        functionDefinition();

        endFile();
    }

    private void declareGlobalVar(
            List<GlobalVariable> vars) {
        vars.forEach(this::write);
        newLine().newLine();
    }

    private void declareGlobalVar(
            DAGGraph<GlobalVariable> vars) {
        vars.bfs(this::write);
        newLine().newLine();
    }

    private Optional<ClassDefinition> findClass(TypeDeclarer td) {
        if (td instanceof DerivedTypeDeclarer dtd) {
            var def = dtd.def();
            if (def instanceof ClassDefinition cd) {
                return Optional.of(cd);
            }
        }
        return Optional.empty();
    }

    private TypeDefinition findType(DefinedType dt) {
        if (dt instanceof PrimitiveType pt) {
            return pt.primitive().type();
        }
        return ((DerivedType) dt).def.must();
    }

    // 写入全局的字符串常量池：非C-type字符串，没有尾部的'0'
    private void literalStringCache() {
        if (header) return;
        writeComment("string cache");
        var list = table.stringCache
                .keySet().stream()
                .sorted(Comparator.comparingInt(StringLiteral::id))
                .toList();
        for (var sl : list) {
            write("static Feng$GlobalArray<Byte,").write(sl.length());
            write("> ").literalString(sl);
            write("= {{{1}}, Feng$Array<Byte, ");
            write(sl.length()).write(">{");
            for (byte b : sl.value()) {
                write(b).write(',');
            }
            write("}}").endStmt();
        }
        newLine();
    }

    // 用于引用全局字符串常量
    private CppGenerator literalString(StringLiteral sl) {
        return write("Feng$constString_").write(sl.id());
    }

    private CppGenerator write(TypeParameter tp) {
        return write(tp.name());
    }

    private void write(TypeParameters tps) {
        if (tps.isEmpty()) return;
        write("template<");
        for (int i = 0; i < tps.size(); i++) {
            if (i > 0) write(", ");
            write("typename ").write(tps.get(i));
        }
        write('>').newLine();
    }

    private CppGenerator write(TypeArguments tas) {
        if (tas.isEmpty()) return this;
        write('<');
        joinByComma(tas, this::write);
        return write('>');
    }

    void declareType(StructureDefinition def) {
        write(def.generic());
        write(def.domain().name).write(' ');
        write(def.symbol());
        endStmt();
    }

    void declareType(ClassDefinition def) {
        write(def.generic());
        write("class ").write(def.symbol()).endStmt();
    }

    CppGenerator enumName(EnumDefinition ed) {
        return write("Feng$Enum_").write(ed.symbol());
    }

    void declareType(EnumDefinition def) {
    }

    void declareType(InterfaceDefinition def) {
        write(def.generic());
        write("class ").write(def.symbol()).endStmt();
    }

    void enumDefinition() {
        writeComment("enum definition");
        for (var ed : table.enumList) {
            visitEnum(ed);
        }
        newLine();
    }

    void visitEnum(EnumDefinition ed) {
        if (header) write("extern");
        write(" Feng$Array<Feng$Enum").write(',');
        write(ed.size()).write("> ").enumName(ed);
        if (header) {
            endStmt();
            return;
        }
        write(" = {").newLine();
        for (var v : ed.values()) {
            write("Feng$Enum{").write(v.val()).write(',');
            write(v.nameLit()).write(".sr()},").newLine();
        }
        write('}').endStmt().newLine();
    }

    void structureDefinition() {
        if (table.module.has()) {
            if (!header) return;
        }
        writeComment("struct definition");
        table.dagStructures.bfs(this::write);
        newLine();
    }

    void declareFunction() {
        if (table.module.has()) {
            if (!header) return;
        }
        writeComment("prototype definition");
        table.dagPrototypes.bfs(this::writePrototype);
        newLine();

        writeComment("function declaration");
        for (var fd : table.functionList) {
            declareFunction(fd);
            newLine();
        }
        newLine();
    }

    void declareFunction(FunctionDefinition fd) {
        if (fd.entry()) return;
        write(fd.generic());
        write(fd.symbol(), fd.procedure().prototype());
        endStmt();
    }

    void functionDefinition() {
        writeComment("function definition");
        for (var fd : table.functionList) {
            if (fd.builtin()) continue;
            implFunc(fd);
            newLine();
        }
        newLine();
        table.main.use(this::writeMain);
        newLine();
    }

    void writeMain(FunctionDefinition main) {
        writeComment("entry function");
        implFunc(main);
        newLine();
        if (header) return;

        try (var is = getResource(mainFile);
             var ir = new InputStreamReader(Objects.requireNonNull(is));
             var r = new BufferedReader(ir)) {
            r.lines().forEach(line -> write(line).newLine());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }


    private CppGenerator write(GlobalVariable v) {
        if (v.export()) {
            if (header) write("extern ");
        } else {
            if (header) return this;
            write("static ");
        }

        var t = v.type().must();
        declare(v);
        if (v.export() && header) return endStmt();

        write(" = ");
        v.value().use(e -> {
            writeValue(e, t);
        }, () -> {
            if (t.maybeRefer().has())
                write("nullptr");
            else
                write("{}");
        });
        return endStmt();
    }

    private CppGenerator castRef(
            Expression v, TypeDeclarer t) {
        var rt = v.resultType.must();
        if (t.baseTypeSame(rt)) {
            return write(v);
        }
        if (t instanceof ArrayTypeDeclarer at) {
            if (rt instanceof ArrayTypeDeclarer art) {
                write("Feng$mapA2A<").write(art.element()).write(',');
                if (art.refer().none()) write(art.len());
                else write(art);
                write(',').write(at.element());
                return write(">(").write(v).write(')');
            }
            if (rt.isNil()) {
                return write("nullptr");
            } else {
                write("Feng$mapU2A<").baseTypeSymbol(rt).write(',');
                write(at.element()).write(">(");
                if (rt.maybeRefer().none()) write('&');
                return write(v).write(')');
            }
        }
        if (rt instanceof ArrayTypeDeclarer art) {
            write("Feng$mapA2U<").write(art.element()).write(',');
            write(art).write(',').baseTypeSymbol(t).write(">(");
            return write(v).write(')');
        }
        return write(v);
    }

    private CppGenerator referPhantom(Expression v, TypeDeclarer t) {
        var vt = v.resultType.must();
        var vr = vt.maybeRefer();
        if (vr.has()) {
            castRef(v, t);
            return this;
        }

        // 原型相同不需要转类型
        if (t.baseTypeSame(vt)) {
            if (!(vt instanceof ArrayTypeDeclarer avt))
                return write(v);

            return write('{').write(v)
                    .write(".values,").write(avt.len())
                    .write('}');
        }

        if (t instanceof ArrayTypeDeclarer) {
            return castRef(v, t);
        }

        return write(v);
    }

    private CppGenerator writeLiteral(LiteralExpression v, TypeDeclarer t) {
        if (v.literal() instanceof NilLiteral)
            return castRef(v, t);

        if (v.literal() instanceof StringLiteral sl) {
            if (t instanceof LiteralTypeDeclarer) {
                return write(sl).write(".sr()");
            }
            var r = t.maybeRefer();
            if (r.none()) {
                return writeData(sl, t);
            }
            if (r.get().isKind(PHANTOM)) {
                return write(sl).write(".pr()");
            }
            return write(sl).write(".sr()");
        }

        return write(v);
    }

    private CppGenerator writeValue(
            Expression v, TypeDeclarer t) {
        if (v instanceof LiteralExpression le)
            return writeLiteral(le, t);

        var r = t.maybeRefer();
        if (r.none()) return write(v);

        if (r.get().isKind(PHANTOM)) {
            return referPhantom(v, t);
        }

        return castRef(v, t);
    }

    private CppGenerator varName(Variable v) {
        if (v instanceof GlobalVariable gv) {
            write(gv.symbol());
        } else {
            write(v.name());
        }
        return write('_').write(v.id());
    }

    private CppGenerator declare(Variable v) {
        return declare(() -> varName(v), v.type().must());
    }

    private CppGenerator declareVar(Variable v) {
        var t = v.type().must();
        declare(v);
        write(" = ");
        v.value().use(e -> {
            writeValue(e, t);
        }, () -> {
            if (t.maybeRefer().has())
                write("nullptr");
            else
                write("{}");
        });
        return endStmt();
    }

    // type declarer

    private CppGenerator defaultValue(TypeDeclarer td) {
        if (td instanceof FuncTypeDeclarer) {
            return write("nullptr");
        }
        if (td.maybeRefer().has()) {
            return write("nullptr");
        }
        if (td instanceof PrimitiveTypeDeclarer ||
                td instanceof GenericTypeDeclarer) {
            return write('0');
        }
        if (td instanceof DerivedTypeDeclarer dtd) {
            var dt = dtd.derivedType();
            var def = dtd.def();
            if (def instanceof ClassDefinition cd
                    && !cd.isFinal()) {
                return write(dt.symbol()).write("()");
            }
            write(dt.symbol());
        }
        return write("{}");
    }

    private CppGenerator write(ArrayTypeDeclarer td) {
        if (td.refer().none()) {
            return write("Feng$Array<").write(td.element()).write(',')
                    .write(td.len()).write('>');
        }
        var r = td.refer().get();
        if (r.isKind(PHANTOM))
            write("Feng$ArrayPRefer<");
        else
            write("Feng$ArraySRefer<");
        return write(td.element()).write('>');
    }

    private CppGenerator write(DerivedTypeDeclarer td) {
        var def = td.def();
        if (def instanceof EnumDefinition)
            return write(Primitive.INT);

        if (td.refer().none())
            return write(td.derivedType());
        var r = td.refer().get();
        if (r.isKind(PHANTOM))
            write("Feng$PRefer<");
        else
            write("Feng$SRefer<");
        return write(td.derivedType()).write('>');
    }

    private static final Map<Primitive, String> PrimitiveName =
            Arrays.stream(Primitive.values())
                    .collect(Collectors.toMap(Function.identity(),
                            p -> CommonUtil.upperFirst(p.code)));

    private CppGenerator write(Primitive p) {
        return write(PrimitiveName.get(p));
    }

    private CppGenerator write(PrimitiveTypeDeclarer td) {
        if (td.refer().none()) return write(td.primitive());
        var r = td.refer().get();
        if (r.isKind(PHANTOM))
            write("Feng$PRefer<");
        else
            write("Feng$SRefer<");
        return write(td.primitive()).write('>');
    }

    private CppGenerator write(FuncTypeDeclarer e) {
        return write(e.prototype());
    }

    private CppGenerator write(GenericTypeDeclarer e) {
        return write(e.param());
    }

    private CppGenerator write(EnumTypeDeclarer e) {
        return write(Primitive.INT);
    }

    private CppGenerator write(LiteralTypeDeclarer t) {
        if (t.isInteger()) return write(Primitive.INT);
        if (t.isFloat()) return write(Primitive.FLOAT);
        if (t.isBool()) return write(Primitive.BOOL);
        if (t.literal() instanceof StringLiteral sl) {
            return write(sl.array(Optional.of(STRONG)));
        }

        return this;
    }

    private CppGenerator write(TypeDeclarer e) {
        return switch (e) {
            case ArrayTypeDeclarer ee -> write(ee);
            case DerivedTypeDeclarer ee -> write(ee);
            case FuncTypeDeclarer ee -> write(ee);
            case PrimitiveTypeDeclarer ee -> write(ee);
            case GenericTypeDeclarer ee -> write(ee);
            case EnumTypeDeclarer ee -> write(ee);
            case LiteralTypeDeclarer ee -> write(ee);
            case null, default -> unreachable();
        };
    }

    //

    private CppGenerator write(StructureField sf) {
        write(sf.type());
        write(' ');
        write(sf.name());
        if (sf.bitfield().has()) {
            write(':').write(sf.bits());
        }
        return endStmt();
    }

    private CppGenerator write(StructureDefinition sd) {
        write(sd.domain().name).write(' ');
        write(sd.symbol());
        write('{').newLine();
        for (var sf : sd.fields()) {
            write(sf);
        }
        write('}').endStmt();
        write("static_assert(sizeof(");
        write(sd.domain().name).write(' ');
        write(sd.symbol()).write(") == ");
        write(sd.layout().must().size()).write(')');
        return endStmt();
    }

    // prototype definition

    private void writePrototype(PrototypeDefinition pd) {
        write(pd.generic());
        write("using ").write(pd.symbol()).write(" = ");
        write(pd.prototype());
        endStmt().newLine();
    }


    // class & interface definition

    private void classesDefinition() {
        writeComment("class/interface definition");
//        classRelation(table.dagClasses);
        table.dagInterfaces.bfs(this::declareInterface);
        table.dagClasses.bfs(this::declareClass);
        table.dagClasses.bfs(this::implClass);
        newLine();
    }


    // interface definition

    private void writeExtends(List<DerivedType> exts) {
        if (exts.isEmpty()) return;
        write(' ');
        write(':');
        var first = true;
        for (var p : exts) {
            if (first) first = false;
            else write(',');
            write("public ").write(p);
        }
    }

    void defaultDeconstruct(ObjectDefinition def) {
        if (def instanceof ClassDefinition cd && cd.resource()) {
            write("virtual ~").write(def.symbol())
                    .write("() {").newLine();
            write("this->");
            write(cd.resourceFree().must().name());
            write("()").endStmt();
            write('}').newLine();
            return;
        }
        write("virtual ~").write(def.symbol())
                .write("() = default").endStmt();
    }

    void declareInterface(InterfaceDefinition id) {
        if (id.builtin()) return;
        if (table.module.has()) {
            if (!header) return;
        }
        if (id.builtin()) return;
        write(id.generic());
        write("class ");
        write(id.symbol());
        writeExtends(id.parts().values());
        write(" {\n").indent();
        write("public:\n");
        for (var im : id.methods()) {
            write("virtual ");
            declareMethod(im);
            write(" = 0").endStmt();
        }
        defaultDeconstruct(id);
        operatorEquals(id);
        write("}").endStmt();
    }

    // class definition

    private volatile ClassDefinition enterClass;
    private volatile ClassMethod enterMethod;

    private void declareClass(ClassDefinition cd) {
        if (cd.builtin()) return;
        if (table.module.has()) {
            if (!header) return;
        }
        assert enterClass == null;
        enterClass = cd;
        write(cd.generic());
        write("class ");
        write(cd.symbol());
        if (cd.isFinal()) {
            write(" final");
        } else {
            var pubs = Stream.concat(cd.inherit().stream(),
                    cd.impl().stream()).toList();
            writeExtends(pubs);
        }
        write("{\n").indent();
        write("public:\n");

        for (var f : cd.fields().values())
            write(f);
        if (!cd.isFinal()) {
            emptyConstruct(cd);
            fullConstruct(cd);
            copyConstruct(cd, false);
            copyConstruct(cd, true);
            operatorAssign(cd);
            defaultDeconstruct(cd);
        }
        operatorEquals(cd);

        for (var m : cd.methods().values()) {
            if (!m.override().isEmpty())
                write("virtual ");
            declareMethod(m).endStmt();
        }

        dedent().write("};\n\n");
        enterClass = null;
    }

    private <T> void joinByComma(Iterable<T> s, Consumer<T> w) {
        var first = true;
        for (var t : s) {
            if (first) first = false;
            else write(',');
            w.accept(t);
        }
    }

    private void emptyConstruct(ClassDefinition cd) {
        write(cd.symbol());
        write("() = default").endStmt();
    }

    private void fullConstruct(ClassDefinition cd) {
        if (cd.allFields().isEmpty()) return;
        write("explicit ");
        var inherit = cd.inherit().must();
        write(cd.symbol());
        write('(');
        joinByComma(cd.allFields().values(), f -> {
            var t = inherit.gm().mapIf(f.type());
            declare(f.name(), t);
        });
        write("):");
        write(inherit);
        write('(');
        joinByComma(cd.inheritFields().values(),
                f -> write(f.name()));
        write(')');
        if (!cd.fields().isEmpty()) write(',');
        joinByComma(cd.fields().values(), f -> {
            write(f.name()).write('(')
                    .write(f.name()).write(')');
        });
        write("{}").newLine();
    }

    private void copyConstruct(ClassDefinition cd, boolean tmp) {
        write(cd.symbol());
        write('(');
        definedToken(cd);
        if (tmp) write("&&");
        else write("&");
        write(") = default").endStmt();
    }

    private void operatorAssign(ClassDefinition cd) {
        definedToken(cd);
        write(" &operator=(const ");
        definedToken(cd);
        write(" &) = default").endStmt();
    }

    private void operatorEquals(ObjectDefinition cd) {
        write("auto operator<=>(const ");
        definedToken(cd);
        write(" &) const = default").endStmt();
    }

    private CppGenerator declareMethod(Method m) {
        write(m.generic());
        return write(() -> write(m.name()), m.prototype());
    }

    private void implClass(ClassDefinition cd) {
        if (cd.builtin()) return;
        if (table.module.has()) {
            if (header == cd.generic().isEmpty())
                return;
        }
        assert enterClass == null;
        enterClass = cd;
        for (var cm : cd.methods()) {
            implMethod(cm);
        }
        enterClass = null;
    }

    private CppGenerator definedToken(ObjectDefinition cd) {
        write(cd.symbol());
        if (cd.generic().isEmpty()) return this;
        write('<');
        joinByComma(cd.generic(), tp -> write(tp.name()));
        write('>');
        return this;
    }

    private void implMethod(ClassMethod cm) {
        assert enterClass != null;
        assert enterMethod == null;
        enterMethod = cm;
        write(enterClass.generic());
        write(cm.generic());
        write(() -> {
            definedToken(enterClass).write("::").write(cm.name());
        }, cm.prototype());
        newLine();
        write(cm.procedure().must());
        enterMethod = null;
    }

    private CppGenerator write(Symbol s) {
        s.module().use(mp -> {
            write(mp.toString());
        });
        write(s.name());
        return this;
    }

    private CppGenerator write(DerivedType dt) {
        return write(dt.symbol()).write(dt.generic());
    }

    private CppGenerator write(GenericType gt) {
        return write(gt.name());
    }

    private CppGenerator baseTypeSymbol(TypeDeclarer td) {
        if (td instanceof PrimitiveTypeDeclarer ptd) {
            return write(ptd.primitive());
        } else if (td instanceof DerivedTypeDeclarer dtd) {
            return write(dtd.derivedType());
        }
        return unreachable();
    }

    private CppGenerator write(PrimitiveType dt) {
        return write(dt.primitive());
    }

    private CppGenerator declare(Runnable namer, TypeDeclarer td) {
        write(td).write(' ');
        namer.run();
        return this;
    }

    private CppGenerator declare(Identifier name, TypeDeclarer td) {
        return declare(() -> write(name), td);
    }

    private CppGenerator write(ClassField cf) {
        assert enterClass != null;
        return declare(cf.name(), cf.type()).endStmt();
    }

    private volatile FunctionDefinition enterFunc;

    CppGenerator write(ParameterSet ps) {
        joinByComma(ps, this::declare);
        return this;
    }

    private CppGenerator write(Prototype pt) {
        write("Feng$Prototype<");
        pt.returnSet().use(this::write, () -> write("void"));
        write('(').write(pt.parameterSet()).write(')');
        return write('>');
    }

    private CppGenerator write(Runnable nameToken, Prototype pt) {
        var ps = pt.parameterSet();
        pt.returnSet().use(this::write, () -> write("void"));
        write(' ');
        nameToken.run();
        write('(').write(ps).write(')');
        return this;
    }

    private CppGenerator write(Symbol symbol, Prototype prototype) {
        return write(() -> write(symbol), prototype);
    }

    private void implFunc(FunctionDefinition fd) {
        if (fd.builtin()) return;
        if (table.module.has()) {
            if (header == fd.generic().isEmpty())
                return;
        }
        assert enterFunc == null;
        enterFunc = fd;
        write(fd.generic());
        var proc = fd.procedure();
        write(fd.symbol(), proc.prototype());
        newLine();
        write(proc);
        enterFunc = null;
    }

    private void write(Procedure proc) {
        write('{').newLine();
        write((Statement) proc.body());
        if (noTerminal(proc.body().list()))
            exitScope(proc);
        write('}').newLine();
    }

    //

    private boolean noTerminal(List<Statement> list) {
        if (list.isEmpty()) return false;
        return switch (list.getLast()) {
            case ReturnStatement rs -> false;
            case ThrowStatement ts -> false;
            case null, default -> true;
        };
    }

    private void exitScope(Scope s) {
    }

    private void write(List<Statement> list) {
        for (var s : list) write(s);
    }

    private CppGenerator write(Statement e) {
        switch (e) {
            case DeclarationStatement ee -> write(ee);
            case AssignmentsStatement ee -> write(ee);
            case BlockStatement ee -> write(ee);
            case BreakStatement ee -> write(ee);
            case CallStatement ee -> write(ee);
            case ContinueStatement ee -> write(ee);
            case ForStatement ee -> write(ee);
            case GotoStatement ee -> write(ee);
            case IfStatement ee -> write(ee);
            case LabeledStatement ee -> write(ee);
            case ReturnStatement ee -> write(ee);
            case SwitchStatement ee -> write(ee);
            case ThrowStatement ee -> write(ee);
            case TryStatement ee -> write(ee);
            default -> unreachable();
        }
        return this;
    }

    private CppGenerator write(ForStatement e) {
        return switch (e) {
            case ConditionalForStatement ee -> write(ee);
            case IterableForStatement ee -> write(ee);
            case null, default -> unreachable();
        };
    }

    private CppGenerator write(BlockStatement bs) {
        if (bs.newScope()) write('{').newLine();

        write(bs.list());

        if (bs.newScope()) {
            if (noTerminal(bs.list())) exitScope(bs);
            dedent().write('}').newLine();
        }
        return this;
    }

    private CppGenerator endStmt() {
        return write(";").newLine();
    }

    private CppGenerator write(ReturnStatement rs) {
        if (rs.result().none()) {
            return write("return").endStmt();
        }

        var re = rs.result().get();
        var prot = rs.procedure().must().prototype();
        var rt = prot.returnSet().must();
        return write("return ").writeValue(re, rt).endStmt();
    }

    private CppGenerator write(DeclarationStatement ds) {
        ds.variables().forEach(this::declareVar);
        return this;
    }

    private CppGenerator write(Operand e) {
        switch (e) {
            case IndexOperand ee -> write(ee);
            case FieldOperand ee -> write(ee);
            case VariableOperand ee -> write(ee);
            case DereferOperand ee -> write(ee);
            default -> unreachable();
        }
        return this;
    }

    private CppGenerator write(VariableOperand e) {
        varName(e.variable().must());
        return this;
    }

    private CppGenerator write(IndexOperand e) {
        return index(e.subject(), e.index());
    }

    private CppGenerator write(FieldOperand e) {
        var td = (DerivedTypeDeclarer) e.subject().resultType.must();
        return ofMember(e.subject(), td).write(e.field());
    }

    private CppGenerator write(DereferOperand e) {
        return derefer(e.subject());
    }

    private CppGenerator write(AssignmentsStatement as) {
        for (var a : as.list()) {
            var e = a.operand();
            writeAssign(a.operand(), a.value()).endStmt();
        }
        return this;
    }

    private CppGenerator write(CallStatement e) {
        if (e.replace().has()) {
            write(e.replace().must());
        } else {
            write((Expression) e.call()).endStmt();
        }
        return this;
    }

    private CppGenerator writeAssign(Operand o, Expression v) {
        var t = o.type.must();
        var r = t.maybeRefer();
        if (r.has()) {
            if (r.get().isKind(PHANTOM)) {
                return write(o).write(" = ").castRef(v, t);
            }
            write(o).write('=');
            if (v.unbound()) return write(v);
            return castRef(v, t);
        }

        var cd = findClass(t);
        if (cd.none()) {
            return write(o).write(" = ").write(v);
        }

        write(o).write('=').write(v);
        return this;
    }

    private final Map<ForStatement, Groups.G2<Label, Label>>
            loopLabels = new HashMap<>();

    private CppGenerator write(ConditionalForStatement fs) {
        var lg = Groups.g2(
                new Label(new Identifier("loopNext")),
                new Label(new Identifier("loopExit")));
        loopLabels.put(fs, lg);
        write('{');
        fs.initializer().use(this::write);
        write("for(;;) {");
        write("if(").write(fs.condition()).write("){").newLine();
        write(fs.body());
        write("}else{").newLine();
        write("break").endStmt();
        write('}');
        write(lg.a()).write(":").endStmt();
        fs.updater().use(this::write);
        write('}');
        write(lg.b()).write(":").endStmt();
        write('}');
        return this;
    }

    private CppGenerator write(IterableForStatement s) {
        return write(s.replace.must());
    }

    private CppGenerator write(ContinueStatement s) {
        var g = loopLabels.get(s.target.must());
        return writeGoto(g.a());
    }

    private CppGenerator write(BreakStatement s) {
        var g = loopLabels.get(s.target.must());
        return writeGoto(g.b());
    }

    private CppGenerator write(GotoStatement e) {
        return writeGoto(e.target.must());
    }

    private CppGenerator writeGoto(Label l) {
        return write("goto ").write(l).endStmt();
    }

    private CppGenerator write(IfStatement is) {
        is.init().use(s -> {
            write('{');
            write(s);
        });
        write("if(");
        write(is.condition());
        write(')');
        write(is.yes());
        is.not().use(s -> {
            write(" else ").write(s);
        });
        if (is.init().has()) {
            exitScope(is);
            write('}');
        }
        return this;
    }

    private CppGenerator write(LabeledStatement s) {
        return write(s.label()).write(':').write(s.target());
    }

    private CppGenerator write(SwitchStatement ss) {
        if (ss.init().has()) {
            write('{');
            write(ss.init().get());
        }
        write("switch(");
        write(ss.value());
        write("){");
        for (var br : ss.branches()) {
            for (var cs : br.constants()) {
                write("case ").write(cs).write(':');
            }
            write(br);
            write("break;").newLine();
        }
        ss.defaultBranch().use(br -> {
            write("default: ");
            write(br);
        });
        write('}').newLine();
        if (ss.init().has()) {
            write('}').newLine();
        }
        write('}').newLine();
        return this;
    }

    private CppGenerator write(Branch e) {
        write((Statement) e.body());
        return this;
    }

    private CppGenerator write(ThrowStatement e) {
        return unsupported("throw");
    }

    private CppGenerator write(TryStatement e) {
        return unsupported("try..catch");
    }

    // expression

    private CppGenerator write(Expression e) {
        return switch (e) {
            case BinaryExpression ee -> write(ee);
            case ReferEqualExpression ee -> write(ee);
            case UnaryExpression ee -> write(ee);
            case ArrayExpression ee -> write(ee);
            case AssertExpression ee -> write(ee);
            case ConvertExpression ee -> write(ee);
            case CallExpression ee -> write(ee);
            case CurrentExpression ee -> write(ee);
            case IndexOfExpression ee -> write(ee);
            case LambdaExpression ee -> write(ee);
            case LiteralExpression ee -> write(ee);
            case MemberOfExpression ee -> write(ee);
            case MethodExpression ee -> write(ee);
            case NewExpression ee -> write(ee);
            case ObjectExpression ee -> write(ee);
            case PairsExpression ee -> write(ee);
            case ParenExpression ee -> write(ee);
            case SymbolExpression ee -> write(ee);
            case VariableExpression ee -> write(ee);
            case DereferExpression ee -> write(ee);
            case CheckNilExpression ee -> write(ee);
            case EnumValueExpression ee -> write(ee);
            case EnumIdExpression ee -> write(ee);
            case ArrayLenExpression ee -> write(ee);
            case ConditionalExpression ee -> write(ee);
            case BlockExpression ee -> write(ee);
            default -> unreachable();
        };
    }

    private CppGenerator writeValues(
            List<Expression> values, List<TypeDeclarer> dstTypes) {
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) write(COMMA);
            writeValue(values.get(i), dstTypes.get(i));
        }
        return this;
    }

    private CppGenerator write(BinaryExpression e) {
        var op = e.operator();
        if (op == BinaryOperator.POW) {
            if (e.right().resultType.must().isInteger()) {
                write("Feng$fastPow(");
                if (e.left().resultType.must().isInteger()) {
                    write("(Int64)");
                }
            } else {
                write("std::powl(");
            }
            write(e.left()).write(',').write(e.right());
            return write(')');
        }

        String o = switch (op) {
            case MUL -> "*";
            case DIV -> "/";
            case MOD -> "%";
            case ADD -> "+";
            case SUB -> "-";
            case LSHIFT -> "<<";
            case RSHIFT -> ">>";
            case BITAND -> "&";
            case BITXOR -> "^";
            case BITOR -> "|";
            case EQ -> "==";
            case NE -> "!=";
            case GT -> ">";
            case LT -> "<";
            case GE -> ">=";
            case LE -> "<=";
            case AND -> "&&";
            case OR -> "||";
            default -> unreachable();
        };
        return write('(').write(e.left()).write(')')
                .write(o).write('(').write(e.right()).write(')');
    }

    private CppGenerator write(ReferEqualExpression e) {
        write(e.left());
        var lt = e.left().resultType.must();
        if (lt instanceof ArrayTypeDeclarer) write(".start");
        else write(".t");
        write(e.same() ? "==" : "!=");
        write(e.right());
        var rt = e.right().resultType.must();
        if (rt instanceof ArrayTypeDeclarer) write(".start");
        else write(".t");
        return this;
    }

    private CppGenerator write(ArrayExpression e) {
        var t = (ArrayTypeDeclarer) e.resultType.must();
        e.type().use(this::write, () -> {
            e.lt.use(this::write, () -> {
                write(t);
            });
        });
        write('{');
        var types = new RepeatList<>(
                t.element(), e.size());
        writeValues(e.elements(), types);
        write('}');
        return this;
    }

    private CppGenerator write(AssertExpression e) {
        if (!e.needCheck())
            return castRef(e.subject(), e.type());

        var src = (DerivedTypeDeclarer) e.subject().resultType.must();
        var dst = e.type();
        write("Feng$assert");
        write('<').write(src.derivedType().symbol()).write(',');
        write(dst.derivedType().symbol()).write(">(")
                .write(e.subject()).write(')');
        return this;
    }

    private CppGenerator write(CallExpression e) {
        write(e.callee());
        return write('(').writeValues(e.arguments(), e.prototype()
                .must().parameterSet().types()).write(')');
    }

    private CppGenerator write(IndexOfExpression e) {
        return index(e.subject(), e.index());
    }

    private CppGenerator write(VariableExpression e) {
        return varName(e.variable());
    }

    private CppGenerator write(DereferExpression e) {
        return derefer(e.subject());
    }

    private CppGenerator write(LambdaExpression e) {
        return unsupported("尼玛函数");
    }

    private CppGenerator write(Literal e) {
        return switch (e) {
            case BoolLiteral ee -> write(ee);
            case FloatLiteral ee -> write(ee);
            case IntegerLiteral ee -> write(ee);
            case NilLiteral ee -> write(ee);
            case StringLiteral ee -> write(ee);
            case null, default -> unreachable();
        };
    }

    private CppGenerator write(BoolLiteral e) {
        write(Boolean.toString(e.value()));
        return this;
    }

    private CppGenerator write(FloatLiteral e) {
        write(e.value().toString());
        return this;
    }

    private CppGenerator write(IntegerLiteral e) {
        write(e.value().toString(e.radix()));
        return this;
    }

    private CppGenerator write(NilLiteral e) {
        write("nullptr");
        return this;
    }

    private CppGenerator writeData(StringLiteral e, TypeDeclarer t) {
        write(t).write('{');
        for (byte b : e.value()) {
            write(b).write(',');
        }
        return write('}');
    }

    private CppGenerator write(StringLiteral e) {
        return literalString(e);
    }

    private CppGenerator write(LiteralExpression e) {
        var rt = e.lt.has() ? e.lt.must()
                : e.resultType.must();
        var r = rt.maybeRefer();
        if (r.has()) {
            return write(e.literal());
        }
        write(e.literal());
        return this;
    }

    private CppGenerator write(CurrentExpression e) {
        assert enterClass != null;
        if (e.isSelf()) return write("this");
        var pd = enterClass.parent().must();
        return write(pd.symbol());
    }

    private CppGenerator derefer(PrimaryExpression e) {
        var t = e.resultType.must();
        write("(*");
        return write("(").write(e).write("))");
    }

    private CppGenerator ofMember(Expression subject, Referable ra) {
        if (ra.refer().none())
            return write(subject).write('.');
        write(subject);
        if (subject instanceof CurrentExpression ce
                && !ce.isSelf()) {
            return write("::");
        }
        return write("->");
    }

    private CppGenerator index(
            PrimaryExpression subject, Expression index) {
        return write(subject).write('[').write(index).write(']');
    }

    private CppGenerator enumMember(
            MemberOfExpression e, EnumDefinition ed) {
        if (EnumDefinition.TokenFieldId.equals(e.member().value()))
            return write(e.subject());
        return enumName(ed).write('[').write(e.subject())
                .write("].").write(e.member());
    }

    private CppGenerator write(MemberOfExpression e) {
        var td = e.subject().resultType.must();
        if (td instanceof EnumTypeDeclarer etd) {
            return enumMember(e, etd.def());
        }

        var dtd = (DerivedTypeDeclarer) td;
        var def = dtd.def();
        if (def instanceof EnumDefinition ed) {
            return enumMember(e, ed);
        }

        ofMember(e.subject(), dtd);
        if (!e.generic().isEmpty()) return unreachable();
        write(e.member());
        return this;
    }

    private CppGenerator write(MethodExpression e) {
        var td = (DerivedTypeDeclarer) e.subject().resultType.must();
        var def = td.def();

        ofMember(e.subject(), td);
        return write(e.method().name()).write(e.generic());
    }

    private CppGenerator write(DefinedType t) {
        return switch (t) {
            case PrimitiveType pt -> write(pt);
            case DerivedType dt -> write(dt);
            case GenericType gt -> write(gt);
            default -> unreachable();
        };
    }

    private CppGenerator fieldInit(
            IdentifierMap<? extends Field> fields,
            ObjectExpression init) {
        joinByComma(fields.values(), f -> {
            var o = init.entries().tryGet(f.name());
            if (o.has()) writeValue(o.get(), f.type());
            else defaultValue(f.type());
        });
        return this;
    }

    private CppGenerator visitNew(
            NewDefinedType ndt, NewExpression e) {
        var def = findType(ndt.type());
        if (def instanceof ClassDefinition cd && !cd.isFinal()) {
            e.arg().use(a -> {
                if (a instanceof ObjectExpression) {
                    write("Feng$newObjectInit");
                } else {
                    write("Feng$newObjectCopy");
                }
            }, () -> {
                write("Feng$newObject");
            });
            write('<').write(ndt.type())
                    .write(">(");
            e.arg().use(a -> {
                if (a instanceof ObjectExpression oe) {
                    fieldInit(cd.allFields(), oe);
                } else {
                    write(a);
                }
            });
            return write(')');
        }

        write("Feng$newMem<");
        write(ndt.type());
        write(">(");
        e.arg().use(this::write, () -> {
            if (def instanceof StructureDefinition)
                write("{}");
        });
        return write(')');
    }

    private CppGenerator visitNew(NewArrayType t, NewExpression e) {
        e.arg().use(a -> {
            if (a instanceof ArrayExpression ae) {
                write("Feng$newArrayInit");
            } else {
                write("Feng$newArrayCopy");
            }
        }, () -> {
            write("Feng$newArray");
        });
        write('<').write(t.element())
                .write(">(").write(t.length());
        e.arg().use(a -> {
            if (!(a instanceof ArrayExpression ae)) {
                write(',').write(a);
                return;
            }
            for (var v : ae.elements()) {
                write(",(").write(t.element()).write(')').write(v);
            }
        });
        return write(')');
    }

    private CppGenerator write(NewExpression e) {
        return switch (e.type()) {
            case NewDefinedType t -> visitNew(t, e);
            case NewArrayType t -> visitNew(t, e);
            case null, default -> unreachable();
        };
    }

    private CppGenerator write(ObjectExpression oe) {
        var dt = oe.dtd();
        write(dt);

        var def = dt.def();
        if (def instanceof ClassDefinition cd) {
            return write('{').fieldInit(cd.allFields(),
                    oe).write('}');
        }

        var sd = (StructureDefinition) def;
        write('{');
        var data = new ArrayList<Groups.G2<Identifier, Expression>>();
        for (var f : sd.fields()) {
            var o = oe.entries().tryGet(f.name());
            if (o.has()) data.add(Groups.g2(f.name(), o.get()));
        }
        joinByComma(data, g -> {
            write('.').write(g.a()).write('=').write(g.b());
        });
        return write('}');
    }

    private CppGenerator write(PairsExpression e) {
        return unsupported("pairs");
    }

    private CppGenerator write(ParenExpression e) {
        write('(');
        write(e.child());
        write(')');
        return this;
    }

    private CppGenerator write(CheckNilExpression e) {
        if (!e.nil()) write('!');
        write(e.subject());
        return write(".absent()");
    }

    private CppGenerator write(SymbolExpression e) {
        write(e.symbol());
        write(e.generic());
        return this;
    }

    private CppGenerator write(UnaryExpression e) {
        var op = e.operator();
        var td = e.resultType.must();
        if (!(td instanceof PrimitiveTypeDeclarer ptd))
            return unreachable();
        var p = ptd.primitive();
        if (p == Primitive.BOOL) {
            if (op != UnaryOperator.INVERT)
                return unreachable();
            write('!');
        } else {
            if (op == UnaryOperator.NEGATIVE)
                write('-');
            else if (op == UnaryOperator.INVERT)
                write('~');
            // ignore +
        }
        write('(').write(e.operand()).write(')');
        return this;
    }

    private CppGenerator write(ConvertExpression e) {
        return write('(').write(e.primitive()).write(')')
                .write(e.operand());
    }

    private CppGenerator write(EnumValueExpression e) {
        return write(e.value().id());
    }

    private CppGenerator write(EnumIdExpression e) {
        var t = e.index().resultType.must();
        return write("Feng$checkIndex(").
                write(e.index()).write(',')
                .write('(').write(t).write(')')
                .write(e.def().size()).write(')');
    }

    private CppGenerator write(ArrayLenExpression e) {
        return write(e.subject()).write(".len");
    }

    private CppGenerator write(ConditionalExpression e) {
        var rt = e.resultType.must();
        write(e.condition()).write('?');
        write('(').write(rt).write(')').write(e.yes());
        write(':');
        write('(').write(rt).write(')').write(e.not());
        return this;
    }

    private CppGenerator write(BlockExpression e) {
        write("({").newLine();
        write(e.block());
        var re = e.result();
        var rt = re.resultType.must();
        writeValue(re, rt).endStmt();
        return write("})");
    }


    //


}
