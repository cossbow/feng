package org.cossbow.feng.parser;

import org.cossbow.feng.ast.BinaryOperator;
import org.cossbow.feng.ast.expr.*;
import org.cossbow.feng.ast.stmt.AssignmentsStatement;
import org.cossbow.feng.ast.var.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Consumer;

public class AssignmentParseTest extends BaseParseTest {

    public static final List<BinaryOperator> assignableOperators = List.of(
            BinaryOperator.AND,
            BinaryOperator.OR,
            BinaryOperator.ADD,
            BinaryOperator.SUB,
            BinaryOperator.MUL,
            BinaryOperator.DIV,
            BinaryOperator.MOD,
            BinaryOperator.BITAND,
            BinaryOperator.BITOR,
            BinaryOperator.BITXOR,
            BinaryOperator.LSHIFT,
            BinaryOperator.RSHIFT
    );

    @SuppressWarnings("unchecked")
    static <H extends Operand>
    void assignmentOperationTester(String lhs, Consumer<H> lhsTest) {
        for (var op : assignableOperators) {
            var b = randVarSymbol(8);
            var code = "%s%s=%s;".formatted(lhs, operator(op), b);
            var stmt = (AssignmentsStatement) doParseLocal(code);
            var a=stmt.list().getFirst();
            lhsTest.accept((H) a.operand());
            var v = (BinaryExpression) a.value();
            Assertions.assertEquals(b, varName(v.right()));
            Assertions.assertSame(op, v.operator());
        }
    }

    @Test
    public void testAssignmentLeft() {
        var a = randVarSymbol(8);
        var b = randVarSymbol(8);
        var i = randVarSymbol(6);
        var c = randVarSymbol(8);
        var m = randVarName(12);
        var code = "%s, %s[%s], %s.%s= 1,2,3;".formatted(a, b, i, c, m);
        var stmt = (AssignmentsStatement) doParseLocal(code);
        var list = stmt.list();

        Assertions.assertEquals(3, list.size());

        var vhls = (VariableOperand) list.get(0).operand();
        Assertions.assertEquals(a, vhls.symbol());

        var ihls = (IndexOperand) list.get(1).operand();
        Assertions.assertEquals(b, varName(ihls.subject()));
        Assertions.assertEquals(i, varName(ihls.index()));

        var mhls = (FieldOperand) list.get(2).operand();
        Assertions.assertEquals(c, varName(mhls.subject()));
        Assertions.assertEquals(m, mhls.field());
    }

    @Test
    public void testAssignmentRight() {
        var code = "a,b,c,d,e,f,g,h,i,j = 1,2+1,-2,PI,rate(3),foo.boo,arr[11],(2),[5],{id=1};";
        var stmt = (AssignmentsStatement) doParseLocal(code);
        var lhs = stmt.list();
        var rhs = stmt.list().stream().map(Assignment::value).toList();

        Assertions.assertEquals(10, lhs.size());

        checkInstances((rhs), List.of(
                LiteralExpression.class,
                BinaryExpression.class,
                UnaryExpression.class,
                SymbolExpression.class,
                CallExpression.class,
                MemberOfExpression.class,
                IndexOfExpression.class,
                ParenExpression.class,
                ArrayExpression.class,
                ObjectExpression.class
        ));

    }

    @Test
    public void testAssignmentOperation() {
        var v = randVarSymbol(8);
        assignmentOperationTester("" + v, lhs -> {
            var refLeft = (VariableOperand) lhs;
            Assertions.assertEquals(v, refLeft.symbol());
        });

        var f = randVarName(5);
        assignmentOperationTester(v + "." + f, lhs -> {
            var fieldLeft = (FieldOperand) lhs;
            Assertions.assertEquals(v, varName(fieldLeft.subject()));
            Assertions.assertEquals(f, fieldLeft.field());
        });

        var i = randVarSymbol(4);
        assignmentOperationTester(v + "[" + i + "]", lhs -> {
            var indexLeft = (IndexOperand) lhs;
            Assertions.assertEquals(v, varName(indexLeft.subject()));
            Assertions.assertEquals(i, varName(indexLeft.index()));
        });
    }

}
