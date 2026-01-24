package org.cossbow.feng.parser;

import org.cossbow.feng.ast.BinaryOperator;
import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.Symbol;
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

    static <I> void appendCalls(StringBuilder sb, List<I> li) {
        sb.append("{");
        appendList(sb, li, "", "();");
        sb.append("}");
    }

    static void checkCalls(List<Symbol> names, BlockStatement block) {
        checkIds(names, block.list(), BaseParseTest::calleeName);
    }

    Statement parseStmt(String code) {
        var f = (FunctionDefinition) doParseFirstDef("func main() {" + code + " }");
        return f.procedure().body().list().getFirst();
    }

    @Test
    public void testBlock() {
        for (int i = 0; i < 10; i++) {
            var list = new ArrayList<String>(i);
            for (int j = 0; j < i; j++) {
                list.add("var %s = %d;".formatted(randVarName(6), i + j));
            }
            var code = list.stream().collect(Collectors.joining("", "{", "}"));
            var block = (BlockStatement) parseStmt(code);
            Assertions.assertEquals(i, block.list().size());
        }
    }

    @Test
    public void testAssignmentOperation() {
        for (var op : AssignmentParseTest.assignableOperators) {
            var a = symbol(randVarName(8));
            var b = symbol(randVarName(8));
            var code = a + operator(op) + "=" + b + ";";
            var stmt = (AssignmentsStatement) parseStmt(code);
            var operand = (VariableAssignableOperand) stmt.operands().getFirst();
            Assertions.assertEquals(a, operand.symbol());
            var value = (BinaryExpression) ((ArrayTuple) stmt.tuple())
                    .values().getFirst();
            Assertions.assertEquals(b, varName(value.right()));
            Assertions.assertSame(op, value.operator());
        }
    }

    @Test
    public void testDeclaration() {
        for (int an = 1; an <= 5; an++) {
            for (int vn = 1; vn <= 10; vn++) {
                var type = symbol(randTypeName(16));
                var code = new StringBuilder();
                var attrs = anyNames(RandTypeName, 16, an);
                appendList(code, attrs, "@", "");
                var names = anyNames(RandVarFuncName, 8, vn);
                code.append(" var ").append(idList(names)).append(" ").append(type).append(";");
                var stmt = (DeclarationStatement) parseStmt(code.toString());

                for (var v : stmt.variables()) {
                    checkIds(attrs, v.modifier().attributes());
                    Assertions.assertEquals(type, typeName(v.type().must()));
                }
                checkIds(names, stmt.variables(), Variable::name);
            }
        }
    }

    @Test
    public void testCall() {
        var fn = symbol(randVarName(16));
        var stmt = (CallStatement) parseStmt(fn + "();");
        var call = stmt.call();
        Assertions.assertEquals(fn, varName(call.callee()));
        Assertions.assertTrue(call.arguments().isEmpty());
    }

    @Test
    public void testIf() {
        var a = symbol(randVarName(8));
        var b = symbol(randVarName(8));
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
        var a = symbol(randVarName(8));
        var b = symbol(randVarName(8));
        var c = symbol(randVarName(8));
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
        var a = symbol(randVarName(8));
        var b = symbol(randVarName(8));
        var c = symbol(randVarName(8));
        var d = symbol(randVarName(8));
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
        var x = symbol(randVarName(12));
        var stmt = (SwitchStatement) parseStmt("switch(%s) {}".formatted(x));
        Assertions.assertEquals(x, varName(stmt.value()));
    }

    @Test
    public void testSwitchBranchFull() {
        var a = randVarName(6);
        var code = """
                switch(var %s = goods(); %s) {
                case 0 {}
                case 1 {call1();}
                case 2 {call2();}
                case 3,4 {call3();}
                default {error();}
                }
                """.formatted(a, a);
        var stmt = (SwitchStatement) parseStmt(code);

        var dcl = (DeclarationStatement) stmt.init().must();
        Assertions.assertEquals(a, dcl.variables().getFirst().name());
        var init = (CallExpression) first(dcl.init().must());
        Assertions.assertEquals(symbol("goods"), varName(init.callee()));
        Assertions.assertTrue(init.arguments().isEmpty());

        Assertions.assertEquals(symbol(a), varName(stmt.value()));
        {
            var br = stmt.branches().getFirst();
            Assertions.assertEquals(0,
                    integer(br.constants().getFirst()).value().intValue());
            Assertions.assertTrue(br.body().isEmpty());
        }
        {
            var br = stmt.branches().get(1);
            Assertions.assertEquals(1,
                    integer(br.constants().getFirst()).value().intValue());
            Assertions.assertEquals(1, br.body().size());
            var c1 = (CallStatement) br.body().list().getFirst();
            Assertions.assertEquals(symbol("call1"), varName(c1.call().callee()));
        }
        {
            var br = stmt.branches().get(2);
            Assertions.assertEquals(2,
                    integer(br.constants().getFirst()).value().intValue());
            Assertions.assertEquals(1, br.body().size());
            var c1 = (CallStatement) br.body().list().getFirst();
            Assertions.assertEquals(symbol("call2"), varName(c1.call().callee()));
        }
        {
            var br = stmt.branches().get(3);
            Assertions.assertEquals(3,
                    integer(br.constants().get(0)).value().intValue());
            Assertions.assertEquals(4,
                    integer(br.constants().get(1)).value().intValue());
            Assertions.assertEquals(1, br.body().size());
            var c1 = (CallStatement) br.body().list().getFirst();
            Assertions.assertEquals(symbol("call3"), varName(c1.call().callee()));
        }
    }

    @Test
    public void testUnaryFor() {
        var i = symbol(randVarName(8));
        var n = symbol(randVarName(16));
        var code = "for(%s < %s) %s+=1;".formatted(i, n, i);
        var stmt = (ConditionalForStatement) parseStmt(code);
        Assertions.assertTrue(stmt.initializer().none());
        Assertions.assertTrue(stmt.updater().none());
        var cond = (BinaryExpression) stmt.condition();
        Assertions.assertSame(BinaryOperator.LT, cond.operator());
        Assertions.assertEquals(i, varName(cond.left()));
        Assertions.assertEquals(n, varName(cond.right()));
        var assign = (AssignmentsStatement) stmt.body();
        var lhs = (VariableAssignableOperand) assign.operands().getFirst();
        Assertions.assertEquals(i, lhs.symbol());
    }

    @Test
    public void testTernaryFor() {
        var i = randVarName(10);
        var n = symbol(randVarName(16));
        var c = symbol(randVarName(32));
        var code = "for(var %s=0; %s<%s; %s+=1) %s(%s);".formatted(i, i, n, i, c, i);
        var stmt = (ConditionalForStatement) parseStmt(code);

        var dcl = (DeclarationStatement) stmt.initializer().must();
        Assertions.assertEquals(i, dcl.variables().getFirst().name());

        var cond = (BinaryExpression) stmt.condition();
        Assertions.assertSame(BinaryOperator.LT, cond.operator());
        Assertions.assertEquals(symbol(i), varName(cond.left()));
        Assertions.assertEquals(n, varName(cond.right()));
        var call = ((CallStatement) stmt.body()).call();
        Assertions.assertEquals(c, varName(call.callee()));
        Assertions.assertEquals(symbol(i), varName(call.arguments().getFirst()));

        var aop = ((AssignmentsStatement) stmt.updater().must());
        var left = ((VariableAssignableOperand) aop.operands().getFirst());
        Assertions.assertEquals(symbol(i), left.symbol());
    }

    @Test
    public void testIterableFor() {
        var size = ThreadLocalRandom.current().nextInt(1, 10);
        var names = anyNames(RandVarFuncName, 8, size);
        var src = symbol(randVarName(16));
        var body = symbol(randVarName(32));
        var code = "for(%s : %s) %s();".formatted(idList(names), src, body);
        var forStmt = (IterableForStatement) parseStmt(code);
        Assertions.assertEquals(names, forStmt.arguments());
        Assertions.assertEquals(src, varName(forStmt.iterable()));
        var call = (CallStatement) forStmt.body();
        Assertions.assertEquals(body, varName(call.call().callee()));
    }

    @Test
    public void testThrow() {
        var type = symbol(randTypeName(32));
        var code = "throw new(%s);".formatted(type);
        var stmt = (ThrowStatement) parseStmt(code);
        var newExpr = (NewExpression) stmt.exception();
        Assertions.assertTrue(newExpr.arg().none());
        Assertions.assertEquals(type, ((NewDefinedType) newExpr.type()).type().symbol());
    }

    @Test
    public void testTry() {
        var rand = ThreadLocalRandom.current();
        for (int n = 1; n <= 10; n++) {
            for (int f = 0; f < 2; f++) {
                var hasFinal = f > 0;

                var code = new StringBuilder("try{");
                var body = anyNames(RandVarSymbol, 8, rand.nextInt(1, 6));
                for (var c : body) code.append(c).append("();");
                code.append("}");

                var excepts = new ArrayList<Identifier>();
                var typeSets = new ArrayList<List<Symbol>>();
                var catchLists = new ArrayList<List<Symbol>>();
                for (int i = 0; i < n; i++) {
                    code.append("catch(");
                    var except = randVarName(4);
                    code.append(except).append(" ");
                    excepts.add(except);
                    var typeSet = anyNames(RandTypeSymbol, 12, rand.nextInt(1, 4));
                    appendList(code, typeSet, "", "|");
                    code.setCharAt(code.length() - 1, ')');
                    typeSets.add(typeSet);

                    var catchList = anyNames(RandVarSymbol, 12, rand.nextInt(1, 10));
                    appendCalls(code, catchList);
                    catchLists.add(catchList);
                }
                var finalBlk = anyNames(RandVarSymbol, 12, rand.nextInt(1, 10));
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
            var symbols = anyNames(RandVarSymbol, 8, i);
            var code = "return " + idList(symbols) + ";";
            var stmt = (ReturnStatement) parseStmt(code);
            if (i == 0) {
                Assertions.assertTrue(stmt.result().none());
                continue;
            }
            checkIds(symbols, ((ArrayTuple) stmt.result().get()).values(), BaseParseTest::varName);
        }
    }

    @Test
    public void testLabelBlock() {
        var name = randVarName(32);
        var stmt = (LabeledStatement) parseStmt(name + ":{}");
        Assertions.assertEquals(name, stmt.label());
        Assertions.assertInstanceOf(BlockStatement.class, stmt.statement());
    }

    @Test
    public void testLabelDeclaration() {
        var name = randVarName(32);
        var stmt = (LabeledStatement) parseStmt(name + ":var a,b,c int;");
        Assertions.assertEquals(name, stmt.label());
        Assertions.assertInstanceOf(DeclarationStatement.class, stmt.statement());
    }

    @Test
    public void testLabelAssignment() {
        var name = randVarName(32);
        var stmt = (LabeledStatement) parseStmt(name + ":a,b,c=1,2,3;");
        Assertions.assertEquals(name, stmt.label());
        Assertions.assertInstanceOf(AssignmentsStatement.class, stmt.statement());
    }

    @Test
    public void testLabelAssignmentOperation() {
        var name = randVarName(32);
        var stmt = (LabeledStatement) parseStmt(name + ":a+=3;");
        Assertions.assertEquals(name, stmt.label());
        Assertions.assertInstanceOf(AssignmentsStatement.class, stmt.statement());
    }

    @Test
    public void testLabelCall() {
        var name = randVarName(32);
        var stmt = (LabeledStatement) parseStmt(name + ":a();");
        Assertions.assertEquals(name, stmt.label());
        Assertions.assertInstanceOf(CallStatement.class, stmt.statement());
    }

    @Test
    public void testLabelIf() {
        var name = randVarName(32);
        var stmt = (LabeledStatement) parseStmt(name + ":if(a>0)acc();");
        Assertions.assertEquals(name, stmt.label());
        Assertions.assertInstanceOf(IfStatement.class, stmt.statement());
    }

    @Test
    public void testLabelSwitch() {
        var name = randVarName(32);
        var stmt = (LabeledStatement) parseStmt(name + ":switch(a){}");
        Assertions.assertEquals(name, stmt.label());
        Assertions.assertInstanceOf(SwitchStatement.class, stmt.statement());
    }

    @Test
    public void testLabelFor() {
        var name = randVarName(32);
        var stmt = (LabeledStatement) parseStmt(name + ":for(a>0)a-=1;");
        Assertions.assertEquals(name, stmt.label());
        Assertions.assertInstanceOf(ConditionalForStatement.class, stmt.statement());
    }

    @Test
    public void testLabelThrow() {
        var name = randVarName(32);
        var stmt = (LabeledStatement) parseStmt(name + ":throw e;");
        Assertions.assertEquals(name, stmt.label());
        Assertions.assertInstanceOf(ThrowStatement.class, stmt.statement());
    }

    @Test
    public void testLabelTry() {
        var name = randVarName(32);
        var stmt = (LabeledStatement) parseStmt(name + ":try{e();}finally{}");
        Assertions.assertEquals(name, stmt.label());
        Assertions.assertInstanceOf(TryStatement.class, stmt.statement());
    }


}
