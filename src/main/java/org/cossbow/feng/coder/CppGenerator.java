package org.cossbow.feng.coder;

import org.cossbow.feng.analysis.TypeDeducer;
import org.cossbow.feng.analysis.TypeTool;
import org.cossbow.feng.ast.*;
import org.cossbow.feng.ast.dcl.*;
import org.cossbow.feng.ast.expr.*;
import org.cossbow.feng.ast.gen.DerivedType;
import org.cossbow.feng.ast.gen.MemType;
import org.cossbow.feng.ast.gen.PrimitiveType;
import org.cossbow.feng.ast.lit.*;
import org.cossbow.feng.ast.oop.ClassDefinition;
import org.cossbow.feng.ast.oop.ClassField;
import org.cossbow.feng.ast.oop.ClassMethod;
import org.cossbow.feng.ast.oop.InterfaceDefinition;
import org.cossbow.feng.ast.proc.FunctionDefinition;
import org.cossbow.feng.ast.proc.PrototypeDefinition;
import org.cossbow.feng.ast.proc.UnnamedParameterSet;
import org.cossbow.feng.ast.proc.VariableParameterSet;
import org.cossbow.feng.ast.stmt.*;
import org.cossbow.feng.ast.struct.StructureDefinition;
import org.cossbow.feng.ast.struct.StructureField;
import org.cossbow.feng.dag.DAGGraph;
import org.cossbow.feng.dag.DAGTask;
import org.cossbow.feng.parser.ParseSymbolTable;
import org.cossbow.feng.util.Groups;
import org.cossbow.feng.visit.EntityVisitor;
import org.cossbow.feng.visit.SymbolContext;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.cossbow.feng.util.ErrorUtil.*;

public class CppGenerator implements EntityVisitor<CppGenerator> {
    private final ParseSymbolTable table;
    private final SymbolContext context;
    private final Appendable out;

    private final TypeDeducer deducer;
    private final TypeTool typeTool;

