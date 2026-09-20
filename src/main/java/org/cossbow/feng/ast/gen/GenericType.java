package org.cossbow.feng.ast.gen;

import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.dcl.GenericTypeDeclarer;
import org.cossbow.feng.ast.dcl.Refer;
import org.cossbow.feng.ast.dcl.TypeDeclarer;
import org.cossbow.feng.util.Optional;

/**
 * Type Variance: reference the type-parameter in the generic-type
 */
final
public class GenericType extends DefinedType {
    private final TypeParameter param;

    public GenericType(Position pos,
                       TypeParameter param) {
        super(pos);
        this.param = param;
    }

    public TypeParameter param() {
        return param;
    }

    /**
     * 克隆并把类型参数替换为 {@code np}（同 id 克隆）。
     * <p>
     * 分支收窄用：{@code T} 克隆出新 {@link TypeParameter}（保 id）并写入
     * 收窄约束，{@code replaceParam} 让类型引用点（声明/调用/返回类型）
     * 指向克隆——{@code equals} 仍与模板 T 相等，mono 期
     * {@link GenericMap#make} 以原 T 为键靠 HashMap equals 冲突命中替换。
     */
    public GenericType replaceParam(TypeParameter np) {
        return new GenericType(pos(), np);
    }

    public Identifier name() {
        return param.name();
    }

    public TypeDeclarer declarer(Optional<Refer> r) {
        if (r.none())
            return new GenericTypeDeclarer(pos(), this);
        return new GenericTypeDeclarer(pos(), this,
                Optional.of(r.get().kind()),
                r.get().required(), r.get().unmodifiable());
    }

    public boolean equals(Object o) {
        return o instanceof GenericType t
                && param.equals(t.param);
    }

    public int hashCode() {
        return param.hashCode();
    }

    //

    @Override
    public String toString() {
        return param.toString();
    }
}
