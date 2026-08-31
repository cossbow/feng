package org.cossbow.feng.ast.type;

import org.cossbow.feng.ast.*;
import org.cossbow.feng.ast.dcl.*;
import org.cossbow.feng.ast.gen.DerivedType;
import org.cossbow.feng.ast.gen.GenericMap;
import org.cossbow.feng.ast.gen.TypeArguments;
import org.cossbow.feng.ast.oop.*;
import org.cossbow.feng.ast.struct.StructureDefinition;
import org.cossbow.feng.ast.struct.StructureField;
import org.cossbow.feng.util.Optional;

import static org.cossbow.feng.util.ErrorUtil.semantic;
import static org.cossbow.feng.util.ErrorUtil.unreachable;

final
public class TypeTool {
    private TypeTool() {
    }

    static Optional<TypeArguments> checkInherited(
            DerivedType lt,
            ObjectDefinition rd, GenericMap next) {
        for (var st : rd.supers()) {
            var sd = (ObjectDefinition) st.def();
            var gm = st.gm().overlay(next);
            var tas = gm.mapAll(sd.generic());
            if (lt.symbol().equals(sd.symbol()))
                return Optional.of(tas);
            var o = checkInherited(lt, sd, gm);
            if (o.has())
                return o;
        }
        return Optional.empty();
    }

    public static Optional<TypeArguments> checkInherited(
            DerivedType lt, DerivedType rt) {

        if (!(lt.def() instanceof ObjectDefinition))
            return Optional.empty();

        if (!(rt.def() instanceof ObjectDefinition rd))
            return Optional.empty();

        return checkInherited(lt, rd, rt.gm());
    }

    //

    public static GenericMap gm(TypeDeclarer td) {
        if (td instanceof DerivedTypeDeclarer dtd) {
            return dtd.gm();
        }
        return GenericMap.EMPTY;
    }

    static ReadMap<Identifier, ? extends Field>
    allFields(TypeDeclarer td, Entity e) {
        if (td instanceof VoidTypeDeclarer) return unreachable();

        if (td instanceof GenericTypeDeclarer gtd) {
            if (gtd.param().constraint().has())
                return gtd.param().members().fields();
        } else if (td instanceof DerivedTypeDeclarer dtd) {
            var def = dtd.def();
            if (def instanceof StructureDefinition sd)
                return sd.fields();
            if (def instanceof ClassDefinition cd)
                return cd.allFields();
            if (def instanceof EnumDefinition ed)
                return ed.fields();
        } else if (td instanceof ArrayTypeDeclarer) {
            return ArrayTypeDeclarer.Fields;
        } else if (td instanceof EnumTypeDeclarer etd) {
            return etd.def().fields();
        }

        return new IdentifierMap<>();
    }

    public static Optional<? extends Field>
    fieldOf(TypeDeclarer td, Identifier name) {
        return allFields(td, name).tryGet(name)
                .map(f ->
                        mapField(gm(td), f));
    }

    public static ReadMap<Identifier, ? extends Field>
    fieldsOf(TypeDeclarer td, Entity e) {
        var gm = gm(td);
        var src = allFields(td, e);
        var dst = new IdentifierMap<Field>(src.size());
        for (var f : src) dst.add(f.name(), mapField(gm, f));
        return dst;
    }

    public static <F extends Field> F mapField(GenericMap gm, F f) {
        if (gm.isEmpty()) return f;

        var t = gm.mapIf(f.type());
        if (t == f.type()) return f;
        @SuppressWarnings("unchecked")
        var nf = (F) f.clone();
        nf.type(t);
        return nf;
    }

    public static <F extends Field> boolean
    hasRequiredInitOf(ReadMap<Identifier, F> fields) {
        for (var f : fields) {
            if (f instanceof ClassField ||
                    f instanceof StructureField)
                if (f.type().requiredInit()) return true;
        }
        return false;
    }

    //

    static ReadMap<Identifier, ? extends Method>
    allMethods(TypeDeclarer td, Entity e) {
        if (td instanceof VoidTypeDeclarer) return unreachable();

        if (td instanceof GenericTypeDeclarer gtd) {
            if (gtd.param().constraint().has())
                return gtd.param().members().methods();
        } else if (td instanceof DerivedTypeDeclarer dtd) {
            var def = dtd.def();
            if (def instanceof InterfaceDefinition sd)
                return sd.allMethods();
            if (def instanceof ClassDefinition cd)
                return cd.allMethods();
        } else if (td instanceof ArrayTypeDeclarer) {
            return ArrayTypeDeclarer.Methods;
        }

        return new IdentifierMap<>();
    }

    public static Optional<? extends Method>
    methodOf(TypeDeclarer td, Identifier name) {
        return allMethods(td, name).tryGet(name).map(
                m -> mapMethod(gm(td), m));
    }

    public static ReadMap<Identifier, ? extends Method>
    methodsOf(TypeDeclarer td, Entity e) {
        var gm = gm(td);
        var src = allMethods(td, e);
        var dst = new IdentifierMap<Method>(src.size());
        for (var m : src) dst.add(m.name(), mapMethod(gm, m));
        return dst;
    }

    public static Method mapMethod(GenericMap gm, Method m) {
        if (gm.isEmpty() || !m.prototype().hasTypeVar()) return m;

        var prot = gm.instantiate(m.prototype());
        if (m instanceof ClassMethod cm) {
            var n = new ClassMethod(cm.pos(), cm.modifier(),
                    cm.name(), cm.generic(), cm.escaped(),
                    cm.unmodifiable(), prot, cm.returnThis());
            n.master(cm.master());
            n.dynamic(cm.dynamic());
            cm.override().forEach(n.override()::add);
            return n;
        }
        if (m instanceof InterfaceMethod im) {
            return new InterfaceMethod(im.pos(), im.modifier(),
                    im.name(), im.generic(), im.escaped(),
                    im.unmodifiable(), prot, im.returnThis());
        }
        return m;
    }
}
