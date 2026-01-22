package org.cossbow.feng.analysis;

import org.cossbow.feng.ast.*;
import org.cossbow.feng.ast.dcl.*;
import org.cossbow.feng.ast.expr.Expression;
import org.cossbow.feng.ast.expr.PrimaryExpression;
import org.cossbow.feng.ast.oop.*;
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

    public Optional<Groups.G2<TypeDeclarer, Field>>
    getField(PrimaryExpression subject, Identifier name) {
        var std = deduce(subject);

        if (std instanceof ArrayTypeDeclarer atd) {
            var af = atd.getField(name);
            if (af.has()) return af.map(f -> Groups.g2(atd, f));
            return semantic("array has no field %s: %s", name, name.pos());
        }

        var mtd = std;
        if (mtd instanceof MemTypeDeclarer mem) {
            if (mem.mapped().none())
                return Optional.empty();
            mtd = mem.mapped().get();
        }
        if (!(mtd instanceof DerivedTypeDeclarer dtd))
            return Optional.empty();

        var dt = dtd.derivedType();
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
            var f = cd.allFields().tryGet(name);
            if (f.has()) return Optional.of(Groups.g2(std, f.must()));
        }

        return Optional.empty();
    }

    public Optional<Groups.G2<DerivedTypeDeclarer, EnumDefinition.Value>>
    getEnum(PrimaryExpression subject, Identifier value) {
        var std = deduce(subject);
        if (!(std instanceof DerivedTypeDeclarer dtd))
            return Optional.empty();

        var o = context.findType(dtd.derivedType().symbol());
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

    public Optional<? extends Entity>
    getMethod(InterfaceDefinition id, Identifier name) {
        var m = id.all().tryGet(name);
        if (m.has()) return m;
        return semantic("interface %s has no method: %s",
                id.symbol(), name);
    }

    public Optional<? extends Entity> getMethod(
            PrimaryExpression subject, Identifier name) {
        var std = deduce(subject);
        if (!(std instanceof DerivedTypeDeclarer dtd))
            return Optional.empty();

        var dt = dtd.derivedType().symbol();

        var o = context.findType(dt);
        if (o.none()) return semantic("undefined type: %s", dt);

        if (o.must() instanceof ClassDefinition cd)
            return cd.allMethods().tryGet(name);

        if (o.must() instanceof InterfaceDefinition id) {
            return getMethod(id, name);
        }

        return semantic("require class: %s", dt);

    }

    public Optional<ObjectDefinition> getObject(TypeDeclarer td) {
        if (!(td instanceof DerivedTypeDeclarer dtd))
            return Optional.empty();

        var dt = dtd.derivedType();
        assert dt.generic().isEmpty();
        var def = context.findType(dt.symbol());
        if (!def.has()) return Optional.empty();

        var t = def.get();
        if (t instanceof ClassDefinition cd)
            return Optional.of(cd);
        if (t instanceof InterfaceDefinition id)
            return Optional.of(id);

        return Optional.empty();
    }

    //

    public static Optional<Primitive.Kind>
    primitiveKind(TypeDeclarer td) {
        if (td instanceof PrimitiveTypeDeclarer ptd)
            return Optional.of(ptd.primitive().kind);
        if (td instanceof LiteralTypeDeclarer ltd)
            return ltd.literal().compatible();
        return Optional.empty();
    }

}