    public CppGenerator(ParseSymbolTable table,
                        SymbolContext context,
                        Appendable out) {
        this.table = table;
        this.context = context;
        this.out = out;
        this.deducer = new TypeDeducer(context);
        this.typeTool = new TypeTool(context);
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

    public static final String COMMA = ", ";

    private <T extends Entity>
    void writeSeq(Collection<T> c, String sep) {
        var first = true;
        for (T t : c) {
            if (first) first = false;
            else write(sep);
            visit(t);
        }
    }

    // global

    public CppGenerator visit(Source src) {
        if (!src.imports().isEmpty()) return unsupported("import");

        visitTypes(src.types());
        if (src.table().dagVars.has())
            visitGlobalVariables(src.table().dagVars.must());

        for (var dcl : src.variables())
            visit(dcl);
        for (var def : src.functions())
            visit(def);
        return this;
    }

    void declareType(PrototypeDefinition def) {
    }

    void declareType(StructureDefinition def) {
    }

    void declareType(EnumDefinition def) {
    }

    void declareType(InterfaceDefinition def) {
    }

    void declareType(ClassDefinition def) {
        write("class ");
        visit(def.symbol().name());
        endStmt();
    }

    void declareType(TypeDefinition def) {
        switch (def) {
            case ClassDefinition cd -> declareType(cd);
            case InterfaceDefinition cd -> declareType(cd);
            case EnumDefinition cd -> declareType(cd);
            case StructureDefinition cd -> declareType(cd);
            case PrototypeDefinition cd -> declareType(cd);
            case null, default -> unreachable();
        }
    }


    void visitStructures(List<TypeDefinition> types) {
        var all = new ArrayList<StructureDefinition>();
        var edges = new ArrayList<Groups.G2<StructureDefinition, StructureDefinition>>();
        for (var def : types) {
            if (!(def instanceof StructureDefinition sd)) continue;
            all.add(sd);
            sd.initDeps().use(deps -> {
                for (var dep : deps) edges.add(Groups.g2(dep, sd));
            });
        }
        var dag = new DAGGraph<>(all, edges);
        dag.bfs(this::visit);
    }

    void visitClasses(List<TypeDefinition> types) {
        var all = new ArrayList<ClassDefinition>();
        var edges = new ArrayList<Groups.G2<ClassDefinition, ClassDefinition>>();
        for (var def : types) {
            if (!(def instanceof ClassDefinition cd)) continue;
            all.add(cd);
            cd.initDeps().use(deps -> {
                for (var dep : deps) edges.add(Groups.g2(dep, cd));
            });
        }
        var dag = new DAGGraph<>(all, edges);
        dag.bfs(this::visit);
    }

    void visitTypes(List<TypeDefinition> types) {
        for (var t : types) declareType(t);
        visitStructures(types);
        visitClasses(types);
    }

    void visitGlobalVariables(DAGGraph<GlobalVariable> dag) {
        new DAGTask<>(dag, (gv, deps) -> {
            visit(gv);
            return CompletableFuture.completedFuture(true);
        });
    }

    static final String STATIC = "static";

    public CppGenerator visit(GlobalVariable v) {
        write(STATIC);
        write(' ');
        visit(v.type().must());
        write(' ');
        visit(v.name());
        if (v.value().none()) return this;
        write(" = ");
        visit(v.value().must());
        endStmt();
        return this;
    }

    // type declarer

    static class ValueArrayKey {
        private TypeDeclarer element;
        private BigInteger length;
    }

    public CppGenerator visit(ArrayTypeDeclarer td) {
        write("struct Array_");
        visit(td.element());
        if (td.length().has()) {
            write('[');
            write(']');
        } else {
        }

        return this;
    }

    public CppGenerator visit(DerivedTypeDeclarer td) {
        visit(td.derivedType());
        if (td.refer().has()) {
            var ref = td.refer().get();
            switch (ref.kind()) {
                case STRONG:
                    write('*');
                    break;
                case null, default:
                    unsupported("其他引用未实现");
            }
        }
        return this;
    }

    public CppGenerator visit(PrimitiveTypeDeclarer dt) {
        String type = switch (dt.primitive()) {
            case INT8 -> "int8_t";
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
        return write(type);
    }

    public CppGenerator visit(MemTypeDeclarer e) {
        return unreachable();
    }

    public CppGenerator visit(FuncTypeDeclarer e) {
        return unreachable();
    }

    //

    public CppGenerator visit(StructureField sf) {
        visit(sf.type());
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

    public CppGenerator visit(ClassDefinition cd) {
        assert enterClass == null;
        enterClass = cd;
        write("class ");
        visit(cd.symbol().name());
        write(' ');
        if (cd.inherit().has() || !cd.impl().isEmpty()) {
            write(": public ");
            int i = 0;
            if (cd.inherit().has()) {
                visit(cd.inherit().get());
            } else {
                visit(cd.impl().getValue(0));
                i = 1;
            }
            for (; i < cd.impl().size(); i++) {
                write(',');
                visit(cd.impl().getValue(i));
            }
            write(' ');
        } else {
            write(": public Object ");
        }
        write("{\n");
        if (!cd.fields().isEmpty()) write("public:\n");
        for (var f : cd.fields().values())
            visit(f);

        if (!cd.methods().isEmpty()) write("public:\n");
        for (var m : cd.methods().values())
            visit(m);

        write("};\n");
        enterClass = null;
        return this;
    }

    public CppGenerator visit(ClassMethod cm) {
        assert enterClass != null;
        assert enterMethod == null;
        enterMethod = cm;
        visit(cm.func());
        enterMethod = null;
        return this;
    }

    public CppGenerator visit(Identifier id) {
        write(id.value());
        return this;
    }

    public CppGenerator visit(Symbol s) {
        if (s.module().has()) {
            visit(s.module().get());
            write('$');
        }
        visit(s.name());
        return this;
    }


    public CppGenerator visit(Variable v) {
        visit(v.type().must());
        write(' ');
        visit(v.name());
        if (v.value().none()) return this;
        write(" = ");
        visit(v.value().must());
        return this;
    }

    public CppGenerator visit(DerivedType dt) {
        visit(dt.symbol());
        if (!dt.generic().isEmpty())
            unsupported("泛型未实现");
        return this;
    }

    public CppGenerator visit(MemType dt) {
        return this;
    }

    public CppGenerator visit(PrimitiveType dt) {
        return this;
    }

    public CppGenerator visit(ClassField cf) {
        assert enterClass != null;
        visit(cf.type());
        write(' ');
        visit(cf.name());
        write(";\n");
        return this;
    }

    private volatile FunctionDefinition enterFunc;

    public CppGenerator visit(FunctionDefinition fd) {
        assert enterFunc == null;
        enterFunc = fd;
        var proc = fd.procedure();
        var ps = proc.prototype().parameterSet();
        var rs = proc.prototype().returnSet();
        if (rs.size() > 1) {
            unsupported("多返回值未实现");
        } else if (rs.size() == 1) {
            visit(rs.getFirst());
        } else {
            write("void");
        }
        write(' ');
        visit(fd.symbol());
        write('(');
        switch (ps) {
            case VariableParameterSet vps:
                writeSeq(vps.variables().values(), COMMA);
                break;
            case UnnamedParameterSet ups:
                writeSeq(ups.types(), COMMA);
                break;
            case null, default:
        }
        write(")\n");
        visit(proc.body());
        enterFunc = null;
        return this;
    }

    public CppGenerator visit(BlockStatement bs) {
        write("{\n");
        for (var s : bs.list())
            visit(s);
        write("}\n");
        return this;
    }

    private CppGenerator endStmt() {
        write(";\n");
        return this;
    }

    public CppGenerator visit(ReturnStatement rs) {
        write("return ");
        rs.result().use(this::visit);
        return endStmt();
    }

    public CppGenerator visit(DeclarationStatement ds) {
        if (ds.init().none()) {
            ds.variables().forEach(this::visit);
            return this;
        }

        // with init

        return this;
    }

    public CppGenerator visit(AssignmentsStatement e) {
        return EntityVisitor.super.visit(e);
    }

    public CppGenerator visit(BreakStatement e) {
        return EntityVisitor.super.visit(e);
    }

    public CppGenerator visit(CallStatement e) {
        visit(e.call());
        return endStmt();
    }

    public CppGenerator visit(ConditionalForStatement s) {
        write("for(");
        s.initializer().use(this::visit);
        visit(s.condition());
        endStmt();
        s.updater().use(this::visit);
        write(')');
        return visit(s.body());
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

    public CppGenerator visit(IfStatement s) {
        if (s.init().has()) {
            write('{');
            visit(s.init().get());
        }
        write("if(");
        visit(s.condition());
        write(')');
        visit(s.yes());
        if (s.not().has()) {
            write("else ");
            visit(s.not().get());
        }
        if (s.init().has()) write('}');
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
                write("case ");
                visit(cs);
                write(":\n");
            }
            for (var s : br.body().list())
                visit(s);

            write("break;\n");
        }
        write("}\n");
        if (ss.init().has()) write('}');
        return this;
    }

    public CppGenerator visit(ThrowStatement e) {
        return unsupported("throw");
    }

    public CppGenerator visit(TryStatement e) {
        return unsupported("try..catch");
    }

    // expression

    public CppGenerator visit(ArrayTuple e) {
        writeSeq(e.values(), COMMA);
        return this;
    }

    public CppGenerator visit(ReturnTuple e) {
        visit(e.call());
        return this;
    }

    public CppGenerator visit(Expression e) {
        write('(');
        EntityVisitor.super.visit(e);
        write(')');
        return this;
    }

    private CppGenerator writePow(Expression left, Expression right) {
        return unsupported("幂运算");
    }

    public CppGenerator visit(BinaryExpression e) {
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

        visit(e.left());
        write(o);
        visit(e.right());
        return this;
    }

    public CppGenerator visit(ArrayExpression e) {
        write('{');
        writeSeq(e.elements(), COMMA);
        write('}');
        return this;
    }

    public CppGenerator visit(AssertExpression e) {
        return unsupported("断言未实现");
    }

    public CppGenerator visit(CallExpression e) {
        visit(e.callee());
        write('(');
        writeSeq(e.arguments(), COMMA);
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

    public CppGenerator visit(LambdaExpression e) {
        return unsupported("尼玛函数");
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
        write("NULL");
        return this;
    }

    public CppGenerator visit(StringLiteral e) {
        write('"');
        write(e.value());
        write('"');
        return this;
    }

    public CppGenerator visit(LiteralExpression e) {
        visit(e.literal());
        return this;
    }

    public CppGenerator visit(CurrentExpression e) {
        if (e.isSelf()) return write("this->");
        return unsupported("super");
    }

    public CppGenerator visit(MemberOfExpression e) {
        if (e.subject() instanceof CurrentExpression ce) {
            visit(ce);
            return write(e.member().value());
        }

        var td = deducer.visit(e.subject());
        if (td instanceof MemTypeDeclarer mtd) {
            if (mtd.mapped().none())
                return semantic("未映射类型");
            td = mtd.mapped().get();
        }
        visit(e.subject());
        if (td instanceof DerivedTypeDeclarer dtd) {
            if (dtd.refer().has())
                write("->");
            else
                write('.');
        }
        if (!e.generic().isEmpty())
            return unsupported("泛型");
        write(e.member().value());
        return this;
    }

    public CppGenerator visit(NewExpression e) {
        return unsupported("new");
    }

    public CppGenerator visit(ObjectExpression e) {
        write('{');
        e.entries().each((name, expr) -> {
            write('.').write(name.value()).write('=');
            visit(expr);
        });
        write('}');
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

    public CppGenerator visit(ReferExpression e) {
        if (!e.generic().isEmpty()) return unsupported("generic");
        visit(e.symbol());
        return this;
    }

    public CppGenerator visit(UnaryExpression e) {
        var op = e.operator();
        var td = deducer.visit(e.operand());
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
        return this;
    }
}
