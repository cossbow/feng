package org.cossbow.feng.coder;

import org.cossbow.feng.analysis.StackedContext;
import org.cossbow.feng.ast.*;
import org.cossbow.feng.ast.dcl.*;
import org.cossbow.feng.ast.expr.*;
import org.cossbow.feng.ast.gen.DefinedType;
import org.cossbow.feng.ast.gen.DerivedType;
import org.cossbow.feng.ast.gen.PrimitiveType;
import org.cossbow.feng.ast.lit.*;
import org.cossbow.feng.ast.oop.ClassDefinition;
import org.cossbow.feng.ast.oop.ClassField;
import org.cossbow.feng.ast.oop.ClassMethod;
import org.cossbow.feng.ast.oop.InterfaceDefinition;
import org.cossbow.feng.ast.proc.*;
import org.cossbow.feng.ast.stmt.*;
import org.cossbow.feng.ast.struct.StructureDefinition;
import org.cossbow.feng.ast.struct.StructureField;
import org.cossbow.feng.ast.var.FieldOperand;
import org.cossbow.feng.ast.var.IndexOperand;
import org.cossbow.feng.ast.var.Operand;
import org.cossbow.feng.ast.var.VariableOperand;
import org.cossbow.feng.dag.DAGGraph;
import org.cossbow.feng.parser.ParseSymbolTable;
import org.cossbow.feng.util.Optional;
import org.cossbow.feng.visit.SymbolContext;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import static org.cossbow.feng.ast.dcl.ReferKind.PHANTOM;
import static org.cossbow.feng.util.ErrorUtil.*;

public class CppGenerator {
    private final ParseSymbolTable table;
    private final StackedContext context;
    private final Appendable out;
    private final boolean debug;

    public CppGenerator(ParseSymbolTable table,
                        SymbolContext parent,
                        Appendable out,
                        boolean debug) {
        this.table = table;
        this.context = new StackedContext(parent);
        this.out = out;
        this.debug = debug;
    }

    static final List<String> headers = List.of(
            "cpp/Header.h"
    );

