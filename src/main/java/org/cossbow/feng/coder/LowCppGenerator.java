package org.cossbow.feng.coder;

import org.cossbow.feng.analysis.StackedContext;
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
import org.cossbow.feng.parser.ParseSymbolTable;
import org.cossbow.feng.util.CommonUtil;
import org.cossbow.feng.util.Optional;
import org.cossbow.feng.util.RepeatList;
import org.cossbow.feng.visit.SymbolContext;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.cossbow.feng.ast.dcl.ReferKind.PHANTOM;
import static org.cossbow.feng.util.ErrorUtil.*;

public class LowCppGenerator {
    private final StackedContext context;
    private final Appendable out;
    private final boolean debug;

    public LowCppGenerator(SymbolContext parent,
                           Appendable out,
                           boolean debug) {
        this.context = new StackedContext(parent);
        this.out = out;
        this.debug = debug;
    }

    //

    static final AtomicInteger tmpId = new AtomicInteger();

    static String tmpName() {
        return "Feng$TmpName_" + (tmpId.incrementAndGet());
    }

    static final List<String> headers = List.of(
            "cpp/Header.h"
    );

    private void start() {
        var cl = Thread.currentThread().getContextClassLoader();
        for (String hf : headers) {
            try (var is = cl.getResourceAsStream(hf);
                 var r = new InputStreamReader(Objects.requireNonNull(is));
                 var br = new BufferedReader(r)) {
                String l;
                while ((l = br.readLine()) != null) {
                    write(l).write('\n');
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    private LowCppGenerator write(char c) {
        try {
            out.append(c);
        } catch (IOException e) {
            io(e);
        }
        return this;
    }

    private LowCppGenerator write(CharSequence cs) {
        try {
            out.append(cs);
        } catch (IOException e) {
            io(e);
        }
        return this;
    }

    private LowCppGenerator write(int b) {
        return write(Integer.toString(b));
    }

    private LowCppGenerator write(long b) {
        return write(Long.toString(b));
    }

    private LowCppGenerator write(Identifier name) {
        return write(name.value());
    }

    private LowCppGenerator format(String fmt, Object... args) {
        return write(fmt.formatted(args));
    }

    private static final String COMMA = ", ";

    private int indentValue;

    LowCppGenerator indent() {
        indentValue++;
        return this;
    }

    LowCppGenerator dedent() {
        indentValue--;
        return this;
    }

    LowCppGenerator newLine() {
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

    private String toCType(Primitive p) {
        return switch (p) {
            case INT8 -> "int8_t";
            case INT16 -> "int16_t";
            case INT32 -> "int32_t";
            case INT64, INT -> "int64_t";
            case UINT8, BYTE -> "uint8_t";
            case UINT16 -> "uint16_t";
            case UINT32 -> "uint32_t";
            case UINT64, UINT -> "uint64_t";
            case FLOAT32 -> "float";
            case FLOAT64, FLOAT -> "double";
            case BOOL -> "bool";
            case null -> unreachable();
        };
    }

    private void definePre(Source src) {
        if (debug) write("#define FENG_DEBUG_MEMORY").newLine();

        var ml = src.table().enumList.stream().flatMapToInt(ed ->
                        ed.values().keys().stream().mapToInt(n -> n.value().length()))
                .max();
        ml.ifPresent(l -> {
            write("#define FENG_MAX_ENUM_NAME_LEN ").write(l + 1).newLine();
        });

        write("#define FENG_MAX_CLASS_NUM ").write(ClassDefinition.maxId()).newLine();
        src.table().dagClasses.use(cg -> {
            var ma = cg.all().stream()
                    .mapToInt(d -> d.ancestors().size()).max();
            ma.ifPresent(s -> {
                write("#define FENG_MAX_INHERIT_SIZE ").write(s).newLine();
            });
            var mi = cg.all().stream()
                    .mapToInt(d -> d.allImpls().size()).max();
            mi.ifPresent(s -> {
                write("#define FENG_MAX_IMPLS_SIZE ").write(s).newLine();
            });
        });

/*
        for (var d : TypeDomain.values()) {
            write("#define ").domain(d).write(' ')
                    .write(d.ordinal()).newLine();
        }
        newLine();
*/
    }

    public void write(Source src) {
        if (!src.imports().isEmpty()) {
            unsupported("import");
            return;
        }

        definePre(src);
        start();

        writeComment("type metadata");
        typesMetadata(src);
        writeComment("type declaration");
        for (var t : src.types()) declareType(t);
        writeComment("prototype definition");
        src.table().dagPrototypes.use(dag ->
                dag.bfs(this::writePrototype));
        writeComment("function declaration");
        declareFunctions(src);

        writeComment("global const");
        declareGlobalVar(src.table().dagConst);

        writeComment("string cache");
        literalStringCache(src);
        writeComment("enum definition");
        visitEnums(src);
        writeComment("struct definition");
        visitStructures(src);
        writeComment("class/interface definition");
        src.table().dagClasses.use(this::classRelation);
        src.table().dagInterfaces.use(dag ->
                dag.bfs(this::declareInterface));
        src.table().dagClasses.use(dag ->
                dag.bfs(this::declareClass));
        src.table().dagClasses.use(this::addClassReleasers);
        src.table().dagInterfaces.use(dag ->
                dag.bfs(this::implInterface));
        src.table().dagClasses.use(dag ->
                dag.bfs(this::implClass));
        newLine();

        writeComment("global variable");
        declareGlobalVar(src.table().dagVars);

        writeComment("function definition");
        visitFunctions(src.table());

    }

    private void declareGlobalVar(
            Optional<DAGGraph<GlobalVariable>> vars) {
        vars.use(dag -> {
            dag.bfs(this::write);
            newLine();
        });
        newLine();
    }

    private Optional<ClassDefinition> findClass(TypeDeclarer td) {
        if (td instanceof DerivedTypeDeclarer dtd) {
            var def = findType(dtd.derivedType());
            if (def instanceof ClassDefinition cd) {
                return Optional.of(cd);
            }
        }
        return Optional.empty();
    }

    private TypeDefinition findType(TypeDeclarer td) {
        if (td instanceof DerivedTypeDeclarer dtd) {
            return findType(dtd.derivedType());
        }
        if (td instanceof PrimitiveTypeDeclarer ptd) {
            return ptd.primitive().type();
        }
        return unreachable();
    }

    private TypeDefinition findType(DerivedType dt) {
        return context.findType(dt.symbol()).must();
    }

    private TypeDefinition findType(DefinedType dt) {
        if (dt instanceof PrimitiveType pt) {
            return pt.primitive().type();
        }
        return findType((DerivedType) dt);
    }

    void typesMetadata(Source src) {
        newLine();
        writeComment("define primitives");
        for (var p : Primitive.values()) {
            write("typedef ").write(toCType(p)).write(' ')
                    .write(p).endStmt();
        }
        newLine();
    }

    // 写入全局的字符串常量池：非C-type字符串，没有尾部的'0'
    private void literalStringCache(Source src) {
        var list = src.table().stringCache
                .keySet().stream()
                .sorted(Comparator.comparingInt(StringLiteral::id))
                .toList();
        for (var sl : list) {
            write("static Feng$GlobalArray<Byte,").write(sl.length());
            write("> ").literalString(sl);
            write("= {.$header={.refcnt={1}}, .array = {.values = {");
            for (byte b : sl.value()) {
                write(b).write(',');
            }
            write("}}}").endStmt();
        }
    }

    // 用于引用全局字符串常量
    private LowCppGenerator literalString(StringLiteral sl) {
        return write("Feng$constString_").write(sl.id());
    }

    void declareType(PrototypeDefinition def) {
    }

    void declareType(StructureDefinition def) {
        write(def.domain().name).write(' ');
        write(def.symbol().name());
        endStmt();
    }

    void declareType(ClassDefinition def) {
        write("class ").write(def.symbol().name()).endStmt();
    }

    LowCppGenerator enumName(EnumDefinition ed) {
        return write("Feng$Enum_").write(ed.symbol());
    }

    void declareType(EnumDefinition def) {
    }

    void declareType(InterfaceDefinition def) {
        write("class ").write(def.symbol().name()).endStmt();
    }

    void declareType(TypeDefinition def) {
        switch (def) {
            case StructureDefinition cd -> declareType(cd);
            case ClassDefinition cd -> declareType(cd);
            case EnumDefinition cd -> declareType(cd);
            case PrototypeDefinition cd -> declareType(cd);
            case InterfaceDefinition cd -> declareType(cd);
            case null, default -> unreachable();
        }
        newLine();
    }


    void visitEnums(Source src) {
        for (var ed : src.table().enumList) {
            visitEnum(ed);
        }
    }

    void visitEnum(EnumDefinition ed) {
        write("static Feng$Enum ").enumName(ed).write("[] = {").newLine();
        for (var v : ed.values()) {
            write("{.value=").write(v.val()).write(", .name=");
            write(v.nameLit()).write(".incAR()},").newLine();
        }
        write('}').endStmt().newLine();
    }

    void visitStructures(Source src) {
        var dag = src.table().dagStructures;
        if (dag.none()) return;
        dag.get().bfs(this::write);
        newLine();
    }

    void declareFunctions(Source src) {
        for (var fd : src.table().namedFunctions) {
            declareFunction(fd);
            newLine();
        }
        newLine();
    }

    void cacheReturnFunc(Prototype pt) {
        var nr = pt.returnSet().map(t -> {
            if (!(t instanceof FuncTypeDeclarer ft)) return t;
            var tmpName = tmpName();
            var nt = new TempNameTypeDeclarer(tmpName);
            write("typedef ");
            writePrototype(tmpName, ft.prototype()).endStmt();
            return nt;
        });
        pt.returnSet(nr);
    }

    void declareFunction(FunctionDefinition fd) {
        cacheReturnFunc(fd.procedure().prototype());
        write(fd.symbol().name(), fd.procedure().prototype());
        endStmt();
    }

    void visitFunctions(ParseSymbolTable table) {
        var list = table.namedFunctions.stream()
                .filter(f -> !table.builtinFunctions.exists(f.symbol().name()))
                .toList();
        for (var fd : list) {
            implFunc(fd);
            newLine();
        }
    }

    static final String STATIC = "static";

    private LowCppGenerator write(GlobalVariable v) {
        write(STATIC);
        write(' ');
        return declareVar(v);
    }

    private LowCppGenerator castPtr(
            Expression v, TypeDeclarer t) {
        var rt = v.resultType.must();
        if (t.baseTypeSame(rt)) return write(v);
        if (t instanceof ArrayTypeDeclarer at) {
            if (rt instanceof ArrayTypeDeclarer art) {
                return write("Feng$mapA2A<").write(art.element()).write(',')
                        .write(at.element()).write(">(").write(v).write(')');
            }
            if (rt.isNil()) {
                return write("{}");
            } else {
                return write("Feng$mapU2A<").baseTypeSymbol(rt).write(',')
                        .write(at.element()).write(">(").write(v).write(')');
            }
        }
        if (rt instanceof ArrayTypeDeclarer art) {
            return write("Feng$mapA2U<").write(art.element()).write(',')
                    .baseTypeSymbol(t).write(">(").write(v).write(')');
        }
        return write("((").write(t).write(')')
                .write(v).write(')');
    }

    private LowCppGenerator referPhantom(Expression v, TypeDeclarer t) {
        var vt = v.resultType.must();
        if (vt.maybeRefer().has())
            return castPtr(v, t);

        if (t.baseTypeSame(vt)) {
            if (!(vt instanceof ArrayTypeDeclarer avt))
                return write('&').write(v);

            return write("Feng$refer(").write(v)
                    .write(".values,").write(avt.len())
                    .write(')');
        }

        if (t instanceof ArrayTypeDeclarer at) {
            var avt = (ArrayTypeDeclarer) vt;
            // mappable
            return write("Feng$refer<").write(avt.element())
                    .write(',').write(at.element()).write(">(")
                    .write(v).write(".values,").write(avt.len())
                    .write(')');
        }

        return write("((").write(t).write(')')
                .write('&').write(v).write(')');
    }

    private LowCppGenerator writeLiteral(LiteralExpression v, TypeDeclarer t) {
        if (v.literal() instanceof NilLiteral)
            return castPtr(v, t);

        if (v.literal() instanceof StringLiteral sl) {
            if (t instanceof LiteralTypeDeclarer) {
                return write(sl).write(".incAR()");
            }
            var r = t.maybeRefer();
            if (r.none()) {
                return writeData(sl);
            }
            if (r.get().isKind(PHANTOM)) {
                return write(sl).write(".refer()");
            }
            return write(sl).write(".incAR()");
        }

        return write(v);
    }

    private LowCppGenerator writeValue(Expression v, TypeDeclarer t, boolean move) {
        if (v instanceof LiteralExpression le)
            return writeLiteral(le, t);

        var r = t.maybeRefer();
        if (r.none()) {
            if (findClass(t).none()) return write(v);
            if (move) return write(v);
            if (v.unbound()) return write(v);
            return write(v).write(".Feng$share()");
        }

        if (r.get().isKind(PHANTOM)) {
            return referPhantom(v, t);
        }

        if (move || v.unbound()) {
            return castPtr(v, t);
        }

        if (t instanceof ArrayTypeDeclarer) {
            write("Feng$incAR(");
        } else {
            write("Feng$inc(");
        }
        return castPtr(v, t).write(')');
    }

    private String genName(Variable v) {
        return v.name() + "_" + v.id();
    }

    private LowCppGenerator varName(Variable v) {
        return write(genName(v));
    }

    private LowCppGenerator declareVar(Variable v) {
        var t = v.type().must();
        declare(genName(v), t);
        v.value().use(e -> {
            write(" = ").writeValue(e, t, false);
        }, () -> {
            write(" = {}");
        });
        return endStmt();
    }

    private void declareVars(List<Variable> list) {
        if (list.isEmpty()) return;
        for (var v : list) declareVar(v);
    }

    // type declarer

    private LowCppGenerator write(ArrayTypeDeclarer td) {
        if (td.refer().none()) {
            return write("Feng$Array<").write(td.element()).write(',')
                    .write(td.len()).write('>');
        }
        return write("Feng$ArrayRefer<")
                .write(td.element()).write('>');
    }

    private LowCppGenerator typeRefer(TypeDeclarer td) {
        if (td.maybeRefer().none()) return this;
        return write('*');
    }

    private LowCppGenerator write(DerivedTypeDeclarer td) {
        var def = td.def();
        if (def instanceof EnumDefinition) {
            return write(Primitive.INT);
        }
        return write(td.derivedType()).typeRefer(td);
    }

    private LowCppGenerator write(Primitive p) {
        return write(CommonUtil.upperFirst(p.code));
    }

    private LowCppGenerator write(PrimitiveTypeDeclarer td) {
        return write(td.primitive()).typeRefer(td);
    }

    private LowCppGenerator write(FuncTypeDeclarer e) {
        return unsupported("func");
    }

    private LowCppGenerator write(EnumTypeDeclarer e) {
        return write(Primitive.INT);
    }

    private LowCppGenerator write(VoidTypeDeclarer e) {
        return write("void");
    }

    private LowCppGenerator write(TempNameTypeDeclarer e) {
        return write(e.name);
    }

    private LowCppGenerator write(TypeDeclarer e) {
        return switch (e) {
            case ArrayTypeDeclarer ee -> write(ee);
            case DerivedTypeDeclarer ee -> write(ee);
            case FuncTypeDeclarer ee -> write(ee);
            case PrimitiveTypeDeclarer ee -> write(ee);
            case EnumTypeDeclarer ee -> write(ee);
            case VoidTypeDeclarer ee -> write(ee);
            case TempNameTypeDeclarer ee -> write(ee);
            case null, default -> unreachable();
        };
    }

    private static class TempNameTypeDeclarer extends TypeDeclarer {
        final String name;

        TempNameTypeDeclarer(String name) {
            super(Position.ZERO);
            this.name = name;
        }

        public boolean equals(Object o) {
            return this == o;
        }

        public int hashCode() {
            return name.hashCode();
        }
    }

    //

    private LowCppGenerator write(StructureField sf) {
        write(sf.type());
        write(' ');
        write(sf.name());
        if (sf.bitfield().has()) {
            write(':').write(sf.bits());
        }
        return endStmt();
    }

    private LowCppGenerator write(StructureDefinition sd) {
        write(sd.domain().name).write(' ');
        write(sd.symbol().name());
        write('{').newLine();
        for (var sf : sd.fields()) {
            write(sf);
        }
        write('}').endStmt();
        write("static_assert(sizeof(");
        write(sd.domain().name).write(' ');
        write(sd.symbol().name()).write(") == ");
        write(sd.layout().must().size()).write(')');
        return endStmt();
    }

    // prototype definition

    private void writePrototype(PrototypeDefinition pd) {
        write("typedef ");
        var td = new FuncTypeDeclarer(Position.ZERO, pd.prototype(),
                FuncTypeDeclarer.Type.FUNC);
        declare(pd.symbol().name(), td);
        endStmt();
    }

    // interface definition

    private void writeParents(List<DerivedType> list) {
        if (list.isEmpty()) return;

        write(":");
        var f = true;
        for (var dt : list) {
            if (f) f = false;
            else write(',');
            write("public ").write(dt);
        }
    }

    void declareInterface(InterfaceDefinition id) {
        if (id.builtin()) return;
        write("class ");
        write(id.symbol().name());
        writeParents(id.parts().values());
        write(" {\n").indent();
        write("public:\n");
        for (var im : id.methods()) {
            switchMethod(im);
        }
        write("}").endStmt();
    }

    void implInterface(InterfaceDefinition id) {
        if (id.builtin()) return;
        for (var im : id.methods()) {
            implSwitchMethod(im);
        }
    }

    // class definition

    private volatile ClassDefinition enterClass;
    private volatile ClassMethod enterMethod;

    private void declareClass(ClassDefinition cd) {
        if (cd.builtin()) return;
        assert enterClass == null;
        enterClass = cd;
        write("class ");
        write(cd.symbol().name());
        write(' ');
        cd.inherit().use(dt -> {
            write(": public ").write(dt);
        });
        write("{\n").indent();
        write("public:\n");
        for (var f : cd.fields().values())
            write(f);

        for (var m : cd.methods().values()) {
            declareMethod(m);
            switchMethod(m);
        }

        classCopy(cd);
        classRelease(cd);

        dedent().write("};\n\n");
        enterClass = null;
    }

    private <O extends ObjectDefinition> List<O>
    sortedById(Collection<O> src) {
        var dst = new ArrayList<>(src);
        dst.sort(Comparator.comparingInt(O::id));
        return dst;
    }

    private void classRelation(DAGGraph<ClassDefinition> dag) {
        write("const Feng$ClassRelation Feng$classRelations[FENG_MAX_CLASS_NUM] = {")
                .newLine();

        for (var cd : sortedById(dag.all())) {
            writeComment(cd.toString());
            write('{');

            write(".inheritsSize=").write(cd.ancestors().size());
            write(", .implsSize=").write(cd.allImpls().size());

            write(", .inherits={");
            for (var a : sortedById(cd.ancestors()))
                write(a.id()).write(',');

            write("}, .impls={");
            for (var i : sortedById(cd.allImpls()))
                write(i.id()).write(',');

            write("}},").newLine();
        }
        write("};").newLine();
    }

    private LowCppGenerator thisField(ClassField cf) {
        return write("this->").write(cf.name());
    }

    private void classCopy(ClassDefinition cd) {
        write(cd.symbol()).write("& Feng$share() {").newLine();
        if (cd.inherit().has()) {
            var pdt = cd.inherit().get();
            write(pdt.symbol()).write("::Feng$share()").endStmt();
        }
        for (var cf : cd.fields()) {
            var ft = cf.type();
            if (ft.maybeRefer().has()) {
                if (ft instanceof ArrayTypeDeclarer) {
                    write("Feng$incAR(");
                } else {
                    write("Feng$inc(");
                }
                thisField(cf).write(')').endStmt();
                continue;
            }
            if (findClass(cf.type()).has()) {
                thisField(cf).write(".Feng$share()").endStmt();
            }
        }
        write("return *this").endStmt();
        write('}').newLine();
    }

    private void classRelease(ClassDefinition cd) {
        write(cd.symbol()).write("& Feng$release() {").newLine();
        if (cd.resource()) {
            write("this->release()").endStmt();
        }
        cd.inherit().use(pdt -> {
            var pd = (ClassDefinition) findType(pdt);
            if (pd == ClassDefinition.ObjectClass) return;
            write(pdt.symbol()).write("::Feng$release()").endStmt();
        });
        for (var cf : cd.fields()) {
            var ft = cf.type();
            if (ft.maybeRefer().has()) {
                if (ft instanceof ArrayTypeDeclarer) {
                    write("Feng$decAR(");
                } else {
                    write("Feng$dec(");
                }
                thisField(cf).write(')').endStmt();
                continue;
            }
            if (findClass(cf.type()).has()) {
                thisField(cf).write(".Feng$release()").endStmt();
            }
        }
        write("return *this").endStmt();
        write("}").newLine();
    }

    private void addClassReleasers(DAGGraph<ClassDefinition> dag) {
        write("const Feng$Releaser Feng$Releasers[FENG_MAX_CLASS_NUM] = {")
                .newLine();

        for (var cd : sortedById(dag.all())) {
            writeComment(cd.toString());
            write('[').write(cd.id()).write("] = Feng$releaser<")
                    .write(cd.symbol()).write(">,");
            newLine();
        }
        write("};").newLine();
    }

    private String switchMethodName(String methodName) {
        return "Feng$switch_" + methodName;
    }

    private void switchMethod(Method m) {
        if (m.override().isEmpty()) return;

        write(switchMethodName(m.name().value()), m.prototype());
        endStmt();
    }

    private void implSwitchMethod(ClassMethod m) {
        if (m.override().isEmpty()) return;

        var token = implMethodToken(m.master(),
                switchMethodName(m.name().value()));
        write(token, m.prototype());
        newLine();
        write('{').newLine();
        var args = m.prototype().parameterSet();
        write("switch (((Object *)this)->feng$classId) {").newLine();
        m.seeOverride(or -> {
            var child = or.master();
            write("case ").write(child.id()).write(':').newLine();
            m.prototype().returnSet().use(rt -> {
                write("return (").write(rt).write(')');
            });
            write("((").write(child.symbol()).write("*)this)->");
            write(m.name()).write('(').writeArgs(args).write(");");
            if (m.prototype().returnSet().none()) write("break;");
            newLine();
        });

        write("default:").newLine();
        if (m.prototype().returnSet().has()) write("return ");
        write(" this->").write(m.name());
        write('(').writeArgs(args);
        write(')').endStmt();

        write('}').newLine();
        write('}').newLine();
    }

    private void implSwitchMethod(InterfaceMethod m) {
        if (m.override().isEmpty()) return;

        var token = implMethodToken(m.master(),
                switchMethodName(m.name().value()));
        write(token, m.prototype());
        newLine();
        write('{').newLine();
        var args = m.prototype().parameterSet();
        write("switch (((Object *)this)->feng$classId) {").newLine();
        for (var cd : m.master.must().impls) {
            write("case ").write(cd.id()).write(':').newLine();
            m.prototype().returnSet().use(rt -> {
                write("return (").write(rt).write(')');
            });
            write("((").write(cd.symbol()).write("*)this)->");
            var cm = cd.allMethods().get(m.name());
            write(m.name()).write('(').writeArgs(args).write(");");
            if (m.prototype().returnSet().none()) write("break;");
            newLine();
        }
        write("default:").newLine();
        write("throw Unreachable()").endStmt();

        write('}').newLine();
        write('}').newLine();
    }

    private void declareMethod(Method cm) {
        cacheReturnFunc(cm.prototype());
        write(cm.name(), cm.prototype()).endStmt();
    }

    private void implClass(ClassDefinition cd) {
        if (cd.builtin()) return;
        assert enterClass == null;
        enterClass = cd;
        for (var cm : cd.methods()) {
            implMethod(cm);
            implSwitchMethod(cm);
        }
        enterClass = null;
    }

    private String implMethodToken(TypeDefinition def, String name) {
        return def.symbol() + "::" + name;
    }

    private void implMethod(ClassMethod cm) {
        assert enterClass != null;
        assert enterMethod == null;
        enterMethod = cm;
        write(implMethodToken(enterClass, cm.name().value()), cm.prototype());
        newLine();
        write(cm.procedure().must());
        enterMethod = null;
    }

    private LowCppGenerator write(Symbol s) {
        if (s.module().has()) {
            write(s.module().get());
            write('$');
        }
        write(s.name());
        return this;
    }


    private LowCppGenerator write(DerivedType dt) {
        write(dt.symbol());
        if (!dt.generic().isEmpty())
            unsupported("泛型未实现");
        return this;
    }

    private LowCppGenerator baseTypeSymbol(TypeDeclarer td) {
        if (td instanceof PrimitiveTypeDeclarer ptd) {
            return write(ptd.primitive());
        } else if (td instanceof DerivedTypeDeclarer dtd) {
            return write(dtd.derivedType());
        }
        return unreachable();
    }

    private LowCppGenerator write(PrimitiveType dt) {
        return write(dt.primitive());
    }

    private LowCppGenerator declare(String name, TypeDeclarer td) {
        if (td instanceof FuncTypeDeclarer ftd) {
            writePrototype(name, ftd.prototype());
        } else {
            write(td).write(' ').write(name);
        }
        return this;
    }

    private LowCppGenerator declare(Identifier name, TypeDeclarer td) {
        return declare(name.value(), td);
    }

    private LowCppGenerator write(ClassField cf) {
        assert enterClass != null;
        return declare(cf.name(), cf.type()).endStmt();
    }

    private volatile FunctionDefinition enterFunc;

    LowCppGenerator write(ParameterSet ps) {
        var first = true;
        for (var a : ps.variables()) {
            if (first) first = false;
            else write(COMMA);
            declare(genName(a), a.type().must());
        }
        return this;
    }

    LowCppGenerator writeArgs(ParameterSet ps) {
        var first = true;
        for (var v : ps.variables()) {
            if (first) first = false;
            else write(COMMA);
            varName(v);
        }
        return this;
    }

    private LowCppGenerator writePrototype(String name, Prototype pt) {
        var ps = pt.parameterSet();
        pt.returnSet().use(this::write, () -> write("void"));
        write('(').write('*').write(name).write(')');
        write('(').write(ps).write(')');
        return this;
    }

    private LowCppGenerator write(String name, Prototype pt) {
        var ps = pt.parameterSet();
        pt.returnSet().use(this::write, () -> write("void"));
        write(' ').write(name);
        write('(').write(ps).write(')');
        return this;
    }

    private LowCppGenerator write(Identifier name, Prototype prototype) {
        return write(name.value(), prototype);
    }

    private void implFunc(FunctionDefinition fd) {
        assert enterFunc == null;
        enterFunc = fd;
        var proc = fd.procedure();
        write(fd.symbol().name(), proc.prototype());
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
        writeComment("release and exit scope");
        for (var v : s.stack().reversed())
            release(v);
    }

    private void write(List<Statement> list) {
        for (var s : list) write(s);
    }

    private LowCppGenerator write(Statement e) {
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

    private LowCppGenerator write(ForStatement e) {
        return switch (e) {
            case ConditionalForStatement ee -> write(ee);
            case IterableForStatement ee -> write(ee);
            case null, default -> unreachable();
        };
    }

    private LowCppGenerator write(BlockStatement bs) {
        if (bs.newScope()) write('{').newLine();

        write(bs.list());

        if (bs.newScope()) {
            if (noTerminal(bs.list())) exitScope(bs);
            dedent().write('}').newLine();
        }
        return this;
    }

    private LowCppGenerator endStmt() {
        return write(";").newLine();
    }

    private LowCppGenerator release(Variable v) {
        var t = v.type().must();
        var r = t.maybeRefer();
        if (r.has()) {
            if (r.get().isKind(PHANTOM)) return this;
            if (t instanceof ArrayTypeDeclarer) {
                write("Feng$decAR(");
            } else {
                write("Feng$dec(");
            }
            return varName(v).write(')').endStmt();
        }
        if (t instanceof ArrayTypeDeclarer) {
            return varName(v).write(".Feng$release()").endStmt();
        }
        var cdo = findClass(t);
        if (cdo.none()) return this;
        return varName(v).write(".Feng$release()").endStmt();
    }

    private LowCppGenerator release(List<Variable> list, int expectId) {
        for (var v : list.reversed()) {
            if (v.id() == expectId) continue;
            release(v);
        }
        return this;
    }

    private LowCppGenerator release(List<Variable> list) {
        return release(list, -1);
    }

    private LowCppGenerator release(
            List<Variable> local, Expression re,
            TypeDeclarer rt, String retTag) {
        if (re instanceof VariableExpression ve) {
            if (local.contains(ve.variable())) {
                release(local, ve.variable().id());
                return write(retTag).writeValue(re, rt, true)
                        .endStmt();
            }
        }
        if (re instanceof IndexOfExpression ||
                re instanceof MemberOfExpression ||
                re instanceof BlockExpression) {
            write(rt).write(" feng$tmp_result = ")
                    .writeValue(re, rt, false)
                    .endStmt();
            release(local);
            return write(retTag).write("feng$tmp_result")
                    .endStmt();
        }

        release(local);
        return write(retTag).writeValue(re, rt, false)
                .endStmt();
    }

    private LowCppGenerator write(ReturnStatement rs) {
        writeComment("release and return");
        if (rs.result().none()) {
            return release(rs.local()).write("return").endStmt();
        }

        var re = rs.result().get();
        var prot = rs.procedure().must().prototype();
        var rt = prot.returnSet().must();
        return release(rs.local(), re, rt, "return ");
    }

    private LowCppGenerator write(DeclarationStatement ds) {
        ds.variables().forEach(this::declareVar);
        return this;
    }

    private LowCppGenerator write(Operand e) {
        switch (e) {
            case IndexOperand ee -> write(ee);
            case FieldOperand ee -> write(ee);
            case VariableOperand ee -> write(ee);
            case DereferOperand ee -> write(ee);
            default -> unreachable();
        }
        return this;
    }

    private LowCppGenerator write(VariableOperand e) {
        varName(e.variable().must());
        return this;
    }

    private LowCppGenerator write(IndexOperand e) {
        return index(e.subject(), e.index());
    }

    private LowCppGenerator write(FieldOperand e) {
        var td = (DerivedTypeDeclarer) e.subject().resultType.must();
        return ofMember(e.subject(), td).write(e.field());
    }

    private LowCppGenerator write(DereferOperand e) {
        return derefer(e.subject());
    }

    private LowCppGenerator write(AssignmentsStatement as) {
        for (var a : as.list()) {
            var e = a.operand();
            if (!e.relay().isEmpty()) {
                write('{').newLine();
                declareVars(e.relay());
            }
            writeAssign(a.operand(), a.value()).endStmt();
            if (!e.relay().isEmpty()) {
                release(e.relay());
                write('}').newLine();
            }
        }
        return this;
    }

    private LowCppGenerator write(BreakStatement e) {
        if (e.label().has()) return unsupported("break label");
        write("break;\n");
        return this;
    }

    private LowCppGenerator write(CallStatement e) {
        if (e.replace().has()) {
            write(e.replace().must());
        } else {
            write((Expression) e.call()).endStmt();
        }
        return this;
    }

    private LowCppGenerator writeAssign(Operand o, Expression v) {
        var t = o.type.must();
        var r = t.maybeRefer();
        if (r.has()) {
            if (r.get().isKind(PHANTOM)) {
                return write(o).write(" = ").castPtr(v, t);
            }
            if (t instanceof ArrayTypeDeclarer) {
                write("Feng$decAR(");
            } else {
                write("Feng$dec(");
            }
            write(o).write(") = ");
            if (v.unbound()) return write(v);
            if (t instanceof ArrayTypeDeclarer) {
                write("Feng$incAR(");
            } else {
                write("Feng$inc(");
            }
            return castPtr(v, t).write(')');
        }

        var cd = findClass(t);
        if (cd.none()) {
            return write(o).write(" = ").write(v);
        }

        write(o).write(".Feng$release() = ").write(v);
        if (v.unbound()) return this;
        return write(".Feng$share()");
    }


    private LowCppGenerator write(ConditionalForStatement fs) {
        if (fs.initializer().none()) {
            write("while(");
            write(fs.condition());
            write(')');
            write(fs.body());
        } else {
            write('{');
            write(fs.initializer().must());
            write("for(");
            write(';');
            write(fs.condition());
            write(';');
            write(')');
            write('{');
            write(fs.body());
            write(fs.updater().must());
            write('}');
            exitScope(fs);
            write('}');
        }
        return this;
    }

    private LowCppGenerator write(IterableForStatement s) {
        return write(s.replace.must());
    }

    private LowCppGenerator write(ContinueStatement s) {
        write("continue ");
        s.label().use(this::write);
        return endStmt();
    }

    private LowCppGenerator write(GotoStatement e) {
        return unsupported("goto");
    }

    private LowCppGenerator write(IfStatement is) {
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

    private LowCppGenerator write(LabeledStatement s) {
        write(s.label());
        write(':');
        return write(s.target());
    }

    private LowCppGenerator write(SwitchStatement ss) {
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

    private LowCppGenerator write(Branch e) {
        write((Statement) e.body());
        return this;
    }

    private LowCppGenerator write(ThrowStatement e) {
        return unsupported("throw");
    }

    private LowCppGenerator write(TryStatement e) {
        return unsupported("try..catch");
    }

    // expression

    private LowCppGenerator write(Expression e) {
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
            case BlockExpression ee -> write(ee);
            default -> unreachable();
        };
    }

    private LowCppGenerator writeValues(
            List<Expression> values, List<TypeDeclarer> dstTypes) {
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) write(COMMA);
            writeValue(values.get(i), dstTypes.get(i), false);
        }
        return this;
    }

    private LowCppGenerator writePow(Expression left, Expression right) {
        return unsupported("幂运算");
    }

    private LowCppGenerator write(BinaryExpression e) {
        var op = e.operator();
        if (op == BinaryOperator.POW)
            return writePow(e.left(), e.right());

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

    private LowCppGenerator write(ReferEqualExpression e) {
        write(e.left());
        var lt = e.left().resultType.must();
        if (lt instanceof ArrayTypeDeclarer) write(".start");
        write(e.same() ? "==" : "!=");
        write(e.right());
        var rt = e.right().resultType.must();
        if (rt instanceof ArrayTypeDeclarer) write(".start");
        return this;
    }

    private LowCppGenerator write(ArrayExpression e) {
        e.type().use(this::write, () -> {
            e.lt.use(this::write);
        });
        write("{.values = {");
        var t = (ArrayTypeDeclarer) e.resultType.must();
        var types = new RepeatList<>(
                t.element(), e.size());
        writeValues(e.elements(), types);
        write("}}");
        return this;
    }

    private LowCppGenerator write(AssertExpression e) {
        if (!e.needCheck())
            return castPtr(e.subject(), e.type());

        var dst = (ObjectDefinition) findType(e.type());
        if (dst instanceof ClassDefinition) {
            write("Feng$inherit");
        } else {
            write("Feng$impl");
        }
        return write('<').write(dst.symbol()).write(">(")
                .write(e.subject()).write(", ")
                .write(dst.id()).write(')');
    }

    private LowCppGenerator write(CallExpression e) {
        write(e.callee());
        return write('(').writeValues(e.arguments(), e.prototype()
                .must().parameterSet().types()).write(')');
    }

    private LowCppGenerator write(IndexOfExpression e) {
        return index(e.subject(), e.index());
    }

    private LowCppGenerator write(VariableExpression e) {
        return varName(e.variable());
    }

    private LowCppGenerator write(DereferExpression e) {
        return derefer(e.subject());
    }

    private LowCppGenerator write(LambdaExpression e) {
        return unsupported("尼玛函数");
    }

    private LowCppGenerator write(Literal e) {
        return switch (e) {
            case BoolLiteral ee -> write(ee);
            case FloatLiteral ee -> write(ee);
            case IntegerLiteral ee -> write(ee);
            case NilLiteral ee -> write(ee);
            case StringLiteral ee -> write(ee);
            case null, default -> unreachable();
        };
    }

    private LowCppGenerator write(BoolLiteral e) {
        write(e.value() ? "true" : "false");
        return this;
    }

    private LowCppGenerator write(FloatLiteral e) {
        write(e.value().toString());
        return this;
    }

    private LowCppGenerator write(IntegerLiteral e) {
        write(e.value().toString(e.radix()));
        return this;
    }

    private LowCppGenerator write(NilLiteral e) {
        write("nullptr");
        return this;
    }

    private LowCppGenerator writeData(StringLiteral e) {
        write("{.values={");
        for (byte b : e.value()) {
            write(b).write(',');
        }
        return write("}}");
    }

    private LowCppGenerator write(StringLiteral e) {
        return literalString(e);
    }

    private LowCppGenerator write(LiteralExpression e) {
        var rt = e.lt.has() ? e.lt.must()
                : e.resultType.must();
        if (rt instanceof ArrayTypeDeclarer atd
                && atd.refer().has()) {
            return write("{}");
        }
        write(e.literal());
        return this;
    }

    private LowCppGenerator write(CurrentExpression e) {
        assert enterClass != null;
        if (e.isSelf()) return write("this");
        var pd = enterClass.parent().must();
        return write(pd.symbol());
    }

    private LowCppGenerator derefer(PrimaryExpression e) {
        var t = e.resultType.must();
        write("(*");
        if (!t.maybeRefer().must().required())
            write("Feng$required");
        return write("(").write(e).write("))");
    }

    private LowCppGenerator ofMember(Expression subject, Referable ra) {
        if (ra.refer().none())
            return write(subject).write('.');
        var ref = ra.refer().get();
        if (ref.required()) {
            write(subject);
            if (subject instanceof CurrentExpression ce
                    && !ce.isSelf()) {
                return write("::");
            }
            return write("->");
        }
        return write("Feng$required(").write(subject).write(")->");
    }

    private LowCppGenerator index(
            PrimaryExpression subject, Expression index) {
        var t = subject.resultType.must();
        var r = t.maybeRefer();
        if (r.none() || r.get().required()) {
            write(subject);
        } else {
            write("Feng$required(").write(subject).write(')');
        }
        return write('[').write(index).write(']');
    }

    private LowCppGenerator enumMember(
            EnumValueExpression s, EnumDefinition ed, Identifier m) {
        // TODO: check bounds
        if (EnumDefinition.TokenFieldId.equals(m.value()))
            return write(s);
        return enumName(ed).write('[').write(s)
                .write("].").write(m);
    }

    private LowCppGenerator callMethod(TypeDefinition def, Identifier member) {
        if (def instanceof ClassDefinition cd) {
            var cm = cd.allMethods().tryGet(member);
            if (cm.match(m -> !m.override().isEmpty())) {
                write(switchMethodName(member.value()));
                return this;
            }
        } else if (def instanceof InterfaceDefinition id) {
            return write(switchMethodName(member.value()));
        }
        return write(member);
    }

    private LowCppGenerator write(MemberOfExpression e) {
        var td = e.subject().resultType.must();
        if (td instanceof EnumTypeDeclarer etd) {
            return enumMember((EnumValueExpression) e.subject(), etd.def(), e.member());
        }

        var dtd = (DerivedTypeDeclarer) td;
        var def = dtd.def();

        ofMember(e.subject(), dtd);
        if (!e.generic().isEmpty()) return unreachable();
        write(e.member());
        return this;
    }

    private LowCppGenerator write(MethodExpression e) {
        var td = (DerivedTypeDeclarer) e.subject().resultType.must();
        var def = td.def();

        ofMember(e.subject(), td);
        if (!e.generic().isEmpty()) return unsupported("泛型");
        return callMethod(def, e.method().name());
    }

    private LowCppGenerator fieldInit(
            TypeDefinition def, Optional<Expression> init) {
        write('(').write(def.symbol()).write(')');
        if (init.has()) {
            return write(init.get());
        } else {
            return write("{}");
        }
    }

    private LowCppGenerator visitNew(NewDefinedType dt, NewExpression e) {
        var def = findType(dt.type());
        if (def instanceof ClassDefinition cd) {
            write("Feng$newObject<").write(cd.symbol())
                    .write(">(");
            if (!cd.isFinal()) write(cd.id()).write(',');
            return fieldInit(cd, e.arg()).write(')');
        }

        write("Feng$newMem<");
        if (def instanceof PrimitiveDefinition pd) {
            write(pd.primitive());
        } else {
            write(def.symbol());
        }
        write(">(");
        if (def instanceof StructureDefinition) {
            return fieldInit(def, e.arg()).write(')');
        }
        if (e.arg().has()) {
            return write(e.arg().get()).write(')');
        } else {
            return write('0').write(')');
        }
    }

    private LowCppGenerator visitNew(NewArrayType t, NewExpression e) {
        write("Feng$newArray<").write(t.element())
                .write(">(").write(t.length());
        e.arg().use(a -> {
            if (!(a instanceof ArrayExpression ae)) {
                write(',').write(a);
                return;
            }
            write(",(Feng$Array<").write(t.element())
                    .write(',').write(ae.size())
                    .write(">)").write(a);
        });
        return write(')');
    }

    private LowCppGenerator write(NewExpression e) {
        return switch (e.type()) {
            case NewDefinedType t -> visitNew(t, e);
            case NewArrayType t -> visitNew(t, e);
            case null, default -> unreachable();
        };
    }

    private boolean objectInit(
            IdentifierTable<Expression> entries,
            Iterator<List<Field>> stack) {
        if (!stack.hasNext()) return false;
        var fields = stack.next();
        write('{');
        if (objectInit(entries, stack)) write(',');
        boolean first = true;
        for (var f : fields) {
            if (first) first = false;
            else write(',');
            var v = entries.get(f.name());
            write('.').write(f.name()).write('=')
                    .writeValue(v, f.type(), false);
        }
        write('}');
        return true;
    }

    private LowCppGenerator write(ObjectExpression oe) {
        if (oe.initStack.isEmpty())
            return write("{}");

        objectInit(oe.entries(), oe.initStack.iterator());

        return this;
    }

    private LowCppGenerator write(PairsExpression e) {
        return unsupported("pairs");
    }

    private LowCppGenerator write(ParenExpression e) {
        write('(');
        write(e.child());
        write(')');
        return this;
    }

    private LowCppGenerator write(CheckNilExpression e) {
        var st = e.subject().resultType.must();
        write(e.subject());
        if (st instanceof ArrayTypeDeclarer)
            write(".start");
        write(e.nil() ? '=' : '!');
        return write("=nullptr");
    }

    private LowCppGenerator write(SymbolExpression e) {
        if (!e.generic().isEmpty()) return unsupported("generic");
        var t = e.resultType.must();
        write(e.symbol());
        return this;
    }

    private LowCppGenerator write(UnaryExpression e) {
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

    private LowCppGenerator write(ConvertExpression e) {
        return write('(').write(e.primitive()).write(')')
                .write(e.operand());
    }

    private LowCppGenerator write(EnumValueExpression e) {
        return write(e.value().id());
    }

    private LowCppGenerator write(EnumIdExpression e) {
        var t = e.index().resultType.must();
        return write("Feng$checkIndex(").
                write(e.index()).write(',')
                .write('(').write(t).write(')')
                .write(e.def().size()).write(')');
    }

    private LowCppGenerator write(ArrayLenExpression e) {
        return write(e.subject()).write(".len");
    }

    private LowCppGenerator write(BlockExpression e) {
        write("({").newLine();
        write(e.block());
        var re = e.result();
        var rt = re.resultType.must();
        return release(e.stack(), re, rt, "").write("})");
    }


    //


}
