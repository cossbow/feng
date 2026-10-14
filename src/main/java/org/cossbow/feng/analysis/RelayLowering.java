package org.cossbow.feng.analysis;

import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.IdentifierMap;
import org.cossbow.feng.ast.Symbol;
import org.cossbow.feng.ast.attr.Modifier;
import org.cossbow.feng.ast.dcl.*;
import org.cossbow.feng.ast.expr.*;
import org.cossbow.feng.ast.oop.ClassDefinition;
import org.cossbow.feng.ast.proc.FunctionDefinition;
import org.cossbow.feng.ast.proc.Procedure;
import org.cossbow.feng.ast.stmt.*;
import org.cossbow.feng.ast.var.*;
import org.cossbow.feng.util.Lazy;
import org.cossbow.feng.util.Optional;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * AST lowering pass that inserts temporary variables to "pin"
 * expressions whose result contains managed references,
 * preventing use-after-free (UAF) issues in backends.
 * <p>
 * This pass runs after semantic analysis and before code generation.
 * It rewrites the AST so that backends (C, IR, machine code) don't
 * need any special handling for lifetime management.
 * <p>
 * Two main scenarios (non-concurrent only):
 * <ol>
 *   <li>{@code a.b.run()} — the receiver {@code a.b} is a reference
 *       that {@code run()} might modify or nullify. The receiver is
 *       pinned to a temp variable before the call.</li>
 *   <li>{@code start(new(Task))} — {@code new(Task)} creates a
 *       temporary managed object. It is pinned to a temp variable
 *       before being passed as an argument.</li>
 * </ol>
 * <p>
 * General rule: a sub-expression that evaluates to a managed
 * reference is pinned when it appears as the receiver of a method
 * call or as a transient (unbound) argument to any call.
 */
public class RelayLowering {

    private final AnalyseSymbolTable ast;

    // 拆块后继语句：声明形态 `var a1 = { chain; a }` 拆为无初值声明 `var a1 T;`
    // + 后继块 `{ chain; a1 = a }`。该块必须扁平化为外层语句列表的兄弟语句
    // （不能嵌套 BlockStatement）——a1 被后续语句引用时嵌套会破坏作用域。
    // 由消费方 lowerStatementInto 在每条语句之后收集展平。
    private final ArrayList<Statement> splitSuffix = new ArrayList<>();

    // 是否允许在当前上下文拆块：仅块体语句列表允许（lowerStatementInto 置 true）；
    // for/if/switch init、upd、else、labeled target 等单语句位置置 false——链块
    // 保留 BlockExpression 形态（强绑定本就平衡无泄漏，phantom init 属超边缘）。
    private boolean allowChainSplit = true;

    public RelayLowering(AnalyseSymbolTable ast) {
        this.ast = ast;
    }

    // ---- public entry point ----

    public void lower() {
        for (var fd : ast.functionList) {
            lowerFunction(fd);
        }
        // 具体化函数（mono 产物，含丢弃强引用返回值的调用，须同样 lower）
        for (var fd : ast.monoFuncs) {
            lowerFunction(fd);
        }
        ast.main.use(this::lowerFunction);
        for (var cd : ast.dagClasses.all()) {
            lowerClass(cd);
        }
        // 具体化类（mono 产物，不在 dagClasses）：monoHead / monoAfter，
        // 其方法体已按 gm 具体化，但尚未 lower——丢弃强引用返回值会泄漏。
        for (var def : ast.monoHead) {
            if (def instanceof ClassDefinition cd) lowerClass(cd);
        }
        for (var list : ast.monoAfter.values()) {
            for (var def : list) {
                if (def instanceof ClassDefinition cd) lowerClass(cd);
            }
        }
        // 方法级泛型实例化（monoMethods）：单独挂到具体类，同样须 lower。
        for (var list : ast.monoMethods.values()) {
            for (var cm : list) {
                cm.procedure().use(this::lowerProcedure);
            }
        }
    }

    private void lowerClass(ClassDefinition cd) {
        for (var cm : cd.allMethods().values()) {
            cm.procedure().use(this::lowerProcedure);
        }
    }

    private void lowerFunction(FunctionDefinition fd) {
        fd.procedure().use(this::lowerProcedure);
    }

    // ---- procedure ----

    private void lowerProcedure(Procedure proc) {
        lowerBlockStatement(proc.body());
    }

    // ---- statements ----

    private BlockStatement lowerBlockStatement(BlockStatement bs) {
        var old = bs.list();
        var newList = new ArrayList<Statement>(old.size());
        for (var s : old) lowerStatementInto(newList, s);
        bs.list(newList);
        return bs;
    }

    /**
     * Lower a statement into {@code out}, flattening any chain-split suffix
     * produced by this statement ({@code var a1 = { chain; a }} → {@code
     * var a1 T;} followed by {@code { chain; a1 = a }}) as sibling statements.
     */
    private void lowerStatementInto(List<Statement> out, Statement s) {
        var start = splitSuffix.size();
        var prev = allowChainSplit;
        allowChainSplit = true;
        var lowered = lowerStatement(s);
        allowChainSplit = prev;
        out.add(lowered);
        if (splitSuffix.size() > start) {
            out.addAll(splitSuffix.subList(start, splitSuffix.size()));
            splitSuffix.subList(start, splitSuffix.size()).clear();
        }
    }

