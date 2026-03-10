package org.cossbow.feng.ast.expr;

import org.cossbow.feng.ast.Entity;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.dcl.TypeDeclarer;
import org.cossbow.feng.util.Lazy;

abstract
public class Expression extends Entity {

    public Expression(Position pos) {
        super(pos);
    }

    // 游离的，未被变量、字段或数组元素绑定的
    public boolean unbound() {
        return false;
    }

    // 表达式推导出来的类型缓存在这里
    public final Lazy<TypeDeclarer> resultType = Lazy.nil();


    //

    // 用于标记需要引用的是可调用的过程：函数或方法
    private volatile boolean expectCallable;

    public boolean expectCallable() {
        return expectCallable;
    }

    public void expectCallable(boolean expectCallable) {
        this.expectCallable = expectCallable;
    }

}
