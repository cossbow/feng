package org.cossbow.feng.analysis.hir;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.expr.PrimaryExpression;
import org.cossbow.feng.ast.gen.GenericMap;
import org.cossbow.feng.util.ErrorUtil;

/**
 * 目前没有IR设计，就在此处添加中间AST节点：
 * 这是用于生成取地址的表达式
 */
public class AddressOfException extends PrimaryExpression {
    private final PrimaryExpression subject;

    public AddressOfException(PrimaryExpression subject) {
        super(subject.pos());
        this.subject = subject;
    }

    public PrimaryExpression subject() {
        return subject;
    }

    public PrimaryExpression mirror() {
        return ErrorUtil.unreachable();
    }

    public PrimaryExpression mono(GenericMap gm) {
        return ErrorUtil.unreachable();
    }

}