    /**
     * Lower a statement in a single-statement position (for/if/switch init,
     * updater, else branch, labeled target): chain splitting is disabled——
     * a split declaration needs sibling statements this position cannot hold,
     * and its variable must remain visible to the following control-flow scope.
     */
    private Statement lowerSingleStatement(Statement s) {
        var prev = allowChainSplit;
        allowChainSplit = false;
        var r = lowerStatement(s);
        allowChainSplit = prev;
        return r;
    }

    private Branch lowerBranch(Branch br) {
        var bs = lowerBlockStatement(br.body());
        return new Branch(br.pos(), bs);
    }

    private SwitchBranch lowerBranch(SwitchBranch br) {
        var bs = lowerBlockStatement(br.body());
        var cs = br.constants().stream().map(this::lowerExpression).toList();
        return new SwitchBranch(br.pos(), cs, bs);
    }

    private ForStatement lowerStatement(
            ConditionalForStatement cfs) {
        var init = cfs.initializer().map(this::lowerSingleStatement);
        var upd = cfs.updater().map(this::lowerSingleStatement);
        var cond = lowerExpression(cfs.condition());
        var body = lowerBlockStatement(cfs.body());
        return new ConditionalForStatement(cfs.pos(),
                body, init, cond, upd);
    }

    private Statement lowerStatement(Statement s) {
        return switch (s) {
            case CallStatement cs -> lowerCallStatement(cs);
            case BlockStatement bs -> lowerBlockStatement(bs);
            case IfStatement is -> {
                var init = is.init().map(this::lowerSingleStatement);
                var cond = (lowerExpression(is.condition()));
                var yes = lowerBlockStatement(is.yes());
                var not = is.not().map(this::lowerSingleStatement);
                yield new IfStatement(is.pos(), init, cond, yes, not);
            }
            case ForStatement fs -> {
                if (fs instanceof ConditionalForStatement cfs) {
                    yield lowerStatement(cfs);
                }
                var ifs = (IterableForStatement) fs;
                yield lowerStatement(ifs.replace.must());
            }
            case ReturnStatement rs -> {
                var v = rs.result().map(this::lowerExpression);
                // return { chain; a } → { chain; return a }：块尾强 lvalue 直写，
                // 消除生产者 inc；try-finally 时序不变（chain 先执行再走返回路径）。
                if (v.has() && v.get() instanceof BlockExpression be
                        && allowChainSplit && canSplitBlock(be)) {
                    var stmts = new ArrayList<Statement>(be.block());
                    var nrs = new ReturnStatement(rs.pos(), Optional.of(be.result()));
                    rs.procedure().use(p -> nrs.procedure().set(p));
                    nrs.local(rs.local());
                    stmts.add(nrs);
                    yield new BlockStatement(rs.pos(), stmts);
                }
                rs.result(v);
                yield rs;
            }
            case SwitchStatement ss -> {
                var init = ss.init().map(this::lowerSingleStatement);
                var cond = lowerExpression(ss.value());
                for (var br : ss.branches()) {
                    lowerBlockStatement(br.body());
                }
                var brs = ss.branches().stream().map(this::lowerBranch).toList();
                var dbr = ss.defaultBranch().map(this::lowerBranch);
                yield new SwitchStatement(ss.pos(), init, cond, brs, dbr);
            }
            case TryStatement ts -> {
                lowerBlockStatement(ts.body());
                for (var cc : ts.catchClauses()) {
                    lowerBlockStatement(cc.body());
                }
                ts.finallyClause().use(this::lowerBlockStatement);
                yield ts;
            }
            case AssignmentsStatement as -> lowerAssignment(as);
            case DeclarationStatement ds -> {
                for (var v : ds.variables()) {
                    v.value().update(this::lowerExpression);
                }
                // 拆块：var a1 = { chain; a } → var a1 T; + splitSuffix { chain; a1 = a }
                // （无初值声明发射 = NULL；初值统一移到 suffix 顺序执行，保证多变量
                //  声明左到右求值次序不变）。消除"块尾生产者 inc"——强绑定直写
                //  lvalue 由槽位 cleanup 平衡，phantom 直写无计数，语句无值。
                if (allowChainSplit && !ds.variables().isEmpty()) {
                    var splitAny = false;
                    for (var v : ds.variables()) {
                        if (v.value().has() && v.value().must() instanceof BlockExpression be
                                && canSplitBlock(be)) {
                            splitAny = true;
                            break;
                        }
                    }
                    if (splitAny) {
                        var suffix = new ArrayList<Statement>();
                        for (var v : ds.variables()) {
                            if (!v.value().has()) continue;
                            var val = v.value().must();
                            v.value().set(Lazy.nil());
                            if (val instanceof BlockExpression be && canSplitBlock(be)) {
                                suffix.add(new BlockStatement(ds.pos(),
                                        chainAssignStmts(be, v)));
                            } else {
                                suffix.add(new AssignmentsStatement(ds.pos(),
                                        List.of(new Assignment(ds.pos(),
                                                makeVarOperand(v), val))));
                            }
                        }
                        splitSuffix.addAll(suffix);
                    }
                }
                yield ds;
            }
            case AssertStatement as -> lowerAssert(as);
            case ThrowStatement ts -> {
                var e = lowerExpression(ts.exception());
                yield new ThrowStatement(ts.pos(), e);
            }
            case LabeledStatement ls -> {
                var tgt = lowerSingleStatement(ls.target());
                yield new LabeledStatement(ls.pos(), ls.label(), tgt);
            }
            default -> s;
        };
    }

