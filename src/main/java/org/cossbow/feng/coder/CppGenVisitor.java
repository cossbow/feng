package org.cossbow.feng.coder;

import org.cossbow.feng.visit.EntityVisitor;
import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.Symbol;
import org.cossbow.feng.ast.dcl.*;
import org.cossbow.feng.ast.gen.*;
import org.cossbow.feng.ast.oop.*;
import org.cossbow.feng.ast.proc.*;
import org.cossbow.feng.ast.stmt.*;
import org.cossbow.feng.util.ErrorUtil;

import java.io.IOException;

public class CppGenVisitor implements EntityVisitor<CppGenVisitor> {
    private final Appendable a;

    public CppGenVisitor(Appendable a) {
        this.a = a;
    }

    private CppGenVisitor write(char c) {
        try {
            a.append(c);
        } catch (IOException e) {
            ErrorUtil.io(e);
        }
        return this;
    }

    private CppGenVisitor write(CharSequence cs) {
        try {
            a.append(cs);
        } catch (IOException e) {
            ErrorUtil.io(e);
        }
        return this;
    }

    //

    @Override
    public CppGenVisitor visit(ClassDefinition cd) {
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
        write("public:\n");
        for (var f : cd.fields().values()) {
            visit(f);
        }
        write("public:\n");
        for (var m : cd.methods().values()) {
            visit(m);
        }
        write('}');
        return this;
    }

    void visit(UnnamedParameterSet ps) {
        var first = true;
        for (var td : ps.types()) {
            if (first) first = false;
            else write(", ");
            visit(td);
        }
    }

    void visit(VariableParameterSet ps) {
        var first = true;
        for (var v : ps.variables().values()) {
            if (first) first = false;
            else write(", ");
            visit(v);
        }
    }

    @Override
    public CppGenVisitor visit(FunctionDefinition fd) {
        var proc = fd.procedure();
        var ps = proc.prototype().parameterSet();
        var rs = proc.prototype().returnSet();
        if (rs.size() > 1) {
            ErrorUtil.unsupported("多返回值需要特殊处理");
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
                visit(vps);
                break;
            case UnnamedParameterSet ups:
                visit(ups);
                break;
            case null, default:
        }
        write(")\n");
        visit(proc.body());
        return this;
    }

    @Override
    public CppGenVisitor visit(ClassMethod cm) {
      return   visit((FunctionDefinition) cm);
    }

    @Override
    public CppGenVisitor visit(Identifier id) {
        write(id.value());
        return this;
    }

    @Override
    public CppGenVisitor visit(Symbol s) {
        if (s.module().has()) {
            visit(s.module().get());
            write('$');
        }
        visit(s.name());
        return this;
    }

    @Override
    public CppGenVisitor visit(ArrayTypeDeclarer td) {
        write('[');
        if (td.length().has()) {
            ErrorUtil.unsupported("未实现定长数组");
        }
        write(']');
        visit(td.element());
        return this;
    }

    @Override
    public CppGenVisitor visit(DefinedTypeDeclarer td) {
        visit(td.definedType());
        if (td.reference().has()) {
            var ref = td.reference().get();
            switch (ref.type()) {
                case STRONG:
                    write('*');
                    break;
                case null, default:
                    ErrorUtil.unsupported("其他引用未实现");
            }
        }
        return this;
    }

    void visit(Declare d) {
        if (d == Declare.CONST) write("const");
    }

    @Override
    public CppGenVisitor visit(Variable v) {
        visit(v.declare());
        write(' ');
        visit(v.type().must());
        write(' ');
        visit(v.name());
        return this;
    }

    @Override
    public CppGenVisitor visit(DefinedType dt) {
        visit(dt.symbol());
        if (!dt.generic().isEmpty())
            ErrorUtil.unsupported("泛型未实现");
        return this;
    }

    @Override
    public CppGenVisitor visit(ClassField cf) {
        visit(cf.declare());
        write(' ');
        visit(cf.type());
        write(' ');
        visit(cf.name());
        write(";\n");
        return this;
    }


    @Override
    public CppGenVisitor visit(BlockStatement bs) {
        write("{\n");
        for (var s : bs.list())
            visit(s);
        write("}\n\n");
        return this;
    }

    @Override
    public CppGenVisitor visit(ReturnStatement rs) {
        write("return ");
        if (rs.result().has())
            visit(rs.result().get());
        write(";\n");
        return this;
    }

}
