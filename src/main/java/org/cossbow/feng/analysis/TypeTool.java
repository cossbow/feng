package org.cossbow.feng.analysis;

import org.cossbow.feng.ast.Field;
import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.dcl.DefinedTypeDeclarer;
import org.cossbow.feng.ast.dcl.MemTypeDeclarer;
import org.cossbow.feng.ast.dcl.TypeDeclarer;
import org.cossbow.feng.ast.expr.Expression;
import org.cossbow.feng.ast.expr.PrimaryExpression;
import org.cossbow.feng.ast.oop.ClassDefinition;
import org.cossbow.feng.ast.oop.ClassField;
import org.cossbow.feng.ast.oop.ClassMethod;
import org.cossbow.feng.ast.oop.EnumDefinition;
import org.cossbow.feng.ast.struct.StructureDefinition;
import org.cossbow.feng.util.Groups;
import org.cossbow.feng.util.Optional;
import org.cossbow.feng.visit.SymbolContext;

import static org.cossbow.feng.util.ErrorUtil.semantic;
import static org.cossbow.feng.util.ErrorUtil.unsupported;

public class TypeTool {
    private final SymbolContext context;

    public TypeTool(SymbolContext context) {
        this.context = context;
    }

    private TypeDeclarer deduce(Expression e) {
        return new TypeDeducer(context).visit(e);
    }

    public Optional<ClassDefinition> getParent(ClassDefinition cd) {
        if (cd.parent().none())
            return Optional.empty();

        var pt = context.findType(cd.parent().must().symbol());
        if (pt.none())
            return semantic("class %s not defined", cd.parent().must());

        if (pt.must() instanceof ClassDefinition pcd)
            return Optional.of(pcd);

        return semantic("require class: %s", cd.parent().must());
    }

    public Optional<ClassField> getField(ClassDefinition cd, Identifier name) {
        var f = cd.fields().tryGet(name);
        if (f.has()) return f;

        var pcd = getParent(cd);
        if (pcd.has()) return getField(pcd.must(), name);

        return Optional.empty();
    }

    public Optional<Groups.G2<TypeDeclarer, Field>>
    getField(PrimaryExpression subject, Identifier name) {
        var std = deduce(subject);
        var mtd = std;
        if (mtd instanceof MemTypeDeclarer mem) {
            if (mem.mapped().none())
                return Optional.empty();
            mtd = mem.mapped().get();
        }
        if (!(mtd instanceof DefinedTypeDeclarer dtd))
            return Optional.empty();

        var dt = dtd.definedType();
        if (!dt.generic().isEmpty()) return unsupported("generic");

        var o = context.findType(dt.symbol());
        if (o.none()) return semantic(
                "undefined type: %s", dt.symbol());
        var def = o.get();

        if (def instanceof StructureDefinition sd) {
            var sf = sd.fields().tryGet(name);
            if (sf.has())
                return Optional.of(Groups.g2(std, sf.get()));
            return semantic("%s %s has no field %s",
                    sd.domain(), def.symbol(), name);
        }

        if (def instanceof ClassDefinition cd) {
            var f = getField(cd, name);
            if (f.has()) return Optional.of(Groups.g2(std, f.must()));
        }

        return Optional.empty();
    }

    public Optional<Groups.G2<DefinedTypeDeclarer, EnumDefinition.Value>>
    getEnum(PrimaryExpression subject, Identifier value) {
        var std = deduce(subject);
        if (!(std instanceof DefinedTypeDeclarer dtd))
            return Optional.empty();

        var o = context.findType(dtd.definedType().symbol());
        if (o.none()) return semantic(
                "undefined type: %s", subject.pos());

        if (o.must() instanceof EnumDefinition ed) {
            var v = ed.values().tryGet(value);
            if (v.has()) return Optional.of(Groups.g2(dtd, v.must()));
            return semantic("enum value not defined: %s",
                    value.pos());
        }

        return Optional.empty();
    }

    public Optional<ClassMethod> getMethod(ClassDefinition cd, Identifier name) {
        var f = cd.methods().tryGet(name);
        if (f.has()) return f;

        var pcd = getParent(cd);
        if (pcd.has()) return getMethod(pcd.must(), name);

        return semantic("class %s has no method %s",
                cd.symbol(), name);
    }

    public Optional<ClassMethod> getMethod(
            PrimaryExpression subject, Identifier name) {
        var std = deduce(subject);
        if (!(std instanceof DefinedTypeDeclarer dtd))
            return Optional.empty();

        var dt = dtd.definedType().symbol();

        var o = context.findType(dt);
        if (o.none()) return semantic("undefined type: %s", dt);

        if (!(o.must() instanceof ClassDefinition cd))
            return semantic("require class: %s", dt);

        return getMethod(cd, name);
    }


}
