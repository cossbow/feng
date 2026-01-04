package org.cossbow.feng.coder;

import org.cossbow.feng.visit.EntityVisitor;
import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.Source;
import org.cossbow.feng.ast.Symbol;
import org.cossbow.feng.ast.attr.Attribute;
import org.cossbow.feng.ast.attr.AttributeDefinition;
import org.cossbow.feng.ast.attr.AttributeField;
import org.cossbow.feng.ast.attr.Modifier;
import org.cossbow.feng.ast.dcl.*;
import org.cossbow.feng.ast.expr.*;
import org.cossbow.feng.ast.gen.*;
import org.cossbow.feng.ast.lit.*;
import org.cossbow.feng.ast.micro.MacroClass;
import org.cossbow.feng.ast.micro.MacroFunc;
import org.cossbow.feng.ast.micro.MacroProcedure;
import org.cossbow.feng.ast.micro.MacroVariable;
import org.cossbow.feng.ast.mod.GlobalDeclaration;
import org.cossbow.feng.ast.mod.GlobalDefinition;
import org.cossbow.feng.ast.mod.Import;
import org.cossbow.feng.ast.mod.Module_;
import org.cossbow.feng.ast.oop.*;
import org.cossbow.feng.ast.proc.*;
import org.cossbow.feng.ast.stmt.*;
import org.cossbow.feng.ast.struct.*;
import org.cossbow.feng.ast.var.IndexAssignableOperand;
import org.cossbow.feng.ast.var.MemberAssignableOperand;
import org.cossbow.feng.ast.var.VariableAssignableOperand;
import org.cossbow.feng.util.ErrorUtil;

import java.io.IOException;

public class CppGenVisitor implements EntityVisitor {
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
    public void visit(AttributeDefinition e) {

    }

    @Override
    public void visit(ClassDefinition cd) {
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
    }

    @Override
    public void visit(EnumDefinition e) {

    }

    @Override
    public void visit(InterfaceDefinition e) {

    }

    @Override
    public void visit(PrototypeDefinition e) {

    }

    @Override
    public void visit(InterfaceMethod e) {

    }

