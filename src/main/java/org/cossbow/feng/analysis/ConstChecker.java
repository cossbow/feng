package org.cossbow.feng.analysis;

import org.cossbow.feng.ast.dcl.Declare;
import org.cossbow.feng.ast.dcl.DefinedTypeDeclarer;
import org.cossbow.feng.ast.dcl.FuncTypeDeclarer;
import org.cossbow.feng.ast.dcl.Referable;
import org.cossbow.feng.ast.expr.*;
import org.cossbow.feng.ast.oop.ClassField;
import org.cossbow.feng.ast.oop.ClassMethod;
import org.cossbow.feng.ast.struct.StructureField;
import org.cossbow.feng.visit.EntityVisitor;

import static org.cossbow.feng.util.ErrorUtil.semantic;
import static org.cossbow.feng.util.ErrorUtil.unreachable;

public class ConstChecker implements EntityVisitor<Boolean> {
    private final StackedContext context;
    private final TypeDeducer deducer;

    public ConstChecker(StackedContext context) {
        this.context = new StackedContext(context);
        this.deducer = new TypeDeducer(context);
    }


    @Override
    public Boolean visit(ReferExpression e) {
        var v = context.findVar(e.symbol());
        if (v.none()) return semantic("%s not declared", e.symbol());
        return v.get().declare() == Declare.CONST;
    }

    @Override
    public Boolean visit(ArrayExpression e) {
        return true;
    }

    @Override
    public Boolean visit(AssertExpression e) {
        if (!(e.type() instanceof DefinedTypeDeclarer td))
            return semantic("assert not allowed: %s", e.type());
        if (td.refer().none())
            return semantic("assert require reference");
        return td.refer().get().immutable();
    }

    @Override
    public Boolean visit(CallExpression e) {
        var td = deducer.visit(e.callee());
        if (!(td instanceof FuncTypeDeclarer ftd))
            return true;

        var rs = ftd.prototype().returnSet();
        if (rs.size() > 1) semantic("no multi-returns here");

        td = rs.getFirst();
        return switch (td) {
            case Referable r -> r.refer().none()
                    || r.refer().get().immutable();
            case null -> unreachable();
            default -> true;
        };
    }

    @Override
    public Boolean visit(CurrentExpression e) {
        return true;
    }

    @Override
    public Boolean visit(LiteralExpression e) {
        return true;
    }

    @Override
    public Boolean visit(IndexOfExpression e) {
        return visit(e.subject());
    }

    @Override
    public Boolean visit(MemberOfExpression e) {
        var m = deducer.getMember(e.subject(), e.member());

        if (m instanceof ClassMethod) return true;

        if (m instanceof ClassField cf) {
            if (cf.declare() == Declare.CONST)
                return true;
            return visit(e.subject());
        }

        if (m instanceof StructureField)
            return visit(e.subject());

        return unreachable();
    }

    @Override
    public Boolean visit(ParenExpression e) {
        return visit(e.child());
    }


}
