package org.cossbow.feng.analysis;

import org.cossbow.feng.ast.*;
import org.cossbow.feng.ast.dcl.*;
import org.cossbow.feng.ast.expr.Expression;
import org.cossbow.feng.ast.expr.PrimaryExpression;
import org.cossbow.feng.ast.gen.DerivedType;
import org.cossbow.feng.ast.gen.TypeArguments;
import org.cossbow.feng.ast.lit.IntegerLiteral;
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
            if (mem.mapped().none()) {
                var md = MemDefinition.get(mem.readonly());
                return md.getField(name).map(f -> Groups.g2(std, f));
            }
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

        Optional<? extends Field> of = switch (def) {
            case StructureDefinition sd -> sd.fields().tryGet(name);
            case ClassDefinition cd -> cd.allFields().tryGet(name);
            case EnumDefinition ed -> ed.getField(name);
            case null, default -> Optional.empty();
        };
        return of.map(f -> Groups.g2(std, f));
    }

    public Optional<Groups.G2<DerivedTypeDeclarer, EnumDefinition.Value>>
    getEnum(PrimaryExpression subject, Identifier value) {
        var std = deduce(subject);
        if (!(std instanceof DefinitionTypeDeclarer dtd))
            return Optional.empty();

        if (!(dtd.definition() instanceof EnumDefinition ed))
            return Optional.empty();

        var vo = ed.values().tryGet(value);
        if (vo.none()) return Optional.empty();

        var v = vo.get();
        return Optional.of(Groups.g2(new DerivedTypeDeclarer(value.pos(),
                new DerivedType(std.pos(), ed.symbol(), TypeArguments.EMPTY),
                Optional.empty()), v));
    }

    public Optional<? extends Entity> getMethod(
            PrimaryExpression subject, Identifier name) {
        var std = deduce(subject);
        if (!(std instanceof DerivedTypeDeclarer dtd))
            return Optional.empty();

        var dt = dtd.derivedType().symbol();

        var o = context.findType(dt);
        if (o.none()) return semantic("undefined type: %s", dt);

        return switch (o.must()) {
            case ClassDefinition cd -> cd.allMethods().tryGet(name);
            case InterfaceDefinition id -> id.all().tryGet(name);
            default -> Optional.empty();
        };
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
