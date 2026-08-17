package org.cossbow.feng.ast.expr;

import org.cossbow.feng.ast.gen.DerivedType;
import org.cossbow.feng.util.Optional;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.dcl.NewArrayType;
import org.cossbow.feng.ast.dcl.NewDefinedType;
import org.cossbow.feng.ast.dcl.NewType;
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
            // PrimitiveType / GenericType 不变
            if (!(dt instanceof DerivedType d))
                return type;
            // DerivedType（含泛型实参）需按 gm 替换
            return new NewDefinedType(type.pos(), gm.mapIf(d));
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
