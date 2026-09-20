package org.cossbow.feng.ast.expr;

import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.gen.DerivedType;
import org.cossbow.feng.ast.gen.GenericType;
import org.cossbow.feng.ast.gen.PrimitiveType;
import org.cossbow.feng.util.Optional;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.dcl.DerivedTypeDeclarer;
import org.cossbow.feng.ast.dcl.GenericTypeDeclarer;
import org.cossbow.feng.ast.dcl.NewArrayType;
import org.cossbow.feng.ast.dcl.NewDefinedType;
import org.cossbow.feng.ast.dcl.NewType;
import org.cossbow.feng.ast.dcl.PrimitiveTypeDeclarer;
import org.cossbow.feng.ast.gen.GenericMap;

/**
 * Creating an instance dynamically, the return type is a strong
 * reference to the corresponding primitive type.
 * <p>
 * {@code new(int)}, {@code new(int, 1)}
 * <p>
 * {@code new(Car)}, {@code new(Car, {id=1})}
 * <p>
 * {@code new([n]int)}, {@code new([n]int, [1])}
 */
public class NewExpression extends PrimaryExpression {
    private final NewType type;
    private final Optional<Expression> arg;

    public NewExpression(Position pos,
                         NewType type,
                         Optional<Expression> arg) {
        super(pos);
        this.type = type;
        this.arg = arg;
    }

    public NewType type() {
        return type;
    }

    public Optional<Expression> arg() {
        return arg;
    }

    /**
     * The dynamically created instance is unbound
     */
    @Override
    public boolean unbound() {
        return true;
    }

    @Override
    public boolean unique() {
        return true;
    }

    @Override
    public NewExpression mirror() {
        return new NewExpression(pos(), type, arg.map(Expression::mirror));
    }

    @Override
    public NewExpression mono(GenericMap gm) {
        return monoCopy(new NewExpression(pos(), monoType(type, gm),
                arg.map(e -> e.mono(gm))), gm);
    }

    /**
     * mono2 具体化时必须同步替换 new 的目标类型：{@code new(Box`T`)} 在
     * {@code make`T`} 实例化后应为 {@code new(Box_int)}，否则后端残留
     * 未替换的 {@code Box_T} 类型名。
     */
    private static NewType monoType(NewType type, GenericMap gm) {
        if (type instanceof NewDefinedType ndt) {
            var dt = ndt.type();
            // PrimitiveType 不变；DerivedType（含泛型实参）与 GenericType（类型变量）
            // 需按 gm 替换——后者对 static-if 分支收窄必要：new(T) 在收窄分支
            // 中应映射为收窄后的克隆参数（约束含 ==X，newable 才成立）。
            if (dt instanceof PrimitiveType)
                return type;
            if (dt instanceof DerivedType d)
                return new NewDefinedType(type.pos(), gm.mapIf(d));
            // GenericType：按 gm 替换。gm 无 T → 原样（mapIf(GenericTypeDeclarer)
            // 找不到时返回原 gtd）；T→克隆参数（收窄）→ replaceParam；
            // T→具体类型（实例化）→ 转成 DefinedType 重建 new 目标。
            var gtd0 = ((GenericType) dt).declarer(Optional.empty());
            var t = gm.mapIf(gtd0);
            if (t == gtd0) return type;
            if (t instanceof GenericTypeDeclarer gtd)
                return new NewDefinedType(type.pos(),
                        ((GenericType) dt).replaceParam(gtd.param()));
            if (t instanceof DerivedTypeDeclarer dtd)
                return new NewDefinedType(type.pos(), dtd.derivedType());
            if (t instanceof PrimitiveTypeDeclarer ptd)
                return new NewDefinedType(type.pos(),
                        new PrimitiveType(type.pos(), new Identifier(ptd.primitive().code),
                                ptd.primitive()));
            return type;
        }
        if (type instanceof NewArrayType nat) {
            return new NewArrayType(type.pos(),
                    gm.mapIf(nat.element()),
                    nat.length().mono(gm));
        }
        return type;
    }

    //

    @Override
    public String toString() {
        if (arg.none()) return "new(" + type + ")";
        return "new(" + type + ", " + arg.get() + ")";
    }
}
