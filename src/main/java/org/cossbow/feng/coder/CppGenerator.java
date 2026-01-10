package org.cossbow.feng.coder;

import org.cossbow.feng.analysis.TypeDeducer;
import org.cossbow.feng.ast.*;
import org.cossbow.feng.ast.dcl.*;
import org.cossbow.feng.ast.expr.*;
import org.cossbow.feng.ast.gen.DefinedType;
import org.cossbow.feng.ast.lit.*;
import org.cossbow.feng.ast.mod.Global;
import org.cossbow.feng.ast.oop.ClassDefinition;
import org.cossbow.feng.ast.oop.ClassField;
import org.cossbow.feng.ast.oop.ClassMethod;
import org.cossbow.feng.ast.proc.FunctionDefinition;
import org.cossbow.feng.ast.proc.UnnamedParameterSet;
import org.cossbow.feng.ast.proc.VariableParameterSet;
import org.cossbow.feng.ast.stmt.*;
import org.cossbow.feng.visit.EntityVisitor;
import org.cossbow.feng.visit.SymbolContext;

import java.io.IOException;
import java.util.Collection;

import static org.cossbow.feng.util.ErrorUtil.*;

public class CppGenerator implements EntityVisitor<CppGenerator> {
    private final SymbolContext context;
    private final Appendable out;

    private final TypeDeducer deducer;

