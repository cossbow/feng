package org.cossbow.feng.analysis;

import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.attr.Modifier;
import org.cossbow.feng.ast.dcl.*;
import org.cossbow.feng.ast.expr.*;
import org.cossbow.feng.ast.oop.ClassDefinition;
import org.cossbow.feng.ast.proc.Procedure;
import org.cossbow.feng.ast.stmt.*;
import org.cossbow.feng.ast.var.*;
import org.cossbow.feng.util.Lazy;

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

    public RelayLowering(AnalyseSymbolTable ast) {
        this.ast = ast;
    }

    // ---- public entry point ----

    public void lower() {
        for (var fd : ast.functionList) {
            fd.procedure().use(this::lowerProcedure);
        }
        for (var cd : ast.dagClasses.all()) {
            for (var cm : cd.allMethods().values()) {
                cm.procedure().use(this::lowerProcedure);
            }
        }
    }

    // ---- procedure ----

    private void lowerProcedure(Procedure proc) {
        lowerBlockStatement(proc.body());
    }

    // ---- statements ----

    private void lowerBlockStatement(BlockStatement bs) {
        var old = bs.list();
        var newList = new ArrayList<Statement>(old.size());
        for (var s : old) newList.add(lowerStatement(s));
        bs.list(newList);
    }

    private Statement lowerStatement(Statement s) {
        return switch (s) {
            case CallStatement cs -> lowerCallStatement(cs);
            case BlockStatement bs -> {
                lowerBlockStatement(bs);
                yield bs;
            }
            case IfStatement is -> {
                is.init().use(this::lowerStatement);
                is.condition(lowerExpression(is.condition()));
                lowerBlockStatement(is.yes());
                is.not().use(this::lowerBodied);
                yield is;
            }
            case ForStatement fs -> {
                if (fs instanceof ConditionalForStatement cfs) {
                    cfs.initializer().use(this::lowerStatement);
                    lowerBlockStatement(cfs.body());
                } else if (fs instanceof IterableForStatement ifs) {
                    lowerBlockStatement(ifs.body());
                }
                yield fs;
            }
            case ReturnStatement rs -> {
                rs.result().use(this::lowerExpression);
                yield rs;
            }
            case SwitchStatement ss -> {
                ss.init().use(this::lowerStatement);
                for (var br : ss.branches()) {
                    lowerBlockStatement(br.body());
                }
                ss.defaultBranch().use(br -> lowerBlockStatement(br.body()));
                yield ss;
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
                yield ds;
            }
            default -> s;
        };
    }

    private void lowerBodied(Statement body) {
        if (body instanceof BlockStatement bs) {
            lowerBlockStatement(bs);
        } else {
            // single-statement else branch: lower directly
            // (the result stays in the Optional<Statement> holder)
        }
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

        if (pins.isEmpty() && preStmts.isEmpty()) return as;

        var stmts = new ArrayList<Statement>(preStmts);
        if (!pins.isEmpty()) stmts.add(new DeclarationStatement(as.pos(), pins));
        stmts.add(as);
        return new BlockStatement(as.pos(), stmts);
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
            case FieldOperand fo ->
                new FieldOperand(fo.pos(), pinnedSubject, fo.field());
            case IndexOperand io ->
                new IndexOperand(io.pos(), pinnedSubject, io.index());
            case DereferOperand dor ->
                new DereferOperand(dor.pos(), pinnedSubject);
            case TupleOperand to ->
                new TupleOperand(to.pos(), pinnedSubject, to.index());
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
        if (!subject.unbound()) return false;
        return needPin(subject.resultType.must());
    }

    // ---- call statement ----

    private Statement lowerCallStatement(CallStatement cs) {
        var lowered = lowerCallExpression(cs.call());
        if (lowered instanceof BlockExpression be) {
            // the call was wrapped with temp vars; convert the block's
            // statements to a BlockStatement and re-add the call.
            var stmts = new ArrayList<Statement>(be.block());
            var re = be.result();
            if (re instanceof CallExpression rce) {
                addCallAsStatement(stmts, rce);
            }
            cs.replace().set(new BlockStatement(cs.pos(), stmts));
            return cs;
        }
        if (lowered instanceof CallExpression lce) {
            if (needDiscardResult(lce)) {
                cs.replace().set(new BlockStatement(cs.pos(),
                        new ArrayList<>(List.of(
                                new DeclarationStatement(lce.pos(),
                                        List.of(makePinVar(lce)))))));
            } else {
                cs.call(lce);
            }
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
        return e.resultType.match(t -> t.checkRefer(ReferKind.STRONG));
    }

    // ---- expression lowering ----

    private Expression lowerExpression(Expression e) {
        return switch (e) {
            case CallExpression ce -> lowerCallExpression(ce);
            case BinaryExpression be -> {
                be.left(pinOperand(lowerExpression(be.left())));
                be.right(pinOperand(lowerExpression(be.right())));
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
                re.resultType.use(n.resultType::set);
                var ds = new DeclarationStatement(re.pos(), pins);
                var be = new BlockExpression(re.pos(), List.of(ds), n);
                be.resultType.set(n.resultType.must());
                yield be;
            }
            case MemberOfExpression me -> lowerMemberOf(me);
            case IndexOfExpression ie -> lowerIndexOf(ie);
            case DereferExpression de -> lowerDerefer(de);
            case BlockExpression be -> {
                var newBlock = new ArrayList<Statement>(be.block().size());
                for (var s : be.block()) newBlock.add(lowerStatement(s));
                be.block(newBlock);
                var newResult = lowerExpression(be.result());
                if (newResult != be.result()) {
                    be.result(newResult);
                    newResult.resultType.use(be.resultType::set);
                }
                yield be;
            }
            case TupleExpression te -> {
                var newElems = new ArrayList<Expression>(te.elements().size());
                for (var el : te.elements()) newElems.add(lowerExpression(el));
                te.elements(newElems);
                yield te;
            }
            case ArrayExpression ae -> {
                var newElems = new ArrayList<Expression>(ae.elements().size());
                for (var el : ae.elements()) newElems.add(lowerExpression(el));
                ae.elements(newElems);
                yield ae;
            }
            case ObjectExpression oe -> {
                for (var n : oe.entries().nodes()) {
                    lowerExpression(n.value());
                }
                yield oe;
            }
            // nodes with final children: deep lowering skipped
            // (they don't typically contain UAF-vulnerable patterns)
            default -> e;
        };
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
            }
        }

        // 2: collect all sub-expressions that need pinning
        record Pin(Expression source, int argIndex) {}
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
     * A method-call receiver needs pinning if it is a direct reference
     * (strong/phantom) and not a simple variable/this reference.
     * <p>
     * Value-type receivers (e.g. {@code obj.r.see()} where {@code r} is an
     * embedded final class) must NOT be pinned: pinning would shallow-copy the
     * value without inc'ing its embedded strong-ref fields, then the copy and
     * the original would both release those refs (double-free). Their subject
     * is already pinned by {@link #lowerSubject}, so they are stable lvalues.
     */
    private boolean needPinReceiver(Expression receiver) {
        if (receiver.resultType.must().maybeRefer().none()) return false;
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
                    if (needPin(f.type())) return true;
                }
            }
            return false;
        }

        if (t instanceof ArrayTypeDeclarer atd) {
            if (atd.len() == 0) return false;
            return needPin(atd.element());
        }

        return false;
    }

}
