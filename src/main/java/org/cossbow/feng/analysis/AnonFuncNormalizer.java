package org.cossbow.feng.analysis;

import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.Symbol;
import org.cossbow.feng.ast.attr.Modifier;
import org.cossbow.feng.ast.dcl.*;
import org.cossbow.feng.ast.expr.*;
import org.cossbow.feng.ast.gen.TypeArguments;
import org.cossbow.feng.ast.gen.TypeParameters;
import org.cossbow.feng.ast.oop.ClassDefinition;
import org.cossbow.feng.ast.proc.*;
import org.cossbow.feng.ast.stmt.*;
import org.cossbow.feng.ast.var.*;
import org.cossbow.feng.dag.DAGGraph;
import org.cossbow.feng.util.ErrorUtil;
import org.cossbow.feng.util.Groups;
import org.cossbow.feng.util.Lazy;
import org.cossbow.feng.util.Optional;

import java.util.*;

import static org.cossbow.feng.ast.Position.ZERO;

/**
 * AST normalization pass that converts all {@link AnonFuncTypeDeclarer}
 * (anonymous function types like {@code func(int)bool}) into
 * {@link NamedFuncTypeDeclarer} backed by auto-generated
 * {@link PrototypeDefinition} entries.
 * <p>
 * Deduplication is by structural signature: return type + parameter
 * types. Two {@code func(int)bool} in different places map to the
 * same {@code PrototypeDefinition}.
 */
public class AnonFuncNormalizer {

    private final AnalyseSymbolTable ast;
    private final Map<String, PrototypeDefinition> protoMap = new LinkedHashMap<>();

    public AnonFuncNormalizer(AnalyseSymbolTable ast) {
        this.ast = ast;
    }

    public void normalize() {
        collectFromClasses();
        collectFromStructures();
        collectFromFunctions();
        collectFromGlobals();
        if (protoMap.isEmpty()) return;

        var allProtos = new ArrayList<>(
                ast.dagPrototypes.all());
        allProtos.addAll(protoMap.values());

        // Build dependency edges: if prototype A's return/param types
        // reference prototype B, add edge B→A so B is declared first.
        // Skip generic prototypes — they have type vars and won't be
        // emitted as typedefs (filtered by declareProtoTypedefs later).
        var keyToProto = new HashMap<String, PrototypeDefinition>();
        for (var pd : allProtos) {
            if (pd.prototype().hasTypeVar()) continue;
            keyToProto.put(protoKey(pd.prototype()), pd);
        }
        var edges = new ArrayList<Groups.G2<PrototypeDefinition, PrototypeDefinition>>();
        for (var pd : allProtos) {
            if (pd.prototype().hasTypeVar()) continue;
            for (var depKey : protoDeps(pd.prototype())) {
                var dep = keyToProto.get(depKey);
                if (dep != null && dep != pd) {
                    edges.add(Groups.g2(dep, pd));
                }
            }
        }

        ast.dagPrototypes = DAGGraph.make(allProtos, edges);
    }

    /**
     * Collect proto keys of {@link FuncTypeDeclarer} types nested inside
     * a prototype's return type and parameter types.
     */
    private Set<String> protoDeps(Prototype pt) {
        var deps = new HashSet<String>();
        pt.returnSet().use(td -> collectProtoDeps(td, deps));
        for (var p : pt.parameterSet()) {
            if (p instanceof FixedParameter fp) {
                collectProtoDeps(fp.type(), deps);
            }
        }
        return deps;
    }

    private void collectProtoDeps(TypeDeclarer td, Set<String> keys) {
        if (td instanceof FuncTypeDeclarer ftd) {
            keys.add(protoKey(ftd.prototype()));
            return;
        }
        if (td instanceof ArrayTypeDeclarer atd) {
            collectProtoDeps(atd.element(), keys);
        } else if (td instanceof TupleTypeDeclarer ttd) {
            for (var t : ttd.elements()) {
                collectProtoDeps(t, keys);
            }
        }
    }

    // ---- classes ----

    private void collectFromClasses() {
        for (var cd : ast.dagClasses) {
            for (var cf : cd.fields().values()) {
                cf.type(replaceAnon(cf.type()));
            }
            for (var cm : cd.methods()) {
                normalizePrototype(cm.prototype());
                cm.procedure().use(this::walkProcedure);
            }
        }
    }

