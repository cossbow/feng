package org.cossbow.feng.ast.stmt;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.Scope;
import org.cossbow.feng.ast.gen.GenericMap;
import org.cossbow.feng.ast.dcl.TypeDeclarer;
import org.cossbow.feng.ast.dcl.Variable;

import java.util.ArrayList;
import java.util.List;

public class CatchClause extends Statement implements Scope {
    private Variable argument;
    private List<TypeDeclarer> typeSet;
    private BlockStatement body;

    public CatchClause(Position pos,
                       Variable argument,
                       List<TypeDeclarer> typeSet,
                       BlockStatement body) {
        super(pos);
        this.argument = argument;
        this.typeSet = typeSet;
        this.body = body;
    }

    public Variable argument() {
        return argument;
    }

    public List<TypeDeclarer> typeSet() {
        return typeSet;
    }

    public BlockStatement body() {
        return body;
    }

    //

    private volatile List<Variable> stack = List.of();

    public List<Variable> stack() {
        return stack;
    }

    public void stack(List<Variable> variables) {
        stack = variables;
    }

    @Override
    public CatchClause mirror() {
        return new CatchClause(pos(), argument.mirror(), typeSet, body.mirror());
    }

    @Override
    public CatchClause mono(GenericMap gm) {
        var types = new ArrayList<TypeDeclarer>(typeSet.size());
        for (var t : typeSet) types.add(gm.mapIf(t));
        // clone 保留原 variable 的 id（与 DeclarationStatement.mono 约定一致）：
        // 引用处 VariableExpression.mono 保留原对象，声明/引用编号须一致，
        // 否则清理栈 mirroredVars 按 id 判定 throw catch 变量时失效。
        var arg = argument.clone();
        if (argument.type().has())
            arg.type().set(gm.mapIf(argument.type().must()));
        return new CatchClause(pos(), arg, types, body.mono(gm));
    }
}
