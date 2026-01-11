package org.cossbow.feng.analysis;

import org.cossbow.feng.ast.Entity;
import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.Source;
import org.cossbow.feng.ast.attr.Modifier;
import org.cossbow.feng.ast.dcl.*;
import org.cossbow.feng.ast.gen.DefinedType;
import org.cossbow.feng.ast.gen.TypeArguments;
import org.cossbow.feng.ast.oop.ClassDefinition;
import org.cossbow.feng.ast.oop.ClassField;
import org.cossbow.feng.ast.oop.ClassMethod;
import org.cossbow.feng.ast.proc.*;
import org.cossbow.feng.ast.stmt.*;
import org.cossbow.feng.ast.struct.StructureDefinition;
import org.cossbow.feng.ast.struct.StructureField;
import org.cossbow.feng.ast.var.IndexAssignableOperand;
import org.cossbow.feng.ast.var.FieldAssignableOperand;
import org.cossbow.feng.ast.var.VariableAssignableOperand;
import org.cossbow.feng.util.Stack;
import org.cossbow.feng.visit.EntityVisitor;
import org.cossbow.feng.visit.SymbolContext;

import java.util.ArrayList;
import java.util.Objects;

import static org.cossbow.feng.util.ErrorUtil.*;

public class SemanticAnalysis implements EntityVisitor<Entity> {

    private final TypeDeducer deducer;
    private final StackedContext context;

    public SemanticAnalysis(SymbolContext parent) {
        deducer = new TypeDeducer(parent);
        context = new StackedContext(parent);
    }

    //


    @Override
    public Entity visit(Source s) {
        s.declarations().forEach(this::visit);
        var t = s.table();
        t.namedTypes.each(this::visit);
        t.unnamedTypes.each(this::visit);
        t.namedFunctions.each(this::visit);
        t.lambdas.forEach(this::visit);
        return s;
    }

    @Override
    public Entity visit(Modifier m) {
        if (!m.attributes().isEmpty())
            unsupported("attribute");
        return m;
    }

    @Override
    public Entity visit(Reference e) {
        if (e.type() == ReferenceType.WEAK)
            unsupported("weak-reference");
        if (e.immutable())
            unsupported("refer immutable instance");
        if (e.required())
            unsupported("required reference");
        return e;
    }

    @Override
    public Entity visit(TypeArguments ta) {
        if (!ta.isEmpty()) unsupported("generic");
        return ta;
    }

    @Override
    public Entity visit(DefinedType dt) {
        var type = context.findType(dt.symbol());
        if (type.none())
            return semantic("%s not define", dt.symbol());
        if (!type.get().generic().isEmpty())
            return unsupported("generic");
        if (!dt.generic().isEmpty())
            return unsupported("generic");
        return type.get();
    }

    @Override
    public Entity visit(DefinedTypeDeclarer td) {
        visit(td.definedType());
        td.reference().use(this::visit);
        return td;
    }

    @Override
    public Entity visit(ArrayTypeDeclarer td) {
        visit(td.element());
        if (td.immutable()) unsupported("array immutable");
        td.length().use(this::visit);
        return td;
    }

    @Override
    public Entity visit(FuncTypeDeclarer td) {
        visit(td.prototype());
        visit(td.generic());
        return td;
    }

    private boolean checkMappable(DefinedTypeDeclarer dtd) {
        if (dtd.reference().has())
            return semantic("mem can't map reference");
        var type = visit(dtd.definedType());
        return type instanceof StructureDefinition;
    }

    private boolean checkMappable(ArrayTypeDeclarer td) {
        if (td.length().none()) return false;
        if (td.immutable()) unsupported("array immutable");
        return checkMappable(td.element());
    }

    private boolean checkMappable(TypeDeclarer td) {
        return switch (td) {
            case PrimitiveTypeDeclarer ignored -> true;
            case DefinedTypeDeclarer dtd -> checkMappable(dtd);
            case ArrayTypeDeclarer atd -> checkMappable(atd);
            case null -> unreachable();
            default -> false;
        };
    }

    @Override
    public Entity visit(MemTypeDeclarer mtd) {
        if (mtd.mapped().none()) return mtd;
        var map = mtd.mapped().get();
        visit(map);
        if (checkMappable(map)) return mtd;
        return semantic("can't map type %s", map.pos());
    }

    @Override
    public Entity visit(PrimitiveTypeDeclarer ptd) {
        return ptd;
    }

    @Override
    public Entity visit(ThisTypeDeclarer ctd) {
        if (enterClass == null)
            semantic("this only use in class");
        return ctd;
    }

