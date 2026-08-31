package org.cossbow.feng.ast.type;

import org.cossbow.feng.ast.*;
import org.cossbow.feng.ast.attr.Attribute;
import org.cossbow.feng.ast.dcl.*;
import org.cossbow.feng.ast.gen.GenericMap;
import org.cossbow.feng.ast.oop.ClassDefinition;
import org.cossbow.feng.ast.oop.ClassMethod;
import org.cossbow.feng.ast.oop.InterfaceDefinition;
import org.cossbow.feng.ast.oop.InterfaceMethod;
import org.cossbow.feng.ast.struct.StructureDefinition;
import org.cossbow.feng.util.Optional;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * A projection of a {@link TypeDeclarer} that exposes only the type
 * *information* relevant to the analysis: the four orthogonal dimensions
 * (value-vs-refer, required-vs-optional, mutable-vs-unmod, newable) plus
 * the aggregate hasRequiredInit flag, the domain, attributes, fields
 * and methods.
 * <p>
 * A {@code TypeView} is <em>not</em> a full {@link TypeDeclarer} — it is
 * the view of a {@link org.cossbow.feng.ast.gen.TypeParameter} (or a
 * constraint), and it is never returned as a type: queries on it yield
 * field types / method signatures ({@link TypeDeclarer} instances),
 * never the view itself.
 * <p>
 * The four dimensions are {@link Boolean} tri-states: {@code null} means
 * unknown (not extractable), {@code false}/{@code true} mean definitely
 * the negative/positive value. {@link #unknown()} is the "no common
 * feature" sentinel, produced e.g. by {@code struct | func} where the two
 * views share nothing.
 */
public class TypeView implements
        Aggregatable<Field>, Abstractable<Method> {

    // ──────────────── Four orthogonal dimensions (null = unknown) ────────────────

    private final Boolean isRefer;       // false=value, true=refer
    private final TypeDomain domain;
    private final Boolean optional;      // false=required, true=nullable
    private final Boolean unmodifiable;  // false=mutable, true=unmod
    private final Boolean newable;       // true=can new (construct an instance)
    private final boolean hasRequiredInit; // true=some member carries a mandatory init

    private final Set<Attribute> attributes;
    private final IdentifierMap<Field> fields;
    private final IdentifierMap<Method> methods;
    private final Optional<ClassDefinition> parent;

    private static final Set<Attribute> NO_ATTRS = Set.of();
    private static final IdentifierMap<Field> NO_FIELDS = new IdentifierMap<>();
    private static final IdentifierMap<Method> NO_METHODS = new IdentifierMap<>();

    private static final TypeView UNKNOWN_VIEW =
            new TypeView(null, null, null, null, null, false,
                    NO_ATTRS, NO_FIELDS, NO_METHODS, Optional.empty());

    private TypeView(Boolean isRefer,
                     TypeDomain domain,
                     Boolean optional,
                     Boolean unmodifiable,
                     Boolean newable,
                     boolean hasRequiredInit,
                     Set<Attribute> attributes,
                     IdentifierMap<Field> fields,
                     IdentifierMap<Method> methods,
                     Optional<ClassDefinition> parent) {
        this.isRefer = isRefer;
        this.domain = domain;
        this.optional = optional;
        this.unmodifiable = unmodifiable;
        this.newable = newable;
        this.hasRequiredInit = hasRequiredInit;
        this.attributes = attributes;
        this.fields = fields;
        this.methods = methods;
        this.parent = parent;
    }

    public Optional<ClassDefinition> parent() {
        return parent;
    }

    // ──────────────── public API ────────────────

    public Boolean isRefer() {
        return isRefer;
    }

    public TypeDomain domain() {
        return domain;
    }

    /**
     * Whether this view definitely represents a nullable type.
     */
    public Boolean optional() {
        return optional;
    }

    /**
     * Whether this view definitely represents an unmodifiable type.
     */
    public Boolean unmodifiable() {
        return unmodifiable;
    }

    /**
     * Whether this view definitely represents a newable type.
     */
    public Boolean newable() {
        return newable;
    }

    /**
     * Whether some member of this view carries a mandatory init
     * (a field whose type requires initialization). OR-combined by
     * union/intersection: the field info may be dropped from the
     * view (e.g. a union mixing a required-field class with a plain
     * one), but the init requirement survives.
     */
    public boolean hasRequiredInit() {
        return hasRequiredInit;
    }

    /**
     * Access the field with the given name, if it is guaranteed by this view.
     */
    public Optional<Field> field(Identifier name) {
        return fields.tryGet(name);
    }

    /**
     * Access the method with the given name, if it is guaranteed by this view.
     */
    public Optional<Method> method(Identifier name) {
        return methods.tryGet(name);
    }

    /**
     * Whether this view definitely carries the given attribute.
     */
    public boolean hasAttribute(Symbol type) {
        return attributes.stream().anyMatch(a -> a.type().equals(type));
    }

    public Set<Attribute> attributes() {
        return attributes;
    }

    public IdentifierMap<Field> fields() {
        return fields;
    }

    public IdentifierMap<Method> methods() {
        return methods;
    }

    // ──────────────── Factory methods ────────────────

    /**
     * Return the sentinel "unknown" view: no dimension carries any
     * information, produced when no common feature could be extracted.
     */
    public static TypeView unknown() {
        return UNKNOWN_VIEW;
    }

    /**
     * Build a view that only carries a refer status.
     */
    public static TypeView refer(Boolean isRefer) {
        return new TypeView(isRefer, null, null, null, null, false,
                NO_ATTRS, NO_FIELDS, NO_METHODS, Optional.empty());
    }

    /**
     * Build a view that only carries a nullability status.
     */
    public static TypeView optional(Boolean optional) {
        return new TypeView(null, null, optional, null, null, false,
                NO_ATTRS, NO_FIELDS, NO_METHODS, Optional.empty());
    }

    /**
     * Build a view that only carries an unmodifiable status.
     */
    public static TypeView unmodifiable(Boolean unmod) {
        return new TypeView(null, null, null, unmod, null, false,
                NO_ATTRS, NO_FIELDS, NO_METHODS, Optional.empty());
    }

    /**
     * Build a view that only carries an attribute constraint.
     */
    public static TypeView attribute(Attribute attr) {
        return new TypeView(null, null, null, null, null, false,
                Set.of(attr), NO_FIELDS, NO_METHODS, Optional.empty());
    }

    /**
     * Build a view from a syntactic {@link Refer} object, decomposing
     * it into the three orthogonal dimensions. A present refer is
     * always a reference; {@code ?} maps to optional, {@code #} to unmod.
     */
    public static TypeView ofRefer(Refer refer) {
        return new TypeView(
                true, null,
                !refer.required(), refer.unmodifiable(), null, false,
                NO_ATTRS, NO_FIELDS, NO_METHODS, Optional.empty());
    }

    /**
     * Build a view that only carries a domain constraint.
     * <p>
     * Primitive / struct / union / enum domains inherently imply a value
     * type; interface / class / func imply unknown.
     */
    public static TypeView domain(TypeDomain domain) {
        return new TypeView(switch (domain) {
            case PRIMITIVE, STRUCT, UNION, ENUM -> false;
            case ATTRIBUTE, INTERFACE, CLASS, FUNC -> null;
        }, domain, null, null, null, false,
                NO_ATTRS, NO_FIELDS, NO_METHODS, Optional.empty());
    }

    /**
     * Build the complete view of a concrete {@link TypeDeclarer},
     * extracting the dimensions, domain, attributes, fields and methods.
     * <p>
     * This is the "full view" used during constraint checking.
     */
    public static TypeView of(TypeDeclarer t) {
        return new TypeView(
                isReferOf(t), domainOf(t), optionalOf(t), unmodifiableOf(t),
                newableOf(t), hasRequiredInitOf(t), attributesOf(t),
                fieldsOf(t), methodsOf(t), parentOf(t));
    }

    // ──────────────── Extraction helpers ────────────────

    private static Boolean isReferOf(TypeDeclarer t) {
        var r = t.maybeRefer();
        if (r.none()) return false; // no refer: value type
        return true;
    }

    private static Boolean optionalOf(TypeDeclarer t) {
        var r = t.maybeRefer();
        if (r.none()) return null;
        return !r.get().required();
    }

    private static Boolean unmodifiableOf(TypeDeclarer t) {
        var r = t.maybeRefer();
        if (r.none()) return null;
        return r.get().unmodifiable();
    }

    /**
     * Whether an instance of the type can be constructed at all: a
     * property of the type definition itself (classes/structs/enums/
     * arrays/primitives are constructible; interfaces/attributes/funcs
     * are not). Whether the construction may omit arguments is a
     * separate question answered by {@link #hasRequiredInit}.
     */
    private static Boolean newableOf(TypeDeclarer t) {
        if (t instanceof DerivedTypeDeclarer d) {
            return d.def().newable();
        }
        if (t instanceof PrimitiveTypeDeclarer) {
            return true;
        }
        if (t instanceof EnumTypeDeclarer || t instanceof ArrayTypeDeclarer) {
            return true;
        }
        return null;
    }

    /**
     * Whether some member of the type carries a mandatory init: a field
     * whose (generic-mapped) type requires initialization. This is the
     * view-level counterpart of {@code requiredInit()} on the concrete
     * declarer — it decides whether the init argument may be omitted.
     */
    private static boolean hasRequiredInitOf(TypeDeclarer t) {
        for (var f : fieldsOf(t)) {
            if (f.type().requiredInit()) return true;
        }
        return false;
    }

    private static TypeDomain domainOf(TypeDeclarer t) {
        if (t instanceof PrimitiveTypeDeclarer p) {
            return p.primitive().type().domain();
        }
        if (t instanceof DerivedTypeDeclarer d) {
            return d.def().domain();
        }
        if (t instanceof DefinitionDeclarer d) {
            return d.def().domain();
        }
        if (t instanceof EnumTypeDeclarer d) {
            return d.def().domain();
        }
        if (t instanceof ArrayTypeDeclarer) {
            return TypeDomain.STRUCT; // arrays modeled as structs
        }
        if (t instanceof FuncTypeDeclarer) {
            return TypeDomain.FUNC;
        }
        if (t instanceof TupleTypeDeclarer) {
            return TypeDomain.STRUCT;
        }
        return null; // void and unknown have no domain
    }

    // ──────────────── Field/method/attribute extraction ────────────────

    private static Set<Attribute> attributesOf(TypeDeclarer t) {
        var attrs = maybeAttributes(t);
        if (attrs.isEmpty()) return NO_ATTRS;
        var set = new HashSet<Attribute>(attrs.size());
        attrs.each(set::add);
        return set;
    }

    private static SymbolMap<Attribute> maybeAttributes(TypeDeclarer t) {
        if (t instanceof DerivedTypeDeclarer d) {
            return d.def().modifier().attributes();
        }
        if (t instanceof EnumTypeDeclarer e) {
            return e.def().modifier().attributes();
        }
        if (t instanceof DefinitionDeclarer d) {
            return d.def().modifier().attributes();
        }
        return new SymbolMap<>();
    }

    private static IdentifierMap<Field> fieldsOf(TypeDeclarer t) {
        if (t instanceof DerivedTypeDeclarer d) {
            var def = d.def();
            var gm = d.gm();
            var out = new IdentifierMap<Field>();
            if (def instanceof ClassDefinition c) {
                for (var f : c.allFields()) out.add(f.name(), mapField(gm, f));
            } else if (def instanceof StructureDefinition s) {
                for (var f : s.fields()) out.add(f.name(), mapField(gm, f));
            }
            return out;
        }
        if (t instanceof ArrayTypeDeclarer) {
            var out = new IdentifierMap<Field>();
            out.add(ArrayTypeDeclarer.FieldLength.name(), ArrayTypeDeclarer.FieldLength);
            out.add(ArrayTypeDeclarer.FieldValues.name(), ArrayTypeDeclarer.FieldValues);
            return out;
        }
        return NO_FIELDS;
    }

    /**
     * Instantiate the field type through the generic mapping of the
     * declaring type (e.g. field {@code var l L;} in {@code C`B, L`}
     * maps {@code L} to the actual type argument). Returns the original
     * field when no mapping is needed.
     */
    private static Field mapField(GenericMap gm, Field f) {
        var t = gm.mapIf(f.type());
        if (t == f.type()) return f;
        var nf = f.clone();
        nf.type(t);
        return nf;
    }

    private static IdentifierMap<Method> methodsOf(TypeDeclarer t) {
        if (t instanceof DerivedTypeDeclarer d) {
            var def = d.def();
            var gm = d.gm();
            var out = new IdentifierMap<Method>();
            if (def instanceof ClassDefinition c) {
                for (var m : c.allMethods()) out.add(m.name(), mapMethod(gm, m));
            } else if (def instanceof InterfaceDefinition i) {
                for (var m : i.allMethods()) out.add(m.name(), mapMethod(gm, m));
            }
            return out;
        }
        if (t instanceof ArrayTypeDeclarer) {
            var out = new IdentifierMap<Method>();
            var swap = ArrayTypeDeclarer.methodOf(ArrayTypeDeclarer.MethodSwap.name());
            var move = ArrayTypeDeclarer.methodOf(ArrayTypeDeclarer.MethodMove.name());
            if (swap.has()) out.add(swap.get().name(), swap.get());
            if (move.has()) out.add(move.get().name(), move.get());
            return out;
        }
        return NO_METHODS;
    }

    /**
     * Instantiate the method prototype through the generic mapping of the
     * declaring type, preserving identity (master / override / dynamic).
     * Returns the original method when no mapping is needed.
     */
    private static Method mapMethod(GenericMap gm, Method m) {
        if (gm.isEmpty() || !m.prototype().hasTypeVar()) return m;
        var prot = gm.instantiate(m.prototype());
        if (m instanceof ClassMethod cm) {
            var n = new ClassMethod(cm.pos(), cm.modifier(), cm.name(), cm.generic(),
                    cm.escaped(), cm.unmodifiable(), prot, cm.returnThis());
            n.master(cm.master());
            n.dynamic(cm.dynamic());
            cm.override().forEach(n.override()::add);
            return n;
        }
        if (m instanceof InterfaceMethod im) {
            return new InterfaceMethod(im.pos(), im.modifier(), im.name(), im.generic(),
                    im.escaped(), im.unmodifiable(), prot, im.returnThis());
        }
        return m;
    }

    private static Optional<ClassDefinition> parentOf(TypeDeclarer t) {
        if (t instanceof DerivedTypeDeclarer d && d.def() instanceof ClassDefinition c) {
            return c.parent().get();
        }
        return Optional.empty();
    }

    // ──────────────── Combinators ────────────────

    /**
     * AND-combine: what both views <em>guarantee</em>. A value survives
     * only when both sides agree on it; any unknown side contributes
     * nothing (e.g. {@code struct | func} has no common feature).
     */
    static Boolean and(Boolean a, Boolean b) {
        return a != null && a.equals(b) ? a : null;
    }

    /**
     * OR-combine: what the combined view <em>must be</em>. An unknown
     * side is absorbed by a definite one; conflicting values yield unknown.
     */
    static Boolean or(Boolean a, Boolean b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.equals(b) ? a : null;
    }

    static TypeDomain andDomain(TypeDomain a, TypeDomain b) {
        return a == b ? a : null;
    }

    static TypeDomain orDomain(TypeDomain a, TypeDomain b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.equals(b) ? a : null;
    }

    /**
     * Union — the result is the <em>widest</em> view carrying only the
     * common features of the two views: a dimension survives only if both
     * views guarantee it; fields/methods survive only if both have the
     * same member.
     */
    public TypeView union(TypeView other) {
        return new TypeView(and(isRefer, other.isRefer),
                andDomain(domain, other.domain),
                and(optional, other.optional),
                and(unmodifiable, other.unmodifiable),
                and(newable, other.newable),
                hasRequiredInit || other.hasRequiredInit,
                intersectAttributes(attributes, other.attributes),
                intersectFields(fields, other.fields),
                intersectMethods(methods, other.methods),
                intersectParent(parent, other.parent));
    }

    /**
     * Intersection — the result is the <em>tighter</em> view that the
     * combined type must satisfy: a dimension is definite if either view
     * makes it definite; fields/methods are the union, dropping names
     * with conflicting types/signatures.
     */
    public TypeView intersect(TypeView other) {
        return new TypeView(or(isRefer, other.isRefer),
                orDomain(domain, other.domain),
                or(optional, other.optional),
                or(unmodifiable, other.unmodifiable),
                or(newable, other.newable),
                hasRequiredInit || other.hasRequiredInit,
                unionAttributes(attributes, other.attributes),
                unionFields(fields, other.fields),
                unionMethods(methods, other.methods),
                intersectParent(parent, other.parent));
    }

    // ──────────────── Combinator helpers ────────────────

    private static Set<Attribute>
    intersectAttributes(Set<Attribute> a, Set<Attribute> b) {
        if (a.isEmpty()) return b;
        if (b.isEmpty()) return a;
        var r = new HashSet<>(a);
        r.retainAll(b);
        return r.isEmpty() ? NO_ATTRS : r;
    }

    private static Set<Attribute>
    unionAttributes(Set<Attribute> a, Set<Attribute> b) {
        if (a.isEmpty()) return b;
        if (b.isEmpty()) return a;
        var r = new HashSet<>(a);
        r.addAll(b);
        return r;
    }

    private static boolean sameView(Field fa, Field fb) {
        return fa.type().equals(fb.type()) &&
                fa.type().sync() == fb.type().sync() &&
                fa.immutable() == fb.immutable() &&
                fa.modifier().equals(fb.modifier());
    }

    private static IdentifierMap<Field>
    intersectFields(IdentifierMap<Field> a, IdentifierMap<Field> b) {
        // Union: a member is guaranteed only when both sides have it; an
        // empty side (e.g. int) contributes nothing.
        if (a.isEmpty() || b.isEmpty()) return NO_FIELDS;
        var r = new IdentifierMap<Field>();
        for (var f : a) {
            var o = b.tryGet(f.name());
            if (o.has() && sameView(o.get(), f)) r.add(f.name(), f);
        }
        return r;
    }

    private static IdentifierMap<Field>
    unionFields(IdentifierMap<Field> a, IdentifierMap<Field> b) {
        if (a.isEmpty()) return b;
        if (b.isEmpty()) return a;
        var r = new IdentifierMap<Field>();
        var names = new LinkedHashSet<Identifier>();
        a.each(f -> names.add(f.name()));
        b.each(f -> names.add(f.name()));
        for (var name : names) {
            var fa = a.tryGet(name);
            var fb = b.tryGet(name);
            if (fa.none()) {
                r.add(name, fb.get());
                continue;
            }
            if (fb.none()) {
                r.add(name, fa.get());
                continue;
            }

            if (sameView(fa.get(), fb.get())) {
                r.add(name, fa.get());
            }
            // 同名不同类型：无保证，剔除
        }
        return r;
    }

    private static boolean sameView(Method fa, Method fb) {
        return fa.prototype().equals(fb.prototype()) &&
                fa.escaped() == fb.escaped() &&
                fa.unmodifiable() == fb.unmodifiable() &&
                fa.modifier().equals(fb.modifier());
    }

    private static IdentifierMap<Method>
    intersectMethods(IdentifierMap<Method> a, IdentifierMap<Method> b) {
        // Union: a member is guaranteed only when both sides have it.
        if (a.isEmpty() || b.isEmpty()) return NO_METHODS;
        var r = new IdentifierMap<Method>();
        for (var m : a) {
            var o = b.tryGet(m.name());
            if (o.has() && sameView(o.get(), m))
                r.add(m.name(), m);
        }
        return r;
    }

    private static IdentifierMap<Method>
    unionMethods(IdentifierMap<Method> a, IdentifierMap<Method> b) {
        if (a.isEmpty()) return b;
        if (b.isEmpty()) return a;
        var r = new IdentifierMap<Method>();
        var names = new LinkedHashSet<Identifier>();
        a.each(m -> names.add(m.name()));
        b.each(m -> names.add(m.name()));
        for (var name : names) {
            var ma = a.tryGet(name);
            var mb = b.tryGet(name);
            if (ma.none()) {
                r.add(name, mb.get());
                continue;
            }
            if (mb.none()) {
                r.add(name, ma.get());
                continue;
            }
            if (sameView(ma.get(), mb.get()))
                r.add(name, ma.get());
            // 同名不同签名：无保证，剔除
        }
        return r;
    }

    private static Optional<ClassDefinition>
    intersectParent(Optional<ClassDefinition> a, Optional<ClassDefinition> b) {
        if (a.none()) return b;
        if (b.none()) return a;
        return a.get().equals(b.get()) ? a : Optional.empty();
    }


    // ──────────────── equals / hashCode / toString ────────────────

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof TypeView that)) return false;
        return Objects.equals(isRefer, that.isRefer)
                && domain == that.domain
                && Objects.equals(optional, that.optional)
                && Objects.equals(unmodifiable, that.unmodifiable)
                && Objects.equals(newable, that.newable)
                && hasRequiredInit == that.hasRequiredInit;
    }

    @Override
    public int hashCode() {
        int result = Objects.hashCode(isRefer);
        result = 31 * result + (domain != null ? domain.hashCode() : 0);
        result = 31 * result + Objects.hashCode(optional);
        result = 31 * result + Objects.hashCode(unmodifiable);
        result = 31 * result + Objects.hashCode(newable);
        result = 31 * result + Boolean.hashCode(hasRequiredInit);
        return result;
    }

    //
    @Override
    public String toString() {
        var sb = new StringBuilder("View(");
        if (isRefer != null) sb.append("isRefer=").append(isRefer).append(", ");
        if (domain != null) sb.append(domain).append(", ");
        if (optional != null) sb.append("optional=").append(optional).append(", ");
        if (unmodifiable != null) sb.append("unmod=").append(unmodifiable).append(", ");
        if (newable != null) sb.append("newable=").append(newable).append(", ");
        if (hasRequiredInit) sb.append("hasRequiredInit, ");
        if (sb.length() > 5) sb.setLength(sb.length() - 2);
        sb.append(")");
        return sb.toString();
    }
}
