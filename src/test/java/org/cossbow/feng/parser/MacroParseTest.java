package org.cossbow.feng.parser;

import org.cossbow.feng.Utils;
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

    private final List<Function<String, List<Macro>>> macroMap = List.of(
            this::interfaceMacro,
            this::classMacro
    );

    private List<Macro> interfaceMacro(String c) {
        var code = "interface A{# %s}".formatted(c);
        var def = (InterfaceDefinition) doParseDefinition(code);
        return (def.macros());
    }

    private List<Macro> classMacro(String c) {
        var code = "class A{# %s}".formatted(c);
        var def = (ClassDefinition) doParseDefinition(code);
        return (def.macros());
    }

    @Test
    public void testFunc() {
        for (var f : macroMap) {
            var name = randVarFuncName(12);
            var left = randVarFuncName(8);
            var right = randVarFuncName(8);
            var result = randVarFuncName(8);
            var code = "operator %s(%s,%s,%s){%s.x=%s.x+%s.x;}"
                    .formatted(name, left, right, result, result, left, right);
            var mp = Utils.<Macro, MacroFunc>convert(f.apply(code)).getFirst();
            var proc = mp.procedure();
            Assertions.assertEquals(name, proc.name());
            checkIds(List.of(left, right, result), proc.params(), MacroVariable::name);
            var stmt = (AssignmentsStatement) proc.body().getFirst();
            var operand = (MemberAssignableOperand) stmt.operands().getFirst();
            Assertions.assertEquals(result, varName(operand.subject()));
            var bin = (BinaryExpression) first(stmt.tuple());
            Assertions.assertEquals(left, varName(((MemberOfExpression) bin.left()).subject()));
            Assertions.assertEquals(right, varName(((MemberOfExpression) bin.right()).subject()));
        }
    }

    @Test
    public void testClass() {
        for (var f : macroMap) {
            var name = randTypeName(12);
            var idx = randVarFuncName(8);
            var m1 = randVarFuncName(8);
            var m2 = randVarFuncName(8);
            var m3 = randVarFuncName(8);
            var code = "helper %s{%s int;%s(){%s=0;}%s(){%s<size}%s(){%s+=1;}}"
                    .formatted(name, idx, m1, idx, m2, idx, m3, idx);
            var mc = Utils.<Macro, MacroClass>convert(f.apply(code)).getFirst();
            Assertions.assertEquals(name, mc.name());
            checkIds(List.of(idx), mc.fields(), MacroVariable::name);
            checkIds(List.of(m1, m2, m3), mc.methods(), MacroProcedure::name);
        }

    }


}