    @Override
    public Entity visit(TupleTypeDeclarer ttd) {
        ttd.tuple().forEach(this::visit);
        return ttd;
    }

    //

    private volatile ClassDefinition enterClass;
    private volatile ClassMethod enterMethod;

    @Override
    public Entity visit(ClassDefinition cd) {
        assert enterClass == null;
        enterClass = cd;
        cd.parent().use(this::visit);
        cd.impl().each(this::visit);
        cd.fields().each(this::visit);
        cd.methods().each(this::visit);
        if (!cd.macros().isEmpty()) unsupported("macro");
        enterClass = null;
        return cd;
    }

    @Override
    public Entity visit(ClassField cf) {
        assert enterClass != null;
        if (cf.type() instanceof DefinedTypeDeclarer ctd) {
            var isPhantom = ctd.reference().match(
                    r -> r.checkType(ReferenceType.PHANTOM));
            if (!isPhantom) return visit(ctd);
            return semantic("can't be phantom-reference: %s",
                    ctd.pos());
        }
        return cf;
    }

    @Override
    public Entity visit(ClassMethod cm) {
        assert enterClass != null;
        assert enterMethod == null;
        enterMethod = cm;
        visit((FunctionDefinition) cm);
        enterMethod = null;
        return cm;
    }

    //

    private volatile FunctionDefinition enterFunc;

    @Override
    public Entity visit(FunctionDefinition fd) {
        assert enterFunc == null;
        enterFunc = fd;
        context.enterScope();

        if (!fd.generic().isEmpty())
            unsupported("generic");
        visit(fd.modifier());

        visit(fd.procedure());

        context.exitScope();
        enterFunc = null;
        return fd;
    }

    @Override
    public Entity visit(Procedure proc) {
        visit(proc.prototype());
        visit(proc.body());
        return proc;
    }

    @Override
    public Entity visit(Prototype prot) {
        checkParams(prot.parameterSet());
        prot.returnSet().forEach(this::visit);
        return prot;
    }

    private void checkParams(ParameterSet ps) {
        if (ps instanceof UnnamedParameterSet ups)
            checkParams(ups);
        else if (ps instanceof VariableParameterSet vps)
            checkParams(vps);
        else if (ps == null)
            unreachable();
    }

    private void checkParams(UnnamedParameterSet ps) {
        for (var td : ps.types()) {
            if (td instanceof DefinedTypeDeclarer dtd) {
                var isWeak = dtd.reference().match(
                        r -> r.checkType(ReferenceType.WEAK));
                if (isWeak)
                    semantic("can't be weak-reference: %s",
                            dtd.pos());
                dtd.reference().use(this::visit);
            }
            visit(td);
        }
    }

    private void checkParams(VariableParameterSet ps) {
        ps.variables().each(this::visit);
    }

    //

    @Override
    public Entity visit(BlockStatement bs) {
        context.enterScope();
        context.exitScope();
        return bs;
    }

    @Override
    public Entity visit(DeclarationStatement ds) {
        if (ds.init().has()) {
            var td = deducer.visit(ds.init().get());
            if (!(td instanceof TupleTypeDeclarer ttd))
                return unsupported("%s", td);
            var types = ttd.tuple();
            if (types.size() != ds.variables().size())
                return semantic("unalign declarion: %s", ds.pos());
            var i = 0;
            for (var v : ds.variables())
                v.type().set(types.get(i++));
        }
        for (var v : ds.variables()) {
            assert v.type().has();
            visit(v);
            context.putVar(v);
        }
        return ds;
    }

    private boolean
    assignable(DefinedTypeDeclarer l, DefinedTypeDeclarer r) {
        if (l.reference().none() && r.reference().none())
            return l.definedType().equals(r.definedType());

        // TODO: 这里比较复杂！

        return false;
    }

    private boolean assignable(TypeDeclarer l, TypeDeclarer r) {
        if (l instanceof PrimitiveTypeDeclarer pl &&
                r instanceof PrimitiveTypeDeclarer pr) {
            return pl.primitive() == pr.primitive();
        }

        if (l instanceof MemTypeDeclarer &&
                r instanceof MemTypeDeclarer) {
            // TODO: 运行时边界检查
            return true;
        }
        if (l instanceof ArrayTypeDeclarer al &&
                r instanceof ArrayTypeDeclarer ar) {
            if (al.immutable() != ar.immutable())
                return false;
            if (!al.element().equals(ar.element()))
                return false;
            if (al.length().none() && ar.length().none())
                return true;
            // TODO: 检查常量值相等
            return true;
        }

        if (l instanceof FuncTypeDeclarer fl &&
                r instanceof FuncTypeDeclarer fr) {
            return fl.prototype().equals(fr.prototype())
                    && fl.generic().equals(fr.generic());
        }

        if (l instanceof DefinedTypeDeclarer dl &&
                r instanceof DefinedTypeDeclarer dr) {
            return assignable(dl, dr);
        }

        return false;
    }