    private void normalizePrototype(Prototype pt) {
        pt.returnSet().use(ret -> {
            var newRet = replaceAnon(ret);
            if (newRet != ret) pt.returnSet(Optional.of(newRet));
        });
        for (var p : pt.parameterSet()) {
            if (p instanceof FixedParameter fp) {
                var newType = replaceAnon(fp.type());
                if (newType != fp.type()) fp.type(newType);
            }
        }
    }

    // ---- structures ----

    private void collectFromStructures() {
        for (var sd : ast.dagStructures) {
            for (var sf : sd.fields()) {
                sf.type(replaceAnon(sf.type()));
            }
        }
    }

    // ---- functions ----

    private void collectFromFunctions() {
        for (var fd : ast.functionList) {
            if (fd.builtin()) continue;
            normalizePrototype(fd.prototype());
            fd.procedure().use(this::walkProcedure);
        }
        ast.main.use(fd -> {
            normalizePrototype(fd.prototype());
            fd.procedure().use(this::walkProcedure);
        });
    }

    // ---- globals ----

    private void collectFromGlobals() {
        // constVars is constants that know in analyzing
        for (var gv : ast.dagVars) {
            gv.type().set(replaceAnon(gv.type().must()));
            gv.value().use(this::walkExpr);
        }
    }

    // ---- procedure / statement walk ----

    private void walkProcedure(Procedure proc) {
        walkBlockStmt(proc.body());
    }

    private void walkBlockStmt(BlockStatement bs) {
        for (var s : bs.list()) walkStmt(s);
    }

    private void walkStmt(Statement s) {
        switch (s) {
            case DeclarationStatement ds -> {
                for (var v : ds.variables()) {
                    v.type().set(replaceAnon(v.type().must()));
                    v.value().use(this::walkExpr);
                }
            }
            case BlockStatement bs -> walkBlockStmt(bs);
            case CallStatement cs -> walkExpr(cs.call());
            case ReturnStatement rs -> rs.result().use(this::walkExpr);
            case IfStatement is -> {
                is.init().use(this::walkStmt);
                walkExpr(is.condition());
                walkStmt(is.yes());
                is.not().use(this::walkStmt);
            }
            case ForStatement fs -> {
                if (fs instanceof ConditionalForStatement cfs) {
                    cfs.initializer().use(this::walkStmt);
                    walkExpr(cfs.condition());
                    walkStmt(cfs.body());
                }
            }
            case SwitchStatement ss -> {
                for (var br : ss.branches()) walkStmt(br);
            }
            case AssignmentsStatement as -> {
                for (int i = 0; i < as.list().size(); i++)
                    walkExpr(as.value(i));
            }
            case SwitchBranch sb -> walkBlockStmt(sb.body());
            case TryStatement ts -> {
                walkStmt(ts.body());
                for (var cc : ts.catchClauses()) {
                    cc.argument().type().set(
                            replaceAnon(cc.argument().type().must()));
                    walkStmt(cc.body());
                }
                ts.finallyClause().use(this::walkStmt);
            }
            default -> {
            }
        }
    }

    // ---- expression walk ----

    private void walkExpr(Expression e) {
        switch (e) {
            case CallExpression ce -> {
                walkExpr(ce.callee());
                for (var a : ce.arguments()) walkExpr(a);
            }
            case NewExpression ne -> {
                // NewArrayType has element type that may contain AnonFunc
                if (ne.type() instanceof NewArrayType nat) {
                    nat.element(replaceAnon(nat.element()));
                }
                ne.arg().use(this::walkExpr);
            }
            case ConvertExpression ce -> walkExpr(ce.operand());
            case ObjectExpression oe -> {
                for (var val : oe.entries().values()) walkExpr(val);
            }
            case ArrayExpression ae -> {
                ae.type().use(atd -> atd.element(
                        replaceAnon(atd.element())));
                for (var item : ae.elements()) walkExpr(item);
            }
            case BinaryExpression be -> {
                walkExpr(be.left());
                walkExpr(be.right());
            }
            case UnaryExpression ue -> walkExpr(ue.operand());
            case ConditionalExpression ce -> {
                walkExpr(ce.condition());
                walkExpr(ce.yes());
                walkExpr(ce.not());
            }
            case BlockExpression be -> {
                for (var st : be.block()) walkStmt(st);
                walkExpr(be.result());
            }
            case IsExpression ie -> walkExpr(ie.subject());  // only class/interface, no func types
            case MemberOfExpression moe -> walkExpr(moe.subject());
            case IndexOfExpression ioe -> walkExpr(ioe.subject());
            case TupleExpression te -> {
                for (var item : te.elements()) walkExpr(item);
            }
            default -> {
            }
        }
    }

