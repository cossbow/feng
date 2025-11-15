package org.cossbow.feng.parser;

import org.cossbow.feng.ast.BinaryOperator;
import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.dcl.NewDefinedType;
import org.cossbow.feng.ast.dcl.Variable;
import org.cossbow.feng.ast.expr.BinaryExpression;
import org.cossbow.feng.ast.expr.CallExpression;
import org.cossbow.feng.ast.expr.NewExpression;
import org.cossbow.feng.ast.proc.FunctionDefinition;
import org.cossbow.feng.ast.stmt.*;
import org.cossbow.feng.ast.var.VariableAssignableOperand;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public class StatementParseTest extends BaseParseTest {

    static void appendCalls(StringBuilder sb, List<Identifier> li) {
        sb.append("{");
        appendList(sb, li, "", "();");
        sb.append("}");
    }

    static void checkCalls(List<Identifier> names, BlockStatement block) {
        checkIds(names, block.list(), BaseParseTest::calleeName);
    }

    Statement parseStmt(String code) {
        var f = (FunctionDefinition) doParseDefinition("func main() {" + code + " }");
        return f.procedure().body().list().getFirst();
    }

    @Test
    public void testBlock() {
        for (int i = 0; i < 10; i++) {
            var list = new ArrayList<String>(i);
            for (int j = 0; j < i; j++) {
                list.add("var %s = %d;".formatted(randVarFuncName(6), i + j));
            }
            var code = list.stream().collect(Collectors.joining("", "{", "}"));
            var block = (BlockStatement) parseStmt(code);
            Assertions.assertEquals(i, block.list().size());
        }
    }

    @Test
    public void testAssignmentOperation() {
        for (var op : AssignmentParseTest.assignableOperators) {
            var a = randVarFuncName(8);
            var b = randVarFuncName(8);
            var code = a + symbol(op) + "=" + b + ";";
            var stmt = (AssignmentOperateStatement) parseStmt(code);
            var lhs = (VariableAssignableOperand) stmt.operand();
            Assertions.assertEquals(a, lhs.name());
            Assertions.assertEquals(b, varName(stmt.value()));
            Assertions.assertSame(op, stmt.operator());
        }
    }

    @Test
    public void testDeclaration() {
        for (int an = 1; an <= 5; an++) {
            for (int vn = 1; vn <= 10; vn++) {
                var type = randTypeName(16);
                var code = new StringBuilder();
                var attrs = anyNames(RandTypeName, 16, an);
                appendList(code, attrs, "@", "");
                var names = anyNames(RandVarFuncName, 8, vn);
                code.append(" var ").append(idList(names)).append(" ").append(type).append(";");
                var stmt = (DeclarationStatement) parseStmt(code.toString());

                for (var v : stmt.variables()) {
                    checkIds(attrs, v.modifier().attributes());
                    Assertions.assertEquals(type, typeName(v.type().get()));
                }
                checkIds(names, stmt.variables(), Variable::name);
            }
        }
    }

    @Test
    public void testCall() {
        var fn = randVarFuncName(16);
        var stmt = (CallStatement) parseStmt(fn + "();");
        var call = stmt.call();
        Assertions.assertEquals(fn, varName(call.callee()));
        Assertions.assertTrue(call.arguments().isEmpty());
    }

    @Test
    public void testIf() {
        var a = randVarFuncName(8);
        var b = randVarFuncName(8);
        var code = "if(%s) %s();".formatted(a, b);
        var stmt = (IfStatement) parseStmt(code);
        Assertions.assertTrue(stmt.init().none());
        Assertions.assertEquals(a, varName(stmt.condition()));
        var call = (CallStatement) stmt.yes();
        Assertions.assertEquals(b, varName(call.call().callee()));
        Assertions.assertTrue(stmt.not().none());
    }

    @Test
    public void testIfElse() {
        var a = randVarFuncName(8);
        var b = randVarFuncName(8);
        var c = randVarFuncName(8);
        var code = "if(%s) %s(); else %s();".formatted(a, b, c);
        var stmt = (IfStatement) parseStmt(code);
        Assertions.assertTrue(stmt.init().none());
        Assertions.assertEquals(a, varName(stmt.condition()));
        var yes = (CallStatement) stmt.yes();
        Assertions.assertEquals(b, varName(yes.call().callee()));
        var not = (CallStatement) stmt.not().must();
        Assertions.assertEquals(c, varName(not.call().callee()));
    }

    @Test
    public void testIfElseIf() {
        var a = randVarFuncName(8);
        var b = randVarFuncName(8);
        var c = randVarFuncName(8);
        var d = randVarFuncName(8);
        var code = "if(%s) %s(); else if(%s) %s(); ".formatted(a, b, c, d);
        var stmt = (IfStatement) parseStmt(code);
        Assertions.assertTrue(stmt.init().none());
        Assertions.assertEquals(a, varName(stmt.condition()));
        var yes = (CallStatement) stmt.yes();
        Assertions.assertEquals(b, varName(yes.call().callee()));
        var stmt2 = (IfStatement) stmt.not().must();
        Assertions.assertEquals(c, varName(stmt2.condition()));
        var yes2 = (CallStatement) stmt2.yes();
        Assertions.assertEquals(d, varName(yes2.call().callee()));
    }

    @Test
    public void testSwitchBranchEmpty() {
        var x = randVarFuncName(12);
        var stmt = (SwitchStatement) parseStmt("switch(%s) {}".formatted(x));
        Assertions.assertEquals(x, varName(stmt.value()));
    }

    @Test
    public void testSwitchBranchFull() {
        var a = randVarFuncName(6);
        var code = """
                switch(var %s = goods(); %s) {
                case "0":
                case "1":
                    call1();
                case "2":
                    call2();
                    fallthrough;
                case "3","4":
                    call3();
                default:
                    error();
                }
                """.formatted(a, a);
        var stmt = (SwitchStatement) parseStmt(code);

        var dcl = (DeclarationStatement) stmt.init().must();
        Assertions.assertEquals(a, dcl.variables().getFirst().name());
        var init = (CallExpression) first(dcl.init().must());
        Assertions.assertEquals(identifier("goods"), varName(init.callee()));
        Assertions.assertTrue(init.arguments().isEmpty());

        Assertions.assertEquals(a, varName(stmt.value()));
        {
            var br = stmt.branches().getFirst();
            Assertions.assertEquals("0", string(br.constants().getFirst()));
            Assertions.assertTrue(br.statements().isEmpty());
            Assertions.assertFalse(br.fallthrough());
        }
        {
            var br = stmt.branches().get(1);
            Assertions.assertEquals("1", string(br.constants().getFirst()));
            Assertions.assertEquals(1, br.statements().size());
            var c1 = (CallStatement) br.statements().getFirst();
            Assertions.assertEquals(identifier("call1"), varName(c1.call().callee()));
            Assertions.assertFalse(br.fallthrough());
        }
        {
            var br = stmt.branches().get(2);
            Assertions.assertEquals("2", string(br.constants().getFirst()));
            Assertions.assertEquals(1, br.statements().size());
            var c1 = (CallStatement) br.statements().getFirst();
            Assertions.assertEquals(identifier("call2"), varName(c1.call().callee()));
            Assertions.assertTrue(br.fallthrough());
        }
        {
            var br = stmt.branches().get(3);
            Assertions.assertEquals("3", string(br.constants().get(0)));
            Assertions.assertEquals("4", string(br.constants().get(1)));
            Assertions.assertEquals(1, br.statements().size());
            var c1 = (CallStatement) br.statements().getFirst();
            Assertions.assertEquals(identifier("call3"), varName(c1.call().callee()));
            Assertions.assertFalse(br.fallthrough());
        }
    }

    @Test
    public void testUnaryFor() {
        var i = randVarFuncName(8);
        var n = randVarFuncName(16);
        var code = "for(%s < %s) %s+=1;".formatted(i, n, i);
        var stmt = (ConditionalForStatement) parseStmt(code);
        Assertions.assertTrue(stmt.initializer().none());
        Assertions.assertTrue(stmt.updater().none());
        var cond = (BinaryExpression) stmt.condition();
        Assertions.assertSame(BinaryOperator.LT, cond.operator());
        Assertions.assertEquals(i, varName(cond.left()));
        Assertions.assertEquals(n, varName(cond.right()));
        var assign = (AssignmentOperateStatement) stmt.body();
        var lhs = (VariableAssignableOperand) assign.operand();
        Assertions.assertEquals(i, lhs.name());
    }

    @Test
    public void testTernaryFor() {
        var i = randVarFuncName(8);
        var n = randVarFuncName(16);
        var c = randVarFuncName(32);
        var code = "for(var %s=0; %s<%s; %s+=1) %s(%s);".formatted(i, i, n, i, c, i);
        var stmt = (ConditionalForStatement) parseStmt(code);

        var dcl = (DeclarationStatement) stmt.initializer().must();
        Assertions.assertEquals(i, dcl.variables().getFirst().name());

        var cond = (BinaryExpression) stmt.condition();
        Assertions.assertSame(BinaryOperator.LT, cond.operator());
        Assertions.assertEquals(i, varName(cond.left()));
        Assertions.assertEquals(n, varName(cond.right()));
        var call = ((CallStatement) stmt.body()).call();
        Assertions.assertEquals(c, varName(call.callee()));
        Assertions.assertEquals(i, varName(call.arguments().getFirst()));

        var aop = ((AssignmentOperateStatement) stmt.updater().must());
        Assertions.assertEquals(i, ((VariableAssignableOperand) aop.operand()).name());
    }

    @Test
    public void testIterableFor() {
        var size = ThreadLocalRandom.current().nextInt(1, 10);
        var names = anyNames(RandVarFuncName, 8, size);
        var src = randVarFuncName(16);
        var body = randVarFuncName(32);
        var code = "for(%s : %s) %s();".formatted(idList(names), src, body);
        var forStmt = (IterableForStatement) parseStmt(code);
        Assertions.assertEquals(names, forStmt.arguments());
        Assertions.assertEquals(src, varName(forStmt.iterable()));
        var call = (CallStatement) forStmt.body();
        Assertions.assertEquals(body, varName(call.call().callee()));
    }

    @Test
    public void testThrow() {
        var type = randTypeName(32);
        var code = "throw new(%s);".formatted(type);
        var stmt = (ThrowStatement) parseStmt(code);
        var newExpr = (NewExpression) stmt.exception();
        Assertions.assertTrue(newExpr.init().none());
        Assertions.assertEquals(type, ((NewDefinedType) newExpr.type()).type().name());
    }

    @Test
    public void testTry() {
        var rand = ThreadLocalRandom.current();
        for (int n = 1; n <= 10; n++) {
            for (int f = 0; f < 2; f++) {
                var hasFinal = f > 0;

                var code = new StringBuilder("try{");
                var body = anyNames(RandVarFuncName, 8, rand.nextInt(1, 6));
                for (var c : body) code.append(c).append("();");
                code.append("}");

                var excepts = new ArrayList<Identifier>();
                var typeSets = new ArrayList<List<Identifier>>();
                var catchLists = new ArrayList<List<Identifier>>();
                for (int i = 0; i < n; i++) {
                    code.append("catch(");
                    var except = randVarFuncName(4);
                    code.append(except).append(" ");
                    excepts.add(except);
                    var typeSet = anyNames(RandTypeName, 12, rand.nextInt(1, 4));
                    appendList(code, typeSet, "", "|");
                    code.setCharAt(code.length() - 1, ')');
                    typeSets.add(typeSet);

                    var catchList = anyNames(RandVarFuncName, 12, rand.nextInt(1, 10));
                    appendCalls(code, catchList);
                    catchLists.add(catchList);
                }
                var finalBlk = anyNames(RandVarFuncName, 12, rand.nextInt(1, 10));
                if (hasFinal) {
                    code.append("finally");
                    appendCalls(code, finalBlk);
                }

                var tryStmt = (TryStatement) parseStmt(code.toString());

                checkCalls(body, tryStmt.body());

                Assertions.assertEquals(n, tryStmt.catchClauses().size());
                for (int i = 0; i < n; i++) {
                    var cc = tryStmt.catchClauses().get(i);
                    Assertions.assertEquals(excepts.get(i), cc.argument().name());
                    checkIds(typeSets.get(i), cc.typeSet(), BaseParseTest::typeName);
                    checkCalls(catchLists.get(i), cc.body());
                }

                Assertions.assertEquals(hasFinal, tryStmt.finallyClause().has());
                if (hasFinal) {
                    checkCalls(finalBlk, tryStmt.finallyClause().get());
                }
            }
        }
    }

    @Test
    public void testReturn() {
        for (int i = 0; i < 10; i++) {
            var names = anyNames(RandVarFuncName, 8, i);
            var code = "return " + idList(names) + ";";
            var stmt = (ReturnStatement) parseStmt(code);
            if (i == 0) {
                Assertions.assertTrue(stmt.result().none());
                continue;
            }
            checkIds(names, ((ArrayTuple) stmt.result().get()).values(), BaseParseTest::varName);
        }
    }

    @Test
    public void testLabelBlock() {
        var name = randVarFuncName(32);
        var stmt = (LabeledStatement) parseStmt(name + ":{}");
        Assertions.assertEquals(name, stmt.label());
        Assertions.assertInstanceOf(BlockStatement.class, stmt.statement());
    }

    @Test
    public void testLabelDeclaration() {
        var name = randVarFuncName(32);
        var stmt = (LabeledStatement) parseStmt(name + ":var a,b,c int;");
        Assertions.assertEquals(name, stmt.label());
        Assertions.assertInstanceOf(DeclarationStatement.class, stmt.statement());
    }

    @Test
    public void testLabelAssignment() {
        var name = randVarFuncName(32);
        var stmt = (LabeledStatement) parseStmt(name + ":a,b,c=1,2,3;");
        Assertions.assertEquals(name, stmt.label());
        Assertions.assertInstanceOf(AssignmentsStatement.class, stmt.statement());
    }

    @Test
    public void testLabelAssignmentOperation() {
        var name = randVarFuncName(32);
        var stmt = (LabeledStatement) parseStmt(name + ":a+=3;");
        Assertions.assertEquals(name, stmt.label());
        Assertions.assertInstanceOf(AssignmentOperateStatement.class, stmt.statement());
    }

    @Test
    public void testLabelCall() {
        var name = randVarFuncName(32);
        var stmt = (LabeledStatement) parseStmt(name + ":a();");
        Assertions.assertEquals(name, stmt.label());
        Assertions.assertInstanceOf(CallStatement.class, stmt.statement());
    }

    @Test
    public void testLabelIf() {
        var name = randVarFuncName(32);
        var stmt = (LabeledStatement) parseStmt(name + ":if(a>0)acc();");
        Assertions.assertEquals(name, stmt.label());
        Assertions.assertInstanceOf(IfStatement.class, stmt.statement());
    }

    @Test
    public void testLabelSwitch() {
        var name = randVarFuncName(32);
        var stmt = (LabeledStatement) parseStmt(name + ":switch(a){}");
        Assertions.assertEquals(name, stmt.label());
        Assertions.assertInstanceOf(SwitchStatement.class, stmt.statement());
    }

    @Test
    public void testLabelFor() {
        var name = randVarFuncName(32);
        var stmt = (LabeledStatement) parseStmt(name + ":for(a>0)a-=1;");
        Assertions.assertEquals(name, stmt.label());
        Assertions.assertInstanceOf(ConditionalForStatement.class, stmt.statement());
    }

    @Test
    public void testLabelThrow() {
        var name = randVarFuncName(32);
        var stmt = (LabeledStatement) parseStmt(name + ":throw e;");
        Assertions.assertEquals(name, stmt.label());
        Assertions.assertInstanceOf(ThrowStatement.class, stmt.statement());
    }

    @Test
    public void testLabelTry() {
        var name = randVarFuncName(32);
        var stmt = (LabeledStatement) parseStmt(name + ":try{e();}finally{}");
        Assertions.assertEquals(name, stmt.label());
        Assertions.assertInstanceOf(TryStatement.class, stmt.statement());
    }

    @Test
    public void testLabelDefineFunc() {
        var name = randVarFuncName(32);
        var stmt = (LabeledStatement) parseStmt(name + ":func done() {}");
        Assertions.assertEquals(name, stmt.label());
        Assertions.assertInstanceOf(LocalDefineStatement.class, stmt.statement());
    }

    @Test
    public void testLabelDefineStruct() {
        var name = randVarFuncName(32);
        var stmt = (LabeledStatement) parseStmt(name + ":struct G{}");
        Assertions.assertEquals(name, stmt.label());
        Assertions.assertInstanceOf(LocalDefineStatement.class, stmt.statement());
    }

    @Test
    public void testLabelDefineClass() {
        var name = randVarFuncName(32);
        var stmt = (LabeledStatement) parseStmt(name + ":class G{}");
        Assertions.assertEquals(name, stmt.label());
        Assertions.assertInstanceOf(LocalDefineStatement.class, stmt.statement());
    }

    @Test
    public void testLabelDefineInterface() {
        var name = randVarFuncName(32);
        var stmt = (LabeledStatement) parseStmt(name + ":interface G{}");
        Assertions.assertEquals(name, stmt.label());
        Assertions.assertInstanceOf(LocalDefineStatement.class, stmt.statement());
    }

    @Test
    public void testLabelDefinePrototype() {
        var name = randVarFuncName(32);
        var stmt = (LabeledStatement) parseStmt(name + ":func g();");
        Assertions.assertEquals(name, stmt.label());
        Assertions.assertInstanceOf(LocalDefineStatement.class, stmt.statement());
    }


    // tuple


    private Tuple parseTuple(String tuple) {
        var stmt = (AssignmentsStatement) parseStmt("a=" + tuple + ";");
        return stmt.tuple();
    }

    @Test
    public void testIfTuple() {
        var size = ThreadLocalRandom.current().nextInt(1, 10);
        var cond = randVarFuncName(8);
        var yes = anyNames(RandVarFuncName, 8, size);
        var not = anyNames(RandVarFuncName, 4, size);
        var code = "if(%s) %s else %s".formatted(cond, idList(yes), idList(not));
        var tp = (IfTuple) parseTuple(code);
        Assertions.assertEquals(cond, varName(tp.condition()));

        var tpYes = (ArrayTuple) tp.yes();
        var tpNot = (ArrayTuple) tp.not();
        Assertions.assertEquals(yes.size(), tpYes.values().size(), code);
        Assertions.assertEquals(yes.size(), tpNot.values().size(), code);
        for (int i = 0; i < size; i++) {
            Assertions.assertEquals(yes.get(i), varName(tpYes.values().get(i)), code);
            Assertions.assertEquals(not.get(i), varName(tpNot.values().get(i)), code);
        }
    }

    @Test
    public void testSwitchTuple() {
        var check = randVarFuncName(8);
        var code = new StringBuilder("switch(" + check + "){\n");
        var size = ThreadLocalRandom.current().nextInt(1, 10);
        var num = ThreadLocalRandom.current().nextInt(1, 10);
        var constants = new ArrayList<List<Identifier>>();
        var values = new ArrayList<List<Identifier>>();
        for (int i = 0; i < num; i++) {
            var cs = anyNames(RandVarFuncName, 6, i + 1);
            constants.add(cs);
            var vs = anyNames(RandVarFuncName, 6, size);
            values.add(vs);
            code.append("case ").append(idList(cs)).append(":")
                    .append(idList(vs)).append(";\n");
        }
        var defVals = anyNames(RandVarFuncName, 6, size);
        code.append("default: ").append(idList(defVals)).append(";\n}");
        var tp = (SwitchTuple) parseTuple(code.toString());

        Assertions.assertEquals(check, varName(tp.value()));
        Assertions.assertEquals(num, tp.rules().size());
        for (int i = 0; i < num; i++) {
            var cs = constants.get(i);
            var vs = values.get(i);
            var r = tp.rules().get(i);
            Assertions.assertEquals(cs.size(), r.constants().size());
            for (int j = 0; j < r.constants().size(); j++)
                Assertions.assertEquals(cs.get(j), varName(r.constants().get(j)));
            var vtp = (ArrayTuple) r.tuple();
            Assertions.assertEquals(size, vtp.values().size());
            for (int j = 0; j < vtp.values().size(); j++)
                Assertions.assertEquals(vs.get(j), varName(vtp.values().get(j)));
        }
        for (int i = 0; i < size; i++) {
            var dtp = (ArrayTuple) tp.defaultRule();
            Assertions.assertEquals(defVals.get(i), varName(dtp.values().get(i)));
        }
    }

}
