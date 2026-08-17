package org.cossbow.feng.ast.stmt;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.dcl.TypeDeclarer;
import org.cossbow.feng.ast.dcl.Variable;
import org.cossbow.feng.ast.expr.Expression;
import org.cossbow.feng.ast.gen.GenericMap;
import org.cossbow.feng.util.Lazy;

import java.util.ArrayList;
import java.util.List;

public class DeclarationStatement extends Statement {
    private List<Variable> variables;

    public DeclarationStatement(Position pos,
                                List<Variable> variables) {
        super(pos);
        this.variables = variables;
    }

    public List<Variable> variables() {
        return variables;
    }

    public int size() {
        return variables.size();
    }

    @Override
    public DeclarationStatement mirror() {
        var vars = new ArrayList<Variable>(variables.size());
        for (var v : variables) vars.add(v.mirror());
        return new DeclarationStatement(pos(), vars);
    }

    @Override
    public DeclarationStatement mono(GenericMap gm) {
        var vars = new ArrayList<Variable>(variables.size());
        for (var v : variables) {
            // clone 保留原 variable 的 id——VariableExpression.mono 引用的是原对象，
            // 若这里 new Variable 会重新分配全局自增 id，声明/引用编号不一致
            // （生成 C 出现 $t_11 声明 vs $t_16 引用）。
            var nv = v.clone();
            if (v.type().has()) nv.type().set(gm.mapIf(v.type().must()));
            if (v.value().has()) nv.value().set(v.value().must().mono(gm));
            vars.add(nv);
        }
        return new DeclarationStatement(pos(), vars);
    }
}