    // ---- recursive type replacement ----

    private TypeDeclarer replaceAnon(TypeDeclarer td) {
        if (td == null) return null;
        return switch (td) {
            case AnonFuncTypeDeclarer a -> makeNamed(a);
            case DerivedTypeDeclarer dtd -> {
                var dt = dtd.derivedType();
                var newArgs = new ArrayList<TypeDeclarer>();
                boolean changed = false;
                for (var ga : dt.generic()) {
                    var na = replaceAnon(ga);
                    newArgs.add(na);
                    if (na != ga) changed = true;
                }
                if (!changed) yield dtd;
                var newDt = dt.clone();
                newDt.generic(new TypeArguments(dt.pos(), newArgs));
                yield new DerivedTypeDeclarer(dtd.pos(), newDt, dtd.refer());
            }
            case ArrayTypeDeclarer atd -> {
                var el = replaceAnon(atd.element());
                if (el == atd.element()) yield atd;
                var na = new ArrayTypeDeclarer(atd.pos(), el,
                        atd.length(), atd.refer(), atd.literal());
                if (atd.len() != null) na.len(atd.len());
                if (atd.unit() != null) na.unit(atd.unit());
                yield na;
            }
            case TupleTypeDeclarer ttd -> {
                var newElems = ttd.elements().stream()
                        .map(this::replaceAnon).toList();
                yield new TupleTypeDeclarer(ttd.pos(), newElems);
            }
            default -> td;
        };
    }

    // ---- Anon → Named conversion ----

    private TypeDeclarer makeNamed(AnonFuncTypeDeclarer a) {
        var pt = a.prototype();
        if (pt.hasTypeVar()) return a;  // unresolved generic, skip

        // Recursively normalize nested function types in the prototype
        var normalized = new Prototype(pt.pos(),
                pt.parameterSet(),
                pt.returnSet().has()
                        ? Optional.of(replaceAnon(pt.returnSet().get()))
                        : pt.returnSet());
        for (var p : normalized.parameterSet()) {
            if (p instanceof FixedParameter fp) {
                fp.type(replaceAnon(fp.type()));
            }
        }

        var key = protoKey(normalized);
        var pd = protoMap.get(key);
        if (pd == null) {
            pd = createProtoDef(key, normalized);
            protoMap.put(key, pd);
        }
        var dt = pd.link();
        var result = new NamedFuncTypeDeclarer(a.pos(),
                a.required(), dt, Lazy.of(pd));
        result.prototype(pd.prototype());
        return result;
    }

    private PrototypeDefinition createProtoDef(String key, Prototype pt) {
        var name = new Identifier(key);
        var sym = new Symbol(ZERO, name);
        return new PrototypeDefinition(ZERO, Modifier.empty(), sym,
                TypeParameters.empty(), pt);
    }

    // ---- stable keys for dedup ----

    private String protoKey(Prototype pt) {
        var sb = new StringBuilder("Proto");
        sb.append('_');
        if (pt.returnSet().has()) sb.append(typeKey(pt.returnSet().get()));
        else sb.append("Void");
        for (var t : pt.parameterSet().types()) {
            sb.append('_').append(typeKey(t));
        }
        return sb.toString();
    }

    private String typeKey(TypeDeclarer td) {
        if (td.hasTypeVar())
            return ErrorUtil.unreachable();
        return switch (td) {
            case PrimitiveTypeDeclarer ptd -> ptd.primitive().name();
            case VoidTypeDeclarer vtd -> "void";
            case DerivedTypeDeclarer dtd -> {
                var sb = new StringBuilder();
                sb.append(dtd.derivedType().symbol().name().value());
                if (!dtd.derivedType().generic().isEmpty()) {
                    for (var ga : dtd.derivedType().generic())
                        sb.append('_').append(typeKey(ga));
                }
                if (dtd.refer().has()) sb.append("Ptr");
                yield sb.toString();
            }
            case ArrayTypeDeclarer atd -> {
                var sb = new StringBuilder("Array");
                sb.append('_').append(typeKey(atd.element()));
                if (atd.len() != null) sb.append('_').append(atd.len());
                if (atd.refer().match(r -> true)) sb.append("Ref");
                yield sb.toString();
            }
            case TupleTypeDeclarer ttd -> {
                var sb = new StringBuilder("Tuple");
                for (var e : ttd.elements()) sb.append('_').append(typeKey(e));
                yield sb.toString();
            }
            case FuncTypeDeclarer ftd -> protoKey(ftd.prototype());
            default -> td.toString();
        };
    }
}