    @Override
    public void visit(StructureDefinition e) {

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
    public void visit(FunctionDefinition fd) {
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
    }

    @Override
    public void visit(ClassMethod cm) {
        visit((FunctionDefinition) cm);
    }

    @Override
    public void visit(Identifier id) {
        write(id.value());
    }

    @Override
    public void visit(Source e) {

    }

    @Override
    public void visit(Symbol s) {
        if (s.module().has()) {
            visit(s.module().get());
            write('$');
        }
        visit(s.name());
    }

    @Override
    public void visit(Attribute e) {

    }

    @Override
    public void visit(AttributeField e) {

    }

    @Override
    public void visit(Modifier e) {

    }

    @Override
    public void visit(NewArrayType e) {

    }

    @Override
    public void visit(NewDefinedType e) {

    }

    @Override
    public void visit(Reference e) {

    }

    @Override
    public void visit(ArrayTypeDeclarer td) {
        write('[');
        if (td.length().has()) {
            ErrorUtil.unsupported("未实现定长数组");
        }
        write(']');
        visit(td.element());
    }

    @Override
    public void visit(DefinedTypeDeclarer td) {
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
    }

    @Override
    public void visit(FuncTypeDeclarer e) {

    }

    void visit(Declare d) {
        if (d == Declare.CONST) write("const");
    }

    @Override
    public void visit(Variable v) {
        visit(v.declare());
        write(' ');
        visit(v.type().must());
        write(' ');
        visit(v.name());
    }

    @Override
    public void visit(BinaryExpression e) {

    }

    @Override
    public void visit(ArrayExpression e) {

    }

    @Override
    public void visit(AssertExpression e) {

    }

    @Override
    public void visit(CallExpression e) {

    }

    @Override
    public void visit(IndexOfExpression e) {

    }

    @Override
    public void visit(LambdaExpression e) {

    }

    @Override
    public void visit(LiteralExpression e) {

    }

    @Override
    public void visit(MemberOfExpression e) {

    }

    @Override
    public void visit(NewExpression e) {

    }

    @Override
    public void visit(ObjectExpression e) {

    }

    @Override
    public void visit(PairsExpression e) {

    }

    @Override
    public void visit(ParenExpression e) {

    }

    @Override
    public void visit(ReferExpression e) {

    }

    @Override
    public void visit(UnaryExpression e) {

    }

    @Override
    public void visit(DefinedType dt) {
        visit(dt.symbol());
        if (!dt.generic().isEmpty())
            ErrorUtil.unsupported("泛型未实现");
    }

    @Override
    public void visit(TypeArguments e) {

    }

    @Override
    public void visit(BinaryTypeConstraint e) {

    }

    @Override
    public void visit(DefinedTypeConstraint e) {

    }

    @Override
    public void visit(DomainTypeConstraint e) {

    }

    @Override
    public void visit(TypeParameter e) {

    }

    @Override
    public void visit(TypeParameters e) {

    }

    @Override
    public void visit(BoolLiteral e) {

    }

    @Override
    public void visit(FloatLiteral e) {

    }

    @Override
    public void visit(IntegerLiteral e) {

    }

    @Override
    public void visit(NilLiteral e) {

    }

    @Override
    public void visit(StringLiteral e) {

    }

    @Override
    public void visit(MacroClass e) {

    }

    @Override
    public void visit(MacroFunc e) {

    }

    @Override
    public void visit(MacroProcedure e) {

    }

    @Override
    public void visit(MacroVariable e) {

    }

    @Override
    public void visit(GlobalDeclaration e) {

    }

    @Override
    public void visit(GlobalDefinition e) {

    }

    @Override
    public void visit(Import e) {

    }

    @Override
    public void visit(Module_ e) {

    }

    @Override
    public void visit(ClassField cf) {
        visit(cf.declare());
        write(' ');
        visit(cf.type());
        write(' ');
        visit(cf.name());
        write(";\n");
    }

    @Override
    public void visit(Procedure e) {

    }

    @Override
    public void visit(Prototype e) {

    }

    @Override
    public void visit(CatchClause e) {

    }

    @Override
    public void visit(AssignmentOperateStatement e) {

    }

    @Override
    public void visit(AssignmentsStatement e) {

    }

    @Override
    public void visit(BlockStatement bs) {
        write("{\n");
        for (var s : bs.list())
            visit(s);
        write("}\n\n");
    }

    @Override
    public void visit(BreakStatement e) {

    }

    @Override
    public void visit(CallStatement e) {

    }

    @Override
    public void visit(ContinueStatement e) {

    }

    @Override
    public void visit(DeclarationStatement e) {

    }

    @Override
    public void visit(ConditionalForStatement e) {

    }

    @Override
    public void visit(IterableForStatement e) {

    }

    @Override
    public void visit(GotoStatement e) {

    }

    @Override
    public void visit(IfStatement e) {

    }

    @Override
    public void visit(LabeledStatement e) {

    }

    @Override
    public void visit(LocalDefineStatement e) {

    }

    @Override
    public void visit(ReturnStatement rs) {
        write("return ");
        if (rs.result().has())
            visit(rs.result().get());
        write(";\n");
    }

    @Override
    public void visit(SwitchStatement e) {

    }

    @Override
    public void visit(ThrowStatement e) {

    }

    @Override
    public void visit(TryStatement e) {

    }

    @Override
    public void visit(SwitchBranch e) {

    }

    @Override
    public void visit(ArrayTuple e) {

    }

    @Override
    public void visit(IfTuple e) {

    }

    @Override
    public void visit(SwitchTuple e) {

    }

    @Override
    public void visit(StructureField e) {

    }

    @Override
    public void visit(ArrayStructureType e) {

    }

    @Override
    public void visit(DefinedStructureType e) {

    }

    @Override
    public void visit(UnnamedStructureType e) {

    }

    @Override
    public void visit(IndexAssignableOperand e) {

    }

    @Override
    public void visit(MemberAssignableOperand e) {

    }

    @Override
    public void visit(VariableAssignableOperand e) {

    }
}