    private void start() {
        var cl = Thread.currentThread().getContextClassLoader();
        for (String hf : headers) {
            try (var is = cl.getResourceAsStream(hf);
                 var r = new InputStreamReader(is);
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

    private CppGenerator write(boolean b) {
        return write(Boolean.toString(b));
    }

    private CppGenerator write(int b) {
        return write(Integer.toString(b));
    }

    private CppGenerator write(Identifier name) {
        return write(name.value());
    }

    private CppGenerator format(String fmt, Object... args) {
        return write(fmt.formatted(args));
    }

    public static final String COMMA = ", ";

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

    private String toCType(Primitive p) {
        return switch (p) {
            case INT8, BYTE -> "int8_t";
            case INT16 -> "int16_t";
            case INT32 -> "int32_t";
            case INT64, INT -> "int64_t";
            case UINT8 -> "uint8_t";
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

        var max = src.table().dagClasses.must().all().stream().mapToInt(d ->
                d.ancestors().size()).max().getAsInt();
        write("#define FENG_MAX_INHERIT_SIZE ").write(max)
                .newLine().newLine();
        for (var d : TypeDomain.values()) {
            write("#define ").domain(d).write(' ')
                    .write(d.ordinal()).newLine();
        }
        newLine();
    }

    public CppGenerator visit(Source src) {
        if (!src.imports().isEmpty()) return unsupported("import");

        definePre(src);
        start();

        writeComment("type metadata");
        typesMetadata(src);
        writeComment("type declaration");
        for (var t : src.types()) declareType(t);
        writeComment("function definition");
        declareFunctions(src);

        writeComment("global variable");
        declareGlobalVar(src);

        writeComment("enum definition");
        visitEnums(src);
        writeComment("struct definition");
        visitStructures(src);
        writeComment("class definition");
        visitClasses(src);
        newLine();

        writeComment("function definition");
        visitFunctions(src.table());

        return this;
    }

    void declareGlobalVar(Source src) {
        src.table().dagVars.use(dag -> {
            dag.bfs(this::visit);
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

    private CppGenerator domain(TypeDomain d) {
        return write("DOMAIN_").write(d.name());
    }

    private CppGenerator typeId(TypeDefinition def) {
        write("TypeId_");
        if (def instanceof PrimitiveDefinition pd) {
            return write(pd.primitive());
        }
        return visit(def.symbol());
    }

    void typesMetadata(Source src) {
        newLine();
        writeComment("define primitives");
        for (var p : Primitive.values()) {
            write("typedef ").write(toCType(p)).write(' ')
                    .write(p.name()).endStmt();
        }
        newLine();

        writeComment("define TypeId");

        write("enum TypeId {").newLine();
        for (var p : Primitive.values()) {
            var def = p.type();
            typeId(def).write('=').write(def.typeId())
                    .write(',').newLine();
        }
        var sorted = src.types().stream()
                .sorted(Comparator.comparingInt(TypeDefinition::typeId))
                .toList();
        for (var def : sorted) {
            typeId(def).write('=').write(def.typeId())
                    .write(',').newLine();
        }
        write('}').endStmt();
    }

    void declareType(PrototypeDefinition def) {
    }

    void declareType(StructureDefinition def) {
        write("struct ");
        visit(def.symbol().name());
        endStmt();
    }

    void declareType(ClassDefinition def) {
        write("class ");
        visit(def.symbol().name());
        endStmt();
    }

    void declareType(EnumDefinition def) {
    }

    void declareType(InterfaceDefinition def) {
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
            // TODO
        }
    }

    void visitStructures(Source src) {
        var dag = src.table().dagStructures;
        if (dag.none()) return;
        dag.get().bfs(this::visit);
    }

    void visitClasses(Source src) {
        var o = src.table().dagClasses;
        if (o.none()) return;
        var dag = o.get();
        classInherit(dag);
        dag.bfs(this::declareClass);
        dag.bfs(this::implClass);
    }

    void declareFunctions(Source src) {
        for (var fd : src.table().namedFunctions) {
            declareFunction(fd);
            newLine();
        }
        newLine();
    }

    void declareFunction(FunctionDefinition fd) {
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

    public CppGenerator visit(GlobalVariable v) {
        write(STATIC);
        write(' ');
        return declareVar(v);
    }

    private CppGenerator writeValue(Expression v, TypeDeclarer t, boolean move) {
        var r = t.maybeRefer();
        if (r.none()) {
            if (findClass(t).none()) return visit(v);
            if (move) return visit(v);
            if (v.unbound()) return visit(v);
            return visit(v).write(".Feng$share()");
        }
        if (r.get().isKind(PHANTOM)) {
            return visit(v);
        }
        if (move || v.unbound()) {
            return visit(v);
        }

        return write("Feng$inc(").visit(v).write(')');
    }

    private CppGenerator varName(Variable v) {
        return write(v.name()).write('_').write(v.id());
    }

    private CppGenerator write(Variable v) {
        return write(v.type().must()).write(' ').
                varName(v).write(" = ").
                writeValue(v.requireValue(), v.type().must(), false);
    }

    // type declarer

    public CppGenerator write(ArrayTypeDeclarer td) {
        unsupported("array");

        write("struct Array_");
        write(td.element());
        if (td.length().has()) {
            write('[');
            format("%d", td.len());
            write(']');
        } else {
            unsupported("array reference");
        }

        return this;
    }

    private CppGenerator writeRefer(TypeDeclarer td) {
        if (td.maybeRefer().none()) return this;
        return write('*');
    }

    public CppGenerator write(DerivedTypeDeclarer td) {
        return visit(td.derivedType()).writeRefer(td);
    }


    public CppGenerator write(Primitive p) {
        return write(p.name());
    }

    public CppGenerator write(PrimitiveTypeDeclarer td) {
        return write(td.primitive()).writeRefer(td);
    }

    public CppGenerator write(FuncTypeDeclarer e) {
        return unreachable();
    }

    public CppGenerator write(ConvertorTypeDeclarer e) {
        return write('(').write(e.primitive()).write(')');
    }

    public CppGenerator write(LiteralTypeDeclarer e) {
        return visit(e.literal());
    }

    private CppGenerator write(TypeDeclarer e) {
        return switch (e) {
            case ArrayTypeDeclarer ee -> write(ee);
            case DerivedTypeDeclarer ee -> write(ee);
            case FuncTypeDeclarer ee -> write(ee);
            case ConvertorTypeDeclarer ee -> write(ee);
            case PrimitiveTypeDeclarer ee -> write(ee);
            case LiteralTypeDeclarer ee -> write(ee);
            case null, default -> unreachable();
        };
    }

    //

    public CppGenerator visit(StructureField sf) {
        write(sf.type());
        write(' ');
        visit(sf.name());
        if (sf.bitfield().has()) {
            write(':');
            format("%d", sf.bits());
        }
        endStmt();
        return this;
    }

    public CppGenerator visit(StructureDefinition sd) {
        write("struct ");
        visit(sd.symbol().name());
        write(" {\n");
        for (var sf : sd.fields()) {
            visit(sf);
        }
        write("};\n");
        return this;
    }


    // class definition

    private volatile ClassDefinition enterClass;
    private volatile ClassMethod enterMethod;

    public void declareClass(ClassDefinition cd) {
        assert enterClass == null;
        enterClass = cd;
        write("class ");
        visit(cd.symbol().name());
        write(' ');
        cd.parent().use(pd -> {
            write(": public ").visit(pd.symbol()).write(' ');
        });
        write("{\n").indent();
        write("public:\n");
        for (var f : cd.fields().values())
            visit(f);

        for (var m : cd.methods().values()) {
            declareMethod(m);
            switchMethod(m);
        }

        classCopy(cd);
        classRelease(cd);

        dedent().write("};\n\n");
        enterClass = null;
    }

    private void classInherit(DAGGraph<ClassDefinition> dag) {
        write("const Feng$SortedTable Feng$inheritTable[] = {").newLine();
        var list = dag.all().stream()
                .sorted(Comparator.comparingInt(TypeDefinition::typeId))
                .toList();
        for (var cd : list) {
            write("[").typeId(cd).write("] = {");
            write(".size=").write(cd.ancestors().size()).write(", .table={");
            for (var a : cd.ancestors())
                typeId(a).write(',');
            write("}},").newLine();
        }
        write("};").newLine();
    }

    private CppGenerator thisField(ClassField cf) {
        return write("this->").visit(cf.name());
    }

    private void classCopy(ClassDefinition cd) {
        visit(cd.symbol()).write("& Feng$share() {").newLine();
        if (cd.inherit().has()) {
            var pdt = cd.inherit().get();
            visit(pdt.symbol()).write("::Feng$share()").endStmt();
        }
        for (var cf : cd.fields()) {
            if (cf.type().maybeRefer().has()) {
                write("Feng$inc(").thisField(cf).write(')').endStmt();
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
        visit(cd.symbol()).write("& Feng$release() {").newLine();
        if (cd.resource()) {
            write("this->release()").endStmt();
        }
        if (cd.inherit().has()) {
            var pdt = cd.inherit().get();
            visit(pdt.symbol()).write("::Feng$release()").endStmt();
        }
        for (var cf : cd.fields()) {
            if (cf.type().maybeRefer().has()) {
                write("Feng$dec(").thisField(cf).write(')').endStmt();
                continue;
            }
            if (findClass(cf.type()).has()) {
                thisField(cf).write(".Feng$release()").endStmt();
            }
        }
        write("return *this").endStmt();
        write("}").newLine();
    }

    private String switchMethodName(String methodName) {
        return "Feng$switch_" + methodName;
    }

    private void switchMethod(ClassMethod cm) {
        if (cm.override().isEmpty()) return;
        write(switchMethodName(cm.name().value()), cm.prototype());
        endStmt();
    }

    private void implSwitchMethod(ClassMethod cm) {
        if (cm.override().isEmpty()) return;
        var token = implMethodToken(cm.master().must(),
                switchMethodName(cm.name().value()));
        write(token, cm.prototype());
        newLine();
        write('{').newLine();
        var hasReturn = cm.prototype().returnSet().has();
        var args = cm.prototype().parameterSet();
        write("switch (Feng$typeId(this)) {").newLine();
        cm.seeOverride(or -> {
            var child = or.master().must();
            write("case ").typeId(child).write(':').newLine();
            if (hasReturn) write("return ");
            write("((").visit(child.symbol()).write("*)this)->");
            if (or.override().isEmpty()) {
                visit(cm.name());
            } else {
                write(switchMethodName(cm.name().value()));
            }
            write('(').writeArgs(args).write(");");
            if (!hasReturn) write("break;");
            newLine();
        });
        write("default:").newLine();
        if (hasReturn) write("return ");
        write(" this->").write(cm.name());
        write('(').writeArgs(args).write(");");
        newLine();

        write('}').newLine();
        write('}').newLine();
    }

    public void declareMethod(Method cm) {
        write(cm.name(), cm.prototype()).endStmt();
    }

    public void implClass(ClassDefinition cd) {
        assert enterClass == null;
        enterClass = cd;
        for (var cm : cd.methods()) {
            implMethod(cm);
            implSwitchMethod(cm);
        }
        enterClass = null;
    }

    private String implMethodToken(ClassDefinition cd, String name) {
        return cd.symbol() + "::" + name;
    }

    public void implMethod(ClassMethod cm) {
        assert enterClass != null;
        assert enterMethod == null;
        enterMethod = cm;
        assert enterFunc == null;
        var fd = cm.func();
        enterFunc = fd;
        write(implMethodToken(enterClass, cm.name().value()), cm.prototype());
        newLine();
        write(fd.procedure());
        enterFunc = null;
        enterMethod = null;
    }

    public CppGenerator visit(Identifier id) {
        return write(id);
    }

    public CppGenerator visit(Symbol s) {
        if (s.module().has()) {
            visit(s.module().get());
            write('$');
        }
        visit(s.name());
        return this;
    }


    public CppGenerator declareVar(Variable v) {
        return write(v).endStmt();
    }

    public CppGenerator visit(DefinedType e) {
        return switch (e) {
            case DerivedType t -> visit(t);
            case PrimitiveType t -> visit(t);
            case null, default -> unreachable();
        };
    }

    public CppGenerator visit(DerivedType dt) {
        visit(dt.symbol());
        if (!dt.generic().isEmpty())
            unsupported("泛型未实现");
        return this;
    }

    public CppGenerator visit(PrimitiveType dt) {
        return write(dt.primitive());
    }

    public CppGenerator visit(ClassField cf) {
        assert enterClass != null;
        write(cf.type());
        write(' ');
        visit(cf.name());
        write(";\n");
        return this;
    }

    private volatile FunctionDefinition enterFunc;

    void write(VariableParameterSet ps) {
        var first = true;
        for (var a : ps.variables()) {
            if (first) first = false;
            else write(COMMA);
            write(a.type().must());
            write(' ');
            varName(a);
        }
    }

    CppGenerator writeArgs(VariableParameterSet ps) {
        var size = ps.size();
        for (int i = 0; i < size; i++) {
            if (i > 0) write(COMMA);
            visit(ps.getName(i));
            i++;
        }
        return this;
    }

    private CppGenerator write(String name, Prototype prototype) {
        var ps = prototype.parameterSet();
        var rs = prototype.returnSet();
        if (rs.has()) {
            write(rs.get());
        } else {
            write("void");
        }
        write(' ');
        write(name);
        write('(');
        write(ps);
        write(')');
        return this;
    }

    private CppGenerator write(Identifier name, Prototype prototype) {
        return write(name.value(), prototype);
    }

    public void implFunc(FunctionDefinition fd) {
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
        visit(proc.body());
        if (noReturn(proc.body().list()))
            exitScope(proc);
        write('}').newLine();
    }

    //

    private boolean noReturn(List<Statement> list) {
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

    private void visit(List<Statement> list) {
        for (var s : list) visit(s);
    }

    private CppGenerator visit(Statement e) {
        return switch (e) {
            case AssignmentsStatement ee -> visit(ee);
            case BlockStatement ee -> visit(ee);
            case BreakStatement ee -> visit(ee);
            case CallStatement ee -> visit(ee);
            case ContinueStatement ee -> visit(ee);
            case DeclarationStatement ee -> visit(ee);
            case ForStatement ee -> visit(ee);
            case GotoStatement ee -> visit(ee);
            case IfStatement ee -> visit(ee);
            case LabeledStatement ee -> visit(ee);
            case ReturnStatement ee -> visit(ee);
            case SwitchStatement ee -> visit(ee);
            case ThrowStatement ee -> visit(ee);
            case TryStatement ee -> visit(ee);
            case null, default -> unreachable();
        };
    }

    private CppGenerator visit(ForStatement e) {
        return switch (e) {
            case ConditionalForStatement ee -> visit(ee);
            case IterableForStatement ee -> visit(ee);
            case null, default -> unreachable();
        };
    }

    private void writeStmts(List<Statement> list) {
        visit(list);
    }

    public CppGenerator visit(BlockStatement bs) {
        if (bs.newScope()) write('{').newLine();

        visit(bs.list());

        if (bs.newScope()) {
            if (noReturn(bs.list())) exitScope(bs);
            dedent().write('}').newLine();
        }
        return this;
    }

    private CppGenerator endStmt() {
        return write(";").newLine();
    }

    private CppGenerator release(Variable v) {
        var t = v.type().must();
        if (t.maybeRefer().has()) {
            return write("Feng$dec(").varName(v).write(')').endStmt();
        }
        var cdo = findClass(t);
        if (cdo.none()) return this;
        return varName(v).write(".Feng$release()").endStmt();
    }

    public CppGenerator visit(ReturnStatement rs) {
        writeComment("release and return");
        var prot = rs.procedure().must().prototype();
        if (rs.result().has()) {
            var re = rs.result().get();
            if (re instanceof VariableExpression ve) {
                var rv = ve.variable();
                for (var v : rs.local().reversed()) {
                    if (v.id() == rv.id()) continue;
                    release(v);
                }
                write("return ");
                writeValue(ve, prot.returnSet().must(), true);
                return endStmt();
            }
        }

        for (var v : rs.local().reversed()) release(v);
        write("return ");
        if (rs.result().has()) {
            writeValue(rs.result().get(), prot.returnSet().must(), false);
        }
        return endStmt();
    }

    public CppGenerator visit(DeclarationStatement ds) {
        ds.variables().forEach(this::declareVar);
        return this;
    }

    public CppGenerator visit(Operand e) {
        return switch (e) {
            case IndexOperand ee -> visit(ee);
            case FieldOperand ee -> visit(ee);
            case VariableOperand ee -> visit(ee);
            case null, default -> unreachable();
        };
    }

    public CppGenerator visit(VariableOperand e) {
        varName(e.variable().must());
        return this;
    }

    public CppGenerator visit(IndexOperand e) {
        visit(e.subject());
        write('[');
        visit(e.index());
        write(']');
        return this;
    }

    public CppGenerator visit(FieldOperand e) {
        visit(e.subject());
        var td = (DerivedTypeDeclarer) e.subject().resultType.must();
        deRefer(td);
        visit(e.field());
        return this;
    }

    public CppGenerator visit(AssignmentsStatement as) {
        for (int i = 0; i < as.operands().size(); i++) {
            writeAssign(as.operand(i), as.value(i));
            endStmt();
        }
        return this;
    }

    public CppGenerator visit(BreakStatement e) {
        if (e.label().has()) return unsupported("break label");
        write("break;\n");
        return this;
    }

    public CppGenerator visit(CallStatement e) {
        var pt = e.call().prototype().must();
        pt.returnSet().use(td -> {
        });
        visit(e.call());
        return endStmt();
    }

    private CppGenerator writeAssign(Operand o, Expression v) {
        var t = o.type.must();
        var r = t.maybeRefer();
        if (r.has()) {
            if (r.get().isKind(PHANTOM)) {
                return visit(o).write(" = ").visit(v);
            }
            return write("Feng$inc(").visit(v).write("),Feng$dec(")
                    .visit(o).write(')');
        }

        var cd = findClass(t);
        if (cd.none()) {
            return visit(o).write(" = ").visit(v);
        }

        visit(o).write(".Feng$release() = ").visit(v);
        if (v.unbound()) return this;
        return write(".Feng$share()");
    }

    private void writeForStmt(Statement s) {
        if (s instanceof DeclarationStatement ds) {
            if (ds.variables().size() > 1) {
                unsupported("multi declaration: %s", ds.pos());
                return;
            }
            write(ds.variables().getFirst());
        } else if (s instanceof AssignmentsStatement as) {
            var size = as.operands().size();
            for (int i = 0; i < size; i++) {
                if (i > 0) write(',');
                writeAssign(as.operand(i), as.value(i));
            }
        } else {
            unreachable();
        }
    }


    public CppGenerator visit(ConditionalForStatement fs) {
        if (fs.initializer().none()) {
            write("while(");
            visit(fs.condition());
            write(')');
            visit(fs.body());
        } else {
            write('{');
            fs.initializer().use(this::visit);
            write("for(");
            write(';');
            visit(fs.condition());
            write(';');
            fs.updater().use(this::writeForStmt);
            write(')');
            visit(fs.body());
            write('}');
        }
        return this;
    }

    public CppGenerator visit(IterableForStatement s) {
        return unsupported("iterable for");
    }

    public CppGenerator visit(ContinueStatement s) {
        write("continue ");
        s.label().use(this::visit);
        return endStmt();
    }

    public CppGenerator visit(GotoStatement e) {
        return unsupported("goto");
    }

    public CppGenerator visit(IfStatement is) {
        is.init().use(s -> {
            write('{');
            visit(s);
        });
        write("if(");
        visit(is.condition());
        write(')');
        visit(is.yes());
        is.not().use(s -> {
            write(" else ").visit(s);
        });
        if (is.init().has()) {
            write('}');
        }
        return this;
    }

    public CppGenerator visit(LabeledStatement s) {
        visit(s.label());
        write(':');
        return visit(s.target());
    }

    public CppGenerator visit(SwitchStatement ss) {
        if (ss.init().has()) {
            write('{');
            visit(ss.init().get());
        }
        write("switch(");
        visit(ss.value());
        write("){");
        for (var br : ss.branches()) {
            for (var cs : br.constants()) {
                write("case ").visit(cs).write(':');
            }
            visit(br);
            write("break;").newLine();
        }
        ss.defaultBranch().use(br -> {
            write("default: ");
            visit(br);
        });
        write('}').newLine();
        if (ss.init().has()) {
            write('}').newLine();
        }
        write('}').newLine();
        return this;
    }

    public CppGenerator visit(Branch e) {
        visit(e.body());
        return this;
    }

    public CppGenerator visit(ThrowStatement e) {
        return unsupported("throw");
    }

    public CppGenerator visit(TryStatement e) {
        return unsupported("try..catch");
    }

    // expression

    public CppGenerator visit(Expression e) {
        switch (e) {
            case BinaryExpression ee -> visit(ee);
            case UnaryExpression ee -> visit(ee);
            case ArrayExpression ee -> visit(ee);
            case AssertExpression ee -> visit(ee);
            case SizeofExpression ee -> visit(ee);
            case CallExpression ee -> visit(ee);
            case CurrentExpression ee -> visit(ee);
            case IndexOfExpression ee -> visit(ee);
            case LambdaExpression ee -> visit(ee);
            case LiteralExpression ee -> visit(ee);
            case MemberOfExpression ee -> visit(ee);
            case NewExpression ee -> visit(ee);
            case ObjectExpression ee -> visit(ee);
            case PairsExpression ee -> visit(ee);
            case ParenExpression ee -> visit(ee);
            case ReferExpression ee -> visit(ee);
            case VariableExpression ee -> visit(ee);
            case IsNilExpression ee -> visit(ee);
            case null, default -> unreachable();
        }
        return this;
    }

    private CppGenerator writeValues(List<Expression> values, List<TypeDeclarer> dstTypes) {
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) write(COMMA);
            writeValue(values.get(i), dstTypes.get(i), false);
        }
        return this;
    }

    private CppGenerator writePow(Expression left, Expression right) {
        return unsupported("幂运算");
    }

    private CppGenerator referOperand(Expression e) {
        var t = e.resultType.must();
        if (t instanceof Referable r) {
            if (r.refer().has()) write(".get()");
        } else if (t.isNil()) {
        }
        return this;
    }

    public CppGenerator visit(BinaryExpression e) {
        write('(');
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

        visit(e.left()).referOperand(e.left());
        write(o);
        visit(e.right()).referOperand(e.right());
        write(')');
        return this;
    }

    public CppGenerator visit(ArrayExpression e) {
        write('{');
//        writeValues(e.elements(), e.result.must());
        write('}');
        return this;
    }

    private CppGenerator cast(Expression v, TypeDeclarer t) {
        var src = findType(v.resultType.must());
        var dst = findType(t);
        if (src.equals(dst)) return visit(v);

        return write("Feng$cast<").visit(src.symbol())
                .write(',').visit(dst.symbol())
                .write(">(").visit(v).write(')');
    }

    public CppGenerator visit(AssertExpression e) {
        if (!e.needCheck())
            return cast(e.subject(), e.type());

        var src = findType(e.subject().resultType.must());
        var dst = findType(e.type());
        return write("Feng$assert<").visit(src.symbol())
                .write(',').visit(dst.symbol())
                .write(">(").visit(e.subject())
                .write(", ").typeId(dst).write(')');
    }

    public CppGenerator visit(CallExpression e) {
        visit(e.callee());
        write('(');
        writeValues(e.arguments(),
                e.prototype().must().parameterSet().types());
        write(')');
        return this;
    }

    public CppGenerator visit(IndexOfExpression e) {
        visit(e.subject());
        write('[');
        visit(e.index());
        write(']');
        return this;
    }

    private CppGenerator visit(VariableExpression e) {
        return varName(e.variable());
    }

    public CppGenerator visit(LambdaExpression e) {
        return unsupported("尼玛函数");
    }

    public CppGenerator visit(Literal e) {
        return switch (e) {
            case BoolLiteral ee -> visit(ee);
            case FloatLiteral ee -> visit(ee);
            case IntegerLiteral ee -> visit(ee);
            case NilLiteral ee -> visit(ee);
            case StringLiteral ee -> visit(ee);
            case null, default -> unreachable();
        };
    }

    public CppGenerator visit(BoolLiteral e) {
        write(e.value() ? "true" : "false");
        return this;
    }

    public CppGenerator visit(FloatLiteral e) {
        write(e.value().toPlainString());
        return this;
    }

    public CppGenerator visit(IntegerLiteral e) {
        write(e.value().toString(e.radix()));
        return this;
    }

    public CppGenerator visit(NilLiteral e) {
        write("nullptr");
        return this;
    }

    public CppGenerator visit(StringLiteral e) {
        write('"');
        var cb = e.charset().decode(ByteBuffer.wrap(e.value()));
        for (int i = 0; i < cb.length(); i++) {
            write(cb.charAt(i));
        }
        write('"');
        return this;
    }

    public CppGenerator visit(LiteralExpression e) {
        visit(e.literal());
        return this;
    }

    public CppGenerator visit(CurrentExpression e) {
        assert enterClass != null;
        if (e.isSelf()) return write("this");
        unsupported("super");
        var pd = enterClass.parent().must();
        return visit(pd.symbol()).write("::");
    }

    private void deRefer(Referable r) {
        if (r.refer().has()) write("->");
        else write('.');
    }

    public CppGenerator visit(MemberOfExpression e) {
        visit(e.subject());
        var td = (DerivedTypeDeclarer) e.subject().resultType.must();
        deRefer(td);
        if (!e.generic().isEmpty()) return unsupported("泛型");
        if (e.expectCallable()) {
            var def = findType(td.derivedType());
            if (def instanceof ClassDefinition cd) {
                var cm = cd.methods().tryGet(e.member());
                if (cm.match(m -> !m.override().isEmpty())) {
                    write(switchMethodName(e.member().value()));
                    return this;
                }
            }
        }
        write(e.member());
        return this;
    }

    private CppGenerator visitNew(NewDefinedType dt, NewExpression e) {
        var def = findType(dt.type());
        write("FENG$NEW(").visit(dt.type()).write(',');
        domain(def.domain()).write(',');
        if (e.arg().has()) {
            visit(e.arg().get());
        } else {
            if (dt.type() instanceof PrimitiveType) {
                write("0");
            } else {
                write("{}");
            }
        }
        write(')');
        return this;
    }

    private CppGenerator visitNew(NewArrayType t, NewExpression e) {
        return unsupported("new array");
    }

    public CppGenerator visit(NewExpression e) {
        return switch (e.type()) {
            case NewDefinedType t -> visitNew(t, e);
            case NewArrayType t -> visitNew(t, e);
            case null, default -> unreachable();
        };
    }

    private boolean objectInit(
            IdentifierTable<Expression> entries,
            Iterator<List<Identifier>> stack) {
        if (!stack.hasNext()) return false;
        var names = stack.next();
        write('{');
        if (objectInit(entries, stack)) write(',');
        for (var name : names) {
            var v = entries.get(name);
            write('.').write(name.value()).write('=');
            visit(v).write(',');
        }
        write('}');
        return true;
    }

    public CppGenerator visit(ObjectExpression oe) {
        if (oe.initStack.isEmpty())
            return write("{}");

        objectInit(oe.entries(), oe.initStack.iterator());

        return this;
    }

    public CppGenerator visit(PairsExpression e) {
        return unsupported("pairs");
    }

    public CppGenerator visit(ParenExpression e) {
        write('(');
        visit(e.child());
        write(')');
        return this;
    }

    public CppGenerator visit(IsNilExpression e) {
        return visit(e.subject()).write(" == nullptr");
    }

    public CppGenerator visit(ReferExpression e) {
        if (!e.generic().isEmpty()) return unsupported("generic");
        var t = e.resultType.must();
        visit(e.symbol());
        return this;
    }

    public CppGenerator visit(UnaryExpression e) {
        write('(');
        var op = e.operator();
        var td = e.resultType.must();
        if (!(td instanceof PrimitiveTypeDeclarer ptd))
            return unreachable();
        var p = ptd.primitive();
        if (p == Primitive.BOOL) {
            if (op != UnaryOperator.INVERT)
                return unreachable();
            write('!');
        } else if (p.isInteger()) {
            if (op == UnaryOperator.NEGATIVE)
                write('-');
            else if (op == UnaryOperator.INVERT)
                write('~');
            // ignore +
        } else {
            if (op == UnaryOperator.INVERT)
                return unreachable();
            else if (op == UnaryOperator.NEGATIVE)
                write('-');
        }
        visit(e.operand());
        write(')');
        return this;
    }

    //


}