    private Statement lowerAssert(AssertStatement as) {
        var ce = lowerExpression(as.condition());
        return new AssertStatement(as.pos(), ce);
    }

    // ---- assignment statement: operand pinning ----

    private Statement lowerAssignment(AssignmentsStatement as) {
        // lower right-hand values first
        for (var a : as.list()) {
            a.value(lowerExpression(a.value()));
        }

        // check each operand for transient subjects that need pinning
        var preStmts = new ArrayList<Statement>();
        var pins = new ArrayList<Variable>();
        for (var a : as.list()) {
            var operand = a.operand();
            var subject = operandSubject(operand);
            if (subject == null) continue;

            // Lower the subject first so a nested temporary (e.g. the array
            // in `alloc(4)[0].id = 1`) is pinned at its root.
            var lowered = lowerSubject(subject, preStmts);
            var finalSubject = lowered;

            if (needPinOperandSubject(lowered)) {
                var tmpVar = makePinVar(lowered);
                var tmpVarExpr = new VariableExpression(lowered.pos(), tmpVar);
                tmpVarExpr.resultType.set(lowered.resultType);
                pins.add(tmpVar);
                finalSubject = tmpVarExpr;
            }

            if (finalSubject != subject) {
                a.operand(makePinnedOperand(operand,
                        (PrimaryExpression) finalSubject));
            }
        }

        // 拆块：b.a = { chain; a } → { chain; b.a = a }（链按序插到其赋值之前，
        // 保持多赋值语句左到右求值次序；块尾强 lvalue 直写，无生产者 inc）。
        if (allowChainSplit && hasSplittableValue(as)) {
            var stmts = new ArrayList<>(preStmts);
            if (!pins.isEmpty()) stmts.add(new DeclarationStatement(as.pos(), pins));
            var pending = new ArrayList<Assignment>();
            for (var a : as.list()) {
                if (a.value() instanceof BlockExpression be && canSplitBlock(be)) {
                    if (!pending.isEmpty()) {
                        stmts.add(new AssignmentsStatement(as.pos(), pending));
                        pending = new ArrayList<>();
                    }
                    stmts.addAll(be.block());
                    a.value(be.result());
                    stmts.add(new AssignmentsStatement(as.pos(), List.of(a)));
                } else {
                    pending.add(a);
                }
            }
            if (!pending.isEmpty()) stmts.add(new AssignmentsStatement(as.pos(), pending));
            return new BlockStatement(as.pos(), stmts);
        }

        if (pins.isEmpty() && preStmts.isEmpty()) return as;

        var stmts = new ArrayList<>(preStmts);
        if (!pins.isEmpty()) stmts.add(new DeclarationStatement(as.pos(), pins));
        stmts.add(as);
        return new BlockStatement(as.pos(), stmts);
    }

