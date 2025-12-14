package org.cossbow.feng.parser;

import org.cossbow.feng.ast.MultiTable;
import org.cossbow.feng.ast.expr.BinaryExpression;
import org.cossbow.feng.ast.expr.MemberOfExpression;
import org.cossbow.feng.ast.micro.*;
import org.cossbow.feng.ast.oop.ClassDefinition;
import org.cossbow.feng.ast.oop.InterfaceDefinition;
import org.cossbow.feng.ast.stmt.AssignmentsStatement;
import org.cossbow.feng.ast.var.MemberAssignableOperand;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Function;

public class MacroParseTest extends BaseParseTest {

    private final List<Function<String, MultiTable<Macro>>> macroMap = List.of(
            this::interfaceMacro,
            this::classMacro
    );

    private MultiTable<Macro> interfaceMacro(String c) {
        var code = "interface A{macro %s}".formatted(c);
        var def = (InterfaceDefinition) doParseDefinition(code);
        return (def.macros());
    }

    private MultiTable<Macro> classMacro(String c) {
        var code = "class A{macro %s}".formatted(c);
        var def = (ClassDefinition) doParseDefinition(code);
        return (def.macros());
    }

    @Test
    public void testFunc() {
        for (var f : macroMap) {
            var type = randVarName(8);
            var name = randVarName(12);
            var left = randVarName(8);
            var right = randVarName(8);
            var result = randVarName(8);
            var code = "%s %s(%s,%s,%s){%s.x=%s.x+%s.x;}"
                    .formatted(type, name, left, right, result, result, left, right);
            var macros = f.apply(code);
            var mf = (MacroFunc) macros.get(type, name);
            var proc = mf.procedure();
            Assertions.assertEquals(name, proc.name());
            checkIds(List.of(left, right, result), proc.params());
            var stmt = (AssignmentsStatement) proc.body().getFirst();
            var operand = (MemberAssignableOperand) stmt.operands().getFirst();
            Assertions.assertEquals(symbol(result), varName(operand.subject()));
            var bin = (BinaryExpression) first(stmt.tuple());
            Assertions.assertEquals(symbol(left),
                    varName(((MemberOfExpression) bin.left()).subject()));
            Assertions.assertEquals(symbol(right),
                    varName(((MemberOfExpression) bin.right()).subject()));
        }
    }

    @Test
    public void testClass() {
        for (var f : macroMap) {
            var type = randTypeName(8);
            var name = randTypeName(12);
            var idx = randVarName(8);
            var m1 = randVarName(8);
            var m2 = randVarName(8);
            var m3 = randVarName(8);
            var code = "%s %s{%s int;%s(){%s=0;}%s(){%s<size}%s(){%s+=1;}}"
                    .formatted(type, name, idx, m1, idx, m2, idx, m3, idx);
            var mc = (MacroClass) f.apply(code).get(type, name);
            Assertions.assertEquals(name, mc.name());
            checkIds(List.of(idx), mc.fields());
            checkIds(List.of(m1, m2, m3), mc.methods());
        }

    }


}
