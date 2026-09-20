package org.cossbow.feng.ast.expr;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.Symbol;
import org.cossbow.feng.ast.gen.GenericMap;
import org.cossbow.feng.ast.Mangle;
import org.cossbow.feng.ast.gen.TypeArguments;
import org.cossbow.feng.ast.proc.FunctionDefinition;

public class FunctionExpression extends PrimaryExpression {
    private final FunctionDefinition func;
    private TypeArguments generic;

    public FunctionExpression(Position pos,
                              FunctionDefinition func,
                              TypeArguments generic) {
        super(pos);
        this.func = func;
        this.generic = generic;
    }

    public FunctionDefinition func() {
        return func;
    }

    public TypeArguments generic() {
        return generic;
    }

    public void generic(TypeArguments generic) {
        this.generic = generic;
    }

    public Symbol symbol() {
        return func.symbol();
    }

    @Override
    public PrimaryExpression mirror() {
        return this;
    }

    @Override
    public PrimaryExpression mono(GenericMap gm) {
        var mapped = gm.mapAll(generic);
        // EMPTY 恒等映射（非泛型函数体裁决 static if 时）：泛型调用保持
        // FunctionExpression，mono 后的 discover 仍能按其 generic 注册具体化；
        // 真实 gm（泛型实例化）才转换成 mangle 符号（ExprWriter 输出一致）。
        if (gm != GenericMap.EMPTY
                && !mapped.isEmpty() && !mapped.hasTypeVar()) {
            return monoCopy(new SymbolExpression(pos(),
                    Mangle.symbol(func.symbol(), mapped), TypeArguments.EMPTY), gm);
        }
        return monoCopy(new FunctionExpression(pos(), func, mapped), gm);
    }

    //
    @Override
    public String toString() {
        if (generic.isEmpty())
            return symbol().toString();
        return symbol().toString() + generic;
    }
}