    @Override
    public Entity visit(AssignmentsStatement as) {
        var left = new ArrayList<TypeDeclarer>(as.operands().size());
        for (var ao : as.operands())
            left.add((TypeDeclarer) visit(ao));

        var td = deducer.visit(as.tuple());
        if (!(td instanceof TupleTypeDeclarer ttd))
            return unreachable();
        var right = ttd.tuple();

        if (left.size() != right.size())
            return semantic("unalign assignment: %s",
                    as.pos());

        for (int i = 0; i < left.size(); i++) {
            var l = left.get(i);
            var r = right.get(i);
            if (!assignable(l, r))
                return semantic("unassignable: %s = %s",
                        l, r);
        }

        return as;
    }

    @Override
    public Entity visit(CallStatement e) {
        visit(e.call());
        return e;
    }

    @Override
    public Entity visit(LabeledStatement e) {
        return visit(e.statement());
    }

    private Stack<ForStatement> loopStack = new Stack<>();

    @Override
    public Entity visit(BreakStatement e) {
        assert enterFunc != null;
        if (e.label().has()) {
            if (enterFunc.procedure().labels()
                    .contains(e.label().get()))
                return semantic("label not found: %s",
                        e.label().get());
        }
        // TODO: 检查是否在循环中
        return e;
    }

    @Override
    public Entity visit(ContinueStatement e) {
        assert enterFunc != null;
        if (e.label().has()) {
            if (enterFunc.procedure().labels()
                    .contains(e.label().get()))
                return semantic("label not found: %s",
                        e.label().get());
        }
        // TODO: 检查是否在循环中
        return e;
    }

    @Override
    public Entity visit(ForStatement e) {
        return EntityVisitor.super.visit(e);
    }

    @Override
    public Entity visit(ConditionalForStatement e) {
        return EntityVisitor.super.visit(e);
    }

    @Override
    public Entity visit(IterableForStatement e) {
        return EntityVisitor.super.visit(e);
    }

    @Override
    public Entity visit(GotoStatement e) {
        return EntityVisitor.super.visit(e);
    }

    @Override
    public Entity visit(IfStatement e) {
        return EntityVisitor.super.visit(e);
    }

    @Override
    public Entity visit(LocalDefineStatement e) {
        return EntityVisitor.super.visit(e);
    }

    @Override
    public Entity visit(ReturnStatement e) {
        return EntityVisitor.super.visit(e);
    }

    @Override
    public Entity visit(SwitchStatement e) {
        return EntityVisitor.super.visit(e);
    }

    @Override
    public Entity visit(ThrowStatement e) {
        return EntityVisitor.super.visit(e);
    }

    @Override
    public Entity visit(TryStatement e) {
        return EntityVisitor.super.visit(e);
    }

    @Override
    public Entity visit(SwitchBranch e) {
        return EntityVisitor.super.visit(e);
    }


    //

    @Override
    public Entity visit(IndexAssignableOperand io) {
        var td = deducer.visit(io.subject());
        if (!(td instanceof ArrayTypeDeclarer atd))
            return semantic("reqired array: %s", io.pos());

        if (atd.immutable())
            return unsupported("array immutable");

        var it = deducer.visit(io.index());
        if (it instanceof PrimitiveTypeDeclarer ptd) {
            if (ptd.primitive().integer ||
                    ptd.primitive() == Primitive.BOOL) {
                return atd.element();
            }
        }

        return semantic("index required integer");
    }

    @Override
    public Entity visit(FieldAssignableOperand mo) {
        var m = deducer.getMember(mo.subject(), mo.field());
        if (m instanceof StructureField sf) return sf.type();

        if (m instanceof ClassField cf) {
            if (cf.declare() == Declare.VAR) return cf.type();

            return semantic("const field: %s", mo.field().pos());
        }

        return semantic("invalid field", mo.field());
    }

    @Override
    public Entity visit(VariableAssignableOperand vo) {
        var s = vo.symbol();
        var v = context.findVar(s);
        if (v.none())
            return semantic("%s is not declared", s);

        var var = v.get();
        if (var.declare() == Declare.CONST)
            return semantic("%s is constant", s);

        visit(var);
        return var.type().must();
    }

    @Override
    public Entity visit(Variable v) {
        visit(v.type().must());
        visit(v.modifier());
        return v;
    }

}