    public CppGenerator(SymbolContext context,
                        Appendable out) {
        this.context = context;
        this.out = out;
        this.deducer = new TypeDeducer(context);
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

    @Override
    public CppGenerator visit(Source e) {
        if (!e.imports().isEmpty()) return unsupported("import");
        for (var def : e.definitions())
            visit(def);
        for (var dcl : e.declarations())
            visit(dcl);
        return this;
    }


    // type declarer

    @Override
    public CppGenerator visit(ArrayTypeDeclarer td) {
        write('[');
        if (td.length().has()) {
            unsupported("未实现定长数组");
        }
        write(']');
        visit(td.element());
        return this;
    }

    @Override
    public CppGenerator visit(DefinedTypeDeclarer td) {
        visit(td.definedType());
        if (td.reference().has()) {
            var ref = td.reference().get();
            switch (ref.type()) {
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

    // class definition

    @Override
    public CppGenerator visit(ClassDefinition cd) {
        write("class ");
        visit(cd.name());
        write(' ');
        if (cd.parent().has() || !cd.impl().isEmpty()) {
            write(": public ");
            int i = 0;
            if (cd.parent().has()) {
                visit(cd.parent().get());
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
        return this;
    }

    @Override
    public CppGenerator visit(ClassMethod cm) {
        return visit((FunctionDefinition) cm);
    }

    @Override
    public CppGenerator visit(Identifier id) {
        write(id.value());
        return this;
    }

    @Override
    public CppGenerator visit(Symbol s) {
        if (s.module().has()) {
            visit(s.module().get());
            write('$');
        }
        visit(s.name());
        return this;
    }


    @Override
    public CppGenerator visit(Variable v) {
        visit(v.type().must());
        write(' ');
        visit(v.name());
        return this;
    }

    @Override
    public CppGenerator visit(DefinedType dt) {
        visit(dt.symbol());
        if (!dt.generic().isEmpty())
            unsupported("泛型未实现");
        return this;
    }

    @Override
    public CppGenerator visit(ClassField cf) {
        visit(cf.type());
        write(' ');
        visit(cf.name());
        write(";\n");
        return this;
    }


    @Override
    public CppGenerator visit(FunctionDefinition fd) {
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
        visit(fd.name());
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
        return this;
    }

    @Override
    public CppGenerator visit(BlockStatement bs) {
        write("{\n");
        for (var s : bs.list())
            visit(s);
        write("}\n");
        return this;
    }

    @Override
    public CppGenerator visit(ReturnStatement rs) {
        write("return ");
        if (rs.result().has())
            visit(rs.result().get());
        write(";\n");
        return this;
    }

    @Override
    public CppGenerator visit(DeclarationStatement ds) {
        var size = ds.variables().size();
        for (int i = 0; i < size; i++) {
            var v = ds.variables().get(i);
            var e = ds.init();  // TODO: tuple怎么处理
        }
        return this;
    }

    // expression

    @Override
    public CppGenerator visit(ArrayTuple e) {
        writeSeq(e.values(), COMMA);
        return this;
    }

    @Override
    public CppGenerator visit(IfTuple e) {
        return unsupported("");
    }

    @Override
    public CppGenerator visit(SwitchTuple e) {
        return unsupported("");
    }

    @Override
    public CppGenerator visit(ReturnTuple e) {
        visit(e.call());
        return this;
    }

    @Override
    public CppGenerator visit(Expression e) {
        write('(');
        EntityVisitor.super.visit(e);
        write(')');
        return this;
    }

    private CppGenerator writePow(Expression left, Expression right) {
        return unsupported("幂运算");
    }

    @Override
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

    @Override
    public CppGenerator visit(ArrayExpression e) {
        write('{');
        writeSeq(e.elements(), COMMA);
        write('}');
        return this;
    }

    @Override
    public CppGenerator visit(AssertExpression e) {
        return unsupported("断言未实现");
    }

    @Override
    public CppGenerator visit(CallExpression e) {
        visit(e.callee());
        write('(');
        writeSeq(e.arguments(), COMMA);
        write(')');
        return this;
    }

    @Override
    public CppGenerator visit(IndexOfExpression e) {
        visit(e.subject());
        write('[');
        visit(e.index());
        write(']');
        return this;
    }

    @Override
    public CppGenerator visit(LambdaExpression e) {
        return unsupported("尼玛函数");
    }

    @Override
    public CppGenerator visit(BoolLiteral e) {
        write(e.value() ? "true" : "false");
        return this;
    }

    @Override
    public CppGenerator visit(FloatLiteral e) {
        write(e.value().toPlainString());
        return this;
    }

    @Override
    public CppGenerator visit(IntegerLiteral e) {
        write(e.value().toString(e.radix()));
        return this;
    }

    @Override
    public CppGenerator visit(NilLiteral e) {
        write("NULL");
        return this;
    }

    @Override
    public CppGenerator visit(StringLiteral e) {
        write('"');
        write(e.value());
        write('"');
        return this;
    }

    @Override
    public CppGenerator visit(LiteralExpression e) {
        visit(e.literal());
        return this;
    }

    @Override
    public CppGenerator visit(MemberOfExpression e) {
        var td = deducer.visit(e.subject());
        if (td instanceof MemTypeDeclarer mtd) {
            if (mtd.mapped().none())
                return semantic("未映射类型");
            td = mtd.mapped().get();
        }
        if (td instanceof DefinedTypeDeclarer dtd) {
            if (dtd.reference().has())
                write("->");
            else
                write('.');
        }
        visit(e.subject());
        if (!e.generic().isEmpty())
            return unsupported("泛型");
        write(e.member().value());
        return EntityVisitor.super.visit(e);
    }

    @Override
    public CppGenerator visit(NewExpression e) {
        return unsupported("new");
    }

    @Override
    public CppGenerator visit(ObjectExpression e) {
        write('{');
        e.entries().foreach((name, expr) -> {
            write('.').write(name.value()).write('=');
            visit(expr);
        });
        write('}');
        return this;
    }

    @Override
    public CppGenerator visit(PairsExpression e) {
        return unsupported("pairs");
    }

    @Override
    public CppGenerator visit(ParenExpression e) {
        write('(');
        visit(e.child());
        write(')');
        return this;
    }

    @Override
    public CppGenerator visit(ReferExpression e) {
        if (!e.generic().isEmpty()) return unsupported("generic");
        visit(e.symbol());
        return this;
    }

    @Override
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
        } else if (p.integer) {
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