    private boolean hasSplittableValue(AssignmentsStatement as) {
        for (var a : as.list()) {
            if (a.value() instanceof BlockExpression be && canSplitBlock(be)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get the subject expression from an operand, or null if
     * the operand doesn't have a subject (e.g., VariableOperand).
     */
    private PrimaryExpression operandSubject(Operand op) {
        return switch (op) {
            case FieldOperand fo -> fo.subject();
            case IndexOperand io -> io.subject();
            case DereferOperand dor -> dor.subject();
            case TupleOperand to -> to.subject();
            default -> null;
        };
    }

    /**
     * Create a new operand with the given pinned subject replacing
     * the original subject. Copies the type from the original operand.
     */
    private Operand makePinnedOperand(Operand op, PrimaryExpression pinnedSubject) {
        Operand result = switch (op) {
            case FieldOperand fo -> new FieldOperand(fo.pos(), pinnedSubject, fo.field());
            case IndexOperand io -> new IndexOperand(io.pos(), pinnedSubject, io.index());
            case DereferOperand dor -> new DereferOperand(dor.pos(), pinnedSubject);
            case TupleOperand to -> new TupleOperand(to.pos(), pinnedSubject, to.index());
            default -> op;
        };
        // Copy type from original operand to the new one
        op.type().use(result.type::set);
        return result;
    }

    /**
     * An operand's subject needs pinning if its type contains
     * managed references AND it is transient (unbound = temporary).
     */
    private boolean needPinOperandSubject(Expression subject) {
        // @Sync 字段读取（Feng$load_sl 原子 +1）即使 subject 是 bound 的，
        // 也必须 pin 出来在作用域结束释放；否则链式访问 a.b.c 中 a.b 的 +1 泄漏。
        if (subject.resultType.match(TypeDeclarer::markSync)) return true;
        if (!subject.unbound()) return false;
        return needPin(subject.resultType.must());
    }

    // ---- call statement ----

    private Statement lowerCallStatement(CallStatement cs) {
        if (cs.replace().has()) {
            // 前序 pass（SemanticAnalyzer 展开）已设置 replace()：lower 替换后的语句
            return lowerStatement(cs.replace().must());
        }

        var lowered = lowerCallExpression(cs.call());
        if (lowered instanceof BlockExpression be) {
            // 调用被临时变量包裹：转成 BlockStatement 并重加调用。
            var stmts = new ArrayList<Statement>(be.block());
            var re = be.result();
            if (re instanceof CallExpression rce) {
                addCallAsStatement(stmts, rce);
            }
            return new BlockStatement(cs.pos(), stmts);
        }
        if (lowered instanceof CallExpression lce) {
            if (needDiscardResult(lce)) {
                // 丢弃 STRONG 返回：物化为 pin 声明以释放引用。
                return new BlockStatement(cs.pos(),
                        new ArrayList<>(List.of(
                                new DeclarationStatement(lce.pos(),
                                        List.of(makePinVar(lce))))));
            }
            cs.call(lce);
        }
        return cs;
    }

    /**
     * Add a call as a statement, releasing a discarded strong-ref result.
     */
    private void addCallAsStatement(List<Statement> stmts, CallExpression ce) {
        if (needDiscardResult(ce)) {
            stmts.add(new DeclarationStatement(ce.pos(), List.of(makePinVar(ce))));
        } else {
            stmts.add(new CallStatement(ce.pos(), ce));
        }
    }

    private boolean needDiscardResult(Expression e) {
        return e.resultType.must().checkRefer(ReferKind.STRONG);
    }

    // ---- expression lowering ----

    private Expression lowerExpression(Expression e) {
        Expression r = switch (e) {
            case CallExpression ce -> lowerCallExpression(ce);
            case BinaryExpression be -> {
                var left = (pinOperand(lowerExpression(be.left())));
                var right = (pinOperand(lowerExpression(be.right())));
                yield new BinaryExpression(be.pos(), be.operator(),
                        left, right);
            }
            case UnaryExpression ue -> {
                var op = lowerExpression(ue.operand());
                yield new UnaryExpression(ue.pos(), ue.operator(), op);
            }
            case ConditionalExpression ce -> {
                var condition = lowerExpression(ce.condition());
                var pins = new ArrayList<Variable>();
                var yes = pinOperand(ce.yes(), pins);
                var not = pinOperand(ce.not(), pins);
                var n = new ConditionalExpression(ce.pos(), condition, yes, not);
                n.resultType.set(ce.resultType);
                if (pins.isEmpty()) yield n;
                var ds = new DeclarationStatement(ce.pos(), pins);
                var be = new BlockExpression(ce.pos(), List.of(ds), n);
                be.resultType.set(n.resultType);
                yield be;
            }
            case ConvertExpression ce -> {
                var op = lowerExpression(ce.operand());
                yield new ConvertExpression(ce.pos(), ce.primitive(), op);
            }
            case EnumIdExpression ee -> {
                var idx = lowerExpression(ee.index());
                yield new EnumIdExpression(ee.pos(), ee.def(), idx);
            }
            case IsExpression ie -> {
                var pins = new ArrayList<Variable>();
                var subject = pinOperand(ie.subject(), pins);
                if (pins.isEmpty()) yield ie;
                var n = new IsExpression(ie.pos(),
                        (PrimaryExpression) subject, ie.type());
                n.needCheck(ie.needCheck());
                n.resultType.set(ie.resultType);
                var ds = new DeclarationStatement(ie.pos(), pins);
                var be = new BlockExpression(ie.pos(), List.of(ds), n);
                be.resultType.set(n.resultType);
                yield be;
            }
            case MethodExpression me -> {
                var pins = new ArrayList<Variable>();
                var subject = pinOperand(me.subject(), pins);
                if (pins.isEmpty()) yield me;
                var n = new MethodExpression(me.pos(),
                        (PrimaryExpression) subject, me.method(), me.generic());
                n.resultType.set(me.resultType);
                var ds = new DeclarationStatement(me.pos(), pins);
                var be = new BlockExpression(me.pos(), List.of(ds), n);
                be.resultType.set(n.resultType);
                yield be;
            }
            case ReferEqualExpression re -> {
                var pins = new ArrayList<Variable>();
                var left = pinOperand(re.left(), pins);
                var right = pinOperand(re.right(), pins);
                if (pins.isEmpty()) {
                    yield re;
                }
                var n = new ReferEqualExpression(re.pos(),
                        (PrimaryExpression) left,
                        (PrimaryExpression) right, re.same());
                n.resultType.set(re.resultType);
                var ds = new DeclarationStatement(re.pos(), pins);
                var be = new BlockExpression(re.pos(), List.of(ds), n);
                be.resultType.set(n.resultType);
                yield be;
            }
            case CheckNilExpression ce -> {
                var pins = new ArrayList<Variable>();
                var subject = pinOperand(ce.subject(), pins);
                if (pins.isEmpty()) yield ce;
                var n = new CheckNilExpression(ce.pos(),
                        subject, ce.nil());
                n.resultType.set(ce.resultType);
                var ds = new DeclarationStatement(ce.pos(), pins);
                var be = new BlockExpression(ce.pos(), List.of(ds), n);
                be.resultType.set(n.resultType);
                yield be;
            }
            case MemberOfExpression me -> lowerMemberOf(me);
            case IndexOfExpression ie -> lowerIndexOf(ie);
            case SliceOfExpression se -> lowerSliceOf(se);
            case DereferExpression de -> lowerDerefer(de);
            case BlockExpression be -> {
                var newBlock = new ArrayList<Statement>(be.block().size());
                for (var s : be.block()) lowerStatementInto(newBlock, s);
                be.block(newBlock);
                var newResult = lowerExpression(be.result());
                if (newResult != be.result()) {
                    be.result(newResult);
                    newResult.resultType.use(be.resultType::set);
                }
                yield be;
            }
            case TupleExpression te -> {
                var li = te.elements().stream().map(this::lowerExpression).toList();
                yield new TupleExpression(te.pos(), li, te.types());
            }
            case TupleIndexExpression tie -> lowerTupleIndex(tie);
            case ArrayExpression ae -> {
                var li = ae.elements().stream().map(this::lowerExpression).toList();
                yield new ArrayExpression(ae.pos(), li, ae.type());
            }
            case ObjectExpression oe -> {
                var entries = new IdentifierMap<Expression>();
                for (var n : oe.entries().nodes()) {
                    var v = lowerExpression(n.value());
                    entries.set(n.key(), v);
                }
                yield new ObjectExpression(oe.pos(), entries, oe.type());
            }
            // nodes with final children: deep lowering skipped
            // (they don't typically contain UAF-vulnerable patterns)
            default -> e;
        };
        r.resultType.set(e.resultType);
        return r;
    }

    /**
     * Pin an unbound operand (temporary strong-ref result) so its lifetime
     * extends to the enclosing scope.
     */
    private Expression pinOperand(Expression e) {
        if (needPinOperandSubject(e)) {
            return makePin(e, e.resultType.must(), tmp -> tmp);
        }
        return e;
    }

    /**
     * Pin an operand that needs a stable lifetime, collecting the created
     * pin variable into {@code pins} so the caller can declare them all in
     * one enclosing block. The returned expression is the pin variable (a
     * borrow), not a block whose result owns the reference.
     */
    private Expression pinOperand(Expression e, List<Variable> pins) {
        var lowered = lowerExpression(e);
        if (!needPinOperandSubject(lowered)) return lowered;
        var pin = makePinVar(lowered);
        var pinExpr = new VariableExpression(lowered.pos(), pin);
        pinExpr.resultType.set(lowered.resultType);
        pins.add(pin);
        return pinExpr;
    }

    /**
     * Lower a subject expression, hoisting any pin block it produces into
     * {@code preStmts}. Returns the (possibly rebuilt) subject expression.
     */
    private Expression lowerSubject(Expression subject, List<Statement> preStmts) {
        var lowered = lowerExpression(subject);
        if (lowered instanceof BlockExpression be) {
            preStmts.addAll(be.block());
            var result = be.result();
            be.resultType.use(t -> result.resultType.set(t));
            return result;
        }
        return lowered;
    }

    private Expression lowerMemberOf(MemberOfExpression me) {
        var preStmts = new ArrayList<Statement>();
        var subject = lowerSubject(me.subject(), preStmts);
        var rt = me.resultType.must();

        var base = new MemberOfExpression(me.pos(), (PrimaryExpression) subject,
                me.member(), me.generic(), me.field());
        base.resultType.set(rt);

        Expression result;
        if (needPinOperandSubject(subject)) {
            result = makePin(subject, rt, pinned -> {
                var n = new MemberOfExpression(me.pos(), (PrimaryExpression) pinned,
                        me.member(), me.generic(), me.field());
                n.resultType.set(rt);
                return n;
            });
        } else {
            result = base;
        }

        if (preStmts.isEmpty()) return result;
        if (result instanceof BlockExpression be) {
            preStmts.addAll(be.block());
            var inner = be.result();
            be.resultType.use(inner.resultType::set);
            result = inner;
        }
        var be = new BlockExpression(me.pos(), preStmts, result);
        be.resultType.set(rt);
        return be;
    }

    private Expression lowerIndexOf(IndexOfExpression ie) {
        var preStmts = new ArrayList<Statement>();
        var subject = lowerSubject(ie.subject(), preStmts);
        var rt = ie.resultType.must();

        var base = new IndexOfExpression(ie.pos(), (PrimaryExpression) subject, ie.index());
        base.resultType.set(rt);

        Expression result;
        if (needPinOperandSubject(subject)) {
            result = makePin(subject, rt, pinned -> {
                var n = new IndexOfExpression(ie.pos(), (PrimaryExpression) pinned, ie.index());
                n.resultType.set(rt);
                return n;
            });
        } else {
            result = base;
        }

        if (preStmts.isEmpty()) return result;
        if (result instanceof BlockExpression be) {
            preStmts.addAll(be.block());
            var inner = be.result();
            be.resultType.use(inner.resultType::set);
            result = inner;
        }
        var be = new BlockExpression(ie.pos(), preStmts, result);
        be.resultType.set(rt);
        return be;
    }

    private Expression lowerTupleIndex(TupleIndexExpression tie) {
        var preStmts = new ArrayList<Statement>();
        var subject = lowerSubject(tie.subject(), preStmts);
        var rt = tie.resultType.must();

        var base = new TupleIndexExpression(tie.pos(),
                (PrimaryExpression) subject, tie.index());
        base.resultType.set(rt);

        Expression result;
        if (needPinOperandSubject(subject)) {
            result = makePin(subject, rt, pinned -> {
                var n = new TupleIndexExpression(tie.pos(),
                        (PrimaryExpression) pinned, tie.index());
                n.resultType.set(rt);
                return n;
            });
        } else {
            result = base;
        }

        if (preStmts.isEmpty()) return result;
        if (result instanceof BlockExpression be) {
            preStmts.addAll(be.block());
            var inner = be.result();
            be.resultType.use(inner.resultType::set);
            result = inner;
        }
        var be = new BlockExpression(tie.pos(), preStmts, result);
        be.resultType.set(rt);
        return be;
    }

    private Expression lowerSliceOf(SliceOfExpression se) {
        var preStmts = new ArrayList<Statement>();
        var subject = lowerSubject(se.subject(), preStmts);
        var rt = se.resultType.must();

        var base = new SliceOfExpression(se.pos(), (PrimaryExpression) subject,
                se.start(), se.end());
        base.resultType.set(rt);

        Expression result;
        if (needPinOperandSubject(subject)) {
            result = makePin(subject, rt, pinned -> {
                var n = new SliceOfExpression(se.pos(), (PrimaryExpression) pinned,
                        se.start(), se.end());
                n.resultType.set(rt);
                return n;
            });
        } else {
            result = base;
        }

        if (preStmts.isEmpty()) return result;
        if (result instanceof BlockExpression be) {
            preStmts.addAll(be.block());
            var inner = be.result();
            be.resultType.use(inner.resultType::set);
            result = inner;
        }
        var be = new BlockExpression(se.pos(), preStmts, result);
        be.resultType.set(rt);
        return be;
    }

    private Expression lowerDerefer(DereferExpression de) {
        var preStmts = new ArrayList<Statement>();
        var subject = lowerSubject(de.subject(), preStmts);
        var rt = de.resultType.must();

        var base = new DereferExpression(de.pos(), (PrimaryExpression) subject);
        base.resultType.set(rt);

        Expression result;
        if (needPinOperandSubject(subject)) {
            result = makePin(subject, rt, pinned -> {
                var n = new DereferExpression(de.pos(), (PrimaryExpression) pinned);
                n.resultType.set(rt);
                return n;
            });
        } else {
            result = base;
        }

        if (preStmts.isEmpty()) return result;
        if (result instanceof BlockExpression be) {
            preStmts.addAll(be.block());
            var inner = be.result();
            be.resultType.use(inner.resultType::set);
            result = inner;
        }
        var be = new BlockExpression(de.pos(), preStmts, result);
        be.resultType.set(rt);
        return be;
    }

    // ---- core: call expression pinning ----

    /**
     * Lower a CallExpression, pinning receiver or arguments as needed.
     * May return a BlockExpression wrapping the call with temp vars.
     */
    private Expression lowerCallExpression(CallExpression ce) {
        // 0: returnThis 调用（C 签名 void，返回值即接收者）→ 拆句为
        //    { self.m(args); self }，由接收者表达式承载结果值
        if (isReturnThisCall(ce)) {
            return splitReturnThisCall(ce);
        }
        // 1: lower arguments
        var newArgs = new ArrayList<Expression>(ce.arguments().size());
        for (var arg : ce.arguments()) newArgs.add(lowerExpression(arg));
        ce.arguments(newArgs);

        // 1.5: lower receiver recursively — a chained call's receiver is itself
        // a call whose result must be pinned, so hoist its nested pins here.
        var preStmts = new ArrayList<Statement>();
        var current = ce;
        if (current.callee() instanceof MethodExpression me) {
            var receiver = me.subject();
            var lowered = lowerExpression(receiver);
            if (lowered != receiver) {
                if (lowered instanceof BlockExpression be) {
                    preStmts.addAll(be.block());
                    var result = be.result();
                    // the rebuilt call expression's resultType may be unset;
                    // carry the block's type down so needPinReceiver can see it
                    be.resultType.use(t -> result.resultType.set(t));
                    lowered = result;
                }
                if (lowered instanceof PrimaryExpression pe) {
                    var newME = new MethodExpression(me.pos(), pe,
                            me.method(), me.generic());
                    newME.resultType.set(me.resultType.must());
                    current = new CallExpression(ce.pos(), newME,
                            ce.arguments(), ce.variadic(), ce.prototype().must());
                    current.resultType.set(ce.resultType);
                }
            }
        }

        // 1.6: lower a func-field callee (MemberOfExpression) to pin its subject
        // (e.g. new(A).cb(), make(n).cb())
        if (current.callee() instanceof MemberOfExpression me) {
            var lowered = lowerMemberOf(me);
            if (lowered instanceof BlockExpression be) {
                preStmts.addAll(be.block());
                lowered = be.result();
            }
            if (lowered != me && lowered instanceof PrimaryExpression pe) {
                current = new CallExpression(ce.pos(), pe, current.arguments(),
                        current.variadic(), current.prototype().must());
                current.resultType.set(ce.resultType);
            }
        }

        // 2: collect all sub-expressions that need pinning
        record Pin(Expression source, int argIndex) {
        }
        var pins = new ArrayList<Pin>();

        if (current.callee() instanceof MethodExpression me) {
            var receiver = me.subject();
            if (needPinReceiver(receiver)) {
                pins.add(new Pin(receiver, -1)); // -1 = receiver
            }
        }

        for (int i = 0; i < newArgs.size(); i++) {
            var arg = newArgs.get(i);
            if (arg.unbound() && needPin(arg.resultType.must())) {
                pins.add(new Pin(arg, i)); // i = argument index
            }
        }

        if (pins.isEmpty()) {
            if (preStmts.isEmpty()) return current;
            if (!ce.resultType.has()) return current;
            current.resultType.set(ce.resultType.must());
            var be = new BlockExpression(ce.pos(), preStmts, current);
            be.resultType.set(ce.resultType.must());
            return be;
        }

        // Guard: if CallExpression has no resultType, skip pinning (shouldn't happen after analysis)
        if (!ce.resultType.has()) return current;

        // 3: pin all collected expressions in one BlockExpression
        var tmpVars = new ArrayList<Variable>();
        for (var pin : pins) {
            var tmpVar = makePinVar(pin.source);
            var tmpVarExpr = new VariableExpression(
                    pin.source.pos(), tmpVar);
            tmpVarExpr.resultType.set(pin.source.resultType);
            tmpVars.add(tmpVar);

            current = (pin.argIndex < 0)
                    ? pinReceiver(current, tmpVarExpr)
                    : pinArgument(current, pin.argIndex, tmpVarExpr);
        }

        var stmts = new ArrayList<Statement>(preStmts);
        stmts.add(new DeclarationStatement(ce.pos(), tmpVars));
        current.resultType.set(ce.resultType.must());
        var be = new BlockExpression(ce.pos(), stmts, current);
        be.resultType.set(ce.resultType.must());
        return be;
    }

    // ---- returnThis split ----

    /**
     * A returnThis call has a void C signature (returnSet 空)——its value is
     * the receiver itself. Split it into {@code { self.m(args); self }}: the
     * inner call becomes a statement, the receiver becomes the block tail
     * that carries the result value. Nested chains flatten recursively
     * through {@link #lowerSubject} ({@code q.m1().m2()} → {@code
     * { q.m1(); q.m2(); q }}).
     * <p>
     * 计数约定（强结果 ⟺ 生产者 inc）：
     * <ul>
     *   <li>绑定 / 存字段 / return：块 unbound → 外层 inc 跳过，块尾 inc 传递；</li>
     *   <li>实参：块 unbound → 外层 pin 接住尾 inc；</li>
     *   <li>语句：lowerCallStatement 丢弃块尾，无 inc 无泄漏。</li>
     * </ul>
     */
    private Expression splitReturnThisCall(CallExpression ce) {
        var me = (MethodExpression) ce.callee();

        // 1: lower arguments (same rule as lowerCallExpression)
        var newArgs = new ArrayList<Expression>(ce.arguments().size());
        for (var arg : ce.arguments()) newArgs.add(lowerExpression(arg));

        // 2: lower subject, hoisting nested split blocks flat
        //    ({ q.m1(); q }.m2() → preStmts [q.m1()] + subject q)
        var preStmts = new ArrayList<Statement>();
        var subject = lowerSubject(me.subject(), preStmts);

        // 3: a non-trivial subject (field read / temporary) would otherwise be
        //    evaluated twice (inner call + block tail) — materialize it once
        //    so the call and the tail alias one stable lvalue
        if (needMaterializeSplitSubject(subject)) {
            var pin = makePinVar(subject);
            var pinExpr = new VariableExpression(subject.pos(), pin);
            pinExpr.resultType.set(subject.resultType);
            preStmts.add(new DeclarationStatement(subject.pos(), List.of(pin)));
            subject = pinExpr;
        }

        // 4: pin unbound arguments
        for (int i = 0; i < newArgs.size(); i++) {
            var arg = newArgs.get(i);
            if (arg.unbound() && needPin(arg.resultType.must())) {
                var pin = makePinVar(arg);
                var pinExpr = new VariableExpression(arg.pos(), pin);
                pinExpr.resultType.set(arg.resultType);
                newArgs.set(i, pinExpr);
                preStmts.add(new DeclarationStatement(arg.pos(), List.of(pin)));
            }
        }

        // 5: build { preStmts; self.m(args); self }
        var newME = new MethodExpression(me.pos(), (PrimaryExpression) subject,
                me.method(), me.generic());
        newME.resultType.set(me.resultType);
        var call = new CallExpression(ce.pos(), newME, newArgs,
                ce.variadic(), ce.prototype().must());
        call.resultType.set(ce.resultType);
        preStmts.add(new CallStatement(ce.pos(), call));

        var be = new BlockExpression(ce.pos(), preStmts, subject);
        be.resultType.set(ce.resultType.must());
        return be;
    }

    /**
     * A returnThis call is recognized by the resolved method's returnThis
     * flag——its C signature is void, so the call must be split.
     */
    private boolean isReturnThisCall(CallExpression ce) {
        return ce.callee() instanceof MethodExpression me
                && me.method().returnThis();
    }

    /**
     * Only a plain lvalue (variable / this) can serve directly as both the
     * inner call's receiver and the block tail; any other subject is
     * materialized once so it isn't evaluated twice.
     */
    private boolean needMaterializeSplitSubject(Expression subject) {
        return !(subject instanceof VariableExpression)
                && !(subject instanceof CurrentExpression);
    }

    private CallExpression pinReceiver(
            CallExpression ce, PrimaryExpression pinnedReceiver) {
        var me = (MethodExpression) ce.callee();
        var newME = new MethodExpression(me.pos(), pinnedReceiver,
                me.method(), me.generic());
        newME.resultType.set(me.resultType.must());
        return new CallExpression(ce.pos(), newME, ce.arguments(),
                ce.variadic(), ce.prototype().must());
    }

    private CallExpression pinArgument(
            CallExpression ce, int index,
            PrimaryExpression pinnedArg) {
        var newArgs = new ArrayList<>(ce.arguments());
        newArgs.set(index, pinnedArg);
        return new CallExpression(ce.pos(), ce.callee(), newArgs,
                ce.variadic(), ce.prototype().must());
    }

    // ---- chain-split helpers ----

    /**
     * A block expression can be split into plain statements when its result is a
     * <em>bound strong lvalue</em>——e.g. a returnThis chain {@code { a.m(); a }}
     * or a pinned member read {@code { var p = a.b; p.c }}. The block tail then
     * needs no "producer inc": the bound lvalue is written straight to the
     * consuming slot (strong slot inc is balanced by its own cleanup, phantom
     * slot borrows without counting, statement discards the value).
     */
    private boolean canSplitBlock(BlockExpression be) {
        var rt = be.resultType;
        if (!rt.has()) return false;
        if (!rt.must().checkRefer(ReferKind.STRONG)) return false;
        return !be.result().unbound();
    }

    /**
     * Build the suffix body {@code [chain stmts..., a1 = result]} for a
     * declaration split.
     */
    private List<Statement> chainAssignStmts(BlockExpression be, Variable v) {
        var stmts = new ArrayList<Statement>(be.block());
        stmts.add(new AssignmentsStatement(be.pos(),
                List.of(new Assignment(be.pos(), makeVarOperand(v), be.result()))));
        return stmts;
    }

    /**
     * A variable operand bound to the concrete {@link Variable}——synthetic
     * assignments (declaration split) bypass symbol resolution, so the
     * variable cache must be wired directly (StmtWriter reads it).
     */
    private VariableOperand makeVarOperand(Variable v) {
        var ov = new VariableOperand(v.pos(), new Symbol(v.name()));
        ov.variable().set(v);
        v.type().use(ov.type::set);
        return ov;
    }

    // ---- pinning helpers ----

    private Expression makePin(
            Expression source, TypeDeclarer resultType,
            Function<Expression, Expression> rebuild) {
        var tmpVar = makePinVar(source);
        var tmpVarExpr = new VariableExpression(source.pos(), tmpVar);
        tmpVarExpr.resultType.set(source.resultType);

        var newExpr = rebuild.apply(tmpVarExpr);

        var ds = new DeclarationStatement(source.pos(), List.of(tmpVar));
        var be = new BlockExpression(source.pos(), List.of(ds), newExpr);
        be.resultType.set(resultType);
        return be;
    }

    private Variable makePinVar(Expression s) {
        var name = new Identifier(s.pos(),
                "feng$pin", true);
        return new Variable(s.pos(), Modifier.empty(),
                Declare.CONST, name,
                s.resultType, Lazy.of(s));
    }

    // ---- predicates ----

    /**
     * A method-call receiver needs pinning if it is an unbound temporary whose
     * lifetime must extend across the call——receiver 是 phantom {@code self}
     * 参数，语义上（rule 6）允许引用临时实例，须物化为稳定 lvalue。
     * <p>
     * 值类型 receiver <b>仅在 unbound 时 pin</b>（如 {@code makeBox(3).area()}）：
     * 走与实参 pin 相同的 {@code const} 声明，由 ReleaserBuilder 正确处理内嵌
     * 强引用字段的引用计数（后端 {@code _self} 文本层物化不 inc，已废弃）。
     * Bound 值类型 lvalue（{@code obj.r.see()}）不 pin——拷贝会 double-free，
     * 且它已是稳定 lvalue、可直接 {@code &}。
     */
    private boolean needPinReceiver(Expression receiver) {
        if (receiver.resultType.must().maybeRefer().none()) {
            return receiver.unbound();
        }
        // safe in non-concurrent code: simple var or 'this'
        return !(receiver instanceof VariableExpression)
                && !(receiver instanceof CurrentExpression);
    }

    /**
     * Check if a type contains managed references.
     */
    private boolean needPin(TypeDeclarer t) {
        if (t.maybeRefer().has()) return true;

        if (t instanceof DerivedTypeDeclarer dtd) {
            if (dtd.def() instanceof ClassDefinition cd) {
                for (var f : cd.allFields()) {
                    // 单态化后 dtd.def() 仍是模板类（字段声明类型为类型变量），
                    // 需经 gm.mapIf 解析实参后才能判定强引用（同 TypeDeclarer.requiredInit）。
                    if (needPin(dtd.gm().mapIf(f.type()))) return true;
                }
            }
            return false;
        }

        if (t instanceof ArrayTypeDeclarer atd) {
            if (atd.len() == 0) return false;
            return needPin(atd.element());
        }

        if (t instanceof TupleTypeDeclarer ttd) {
            for (var e : ttd.elements()) {
                if (needPin(e)) return true;
            }
            return false;
        }

        return false;
    }

}
