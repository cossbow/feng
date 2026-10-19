package org.cossbow.feng.ast.dcl;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.gen.GenericType;
import org.cossbow.feng.ast.gen.TypeParameter;
import org.cossbow.feng.ast.type.Tri;
import org.cossbow.feng.util.Optional;

import static org.cossbow.feng.util.ErrorUtil.semantic;

/**
 * Use type variant as type
 */
public class GenericTypeDeclarer extends TypeDeclarer
        implements Referable {
    private final GenericType type;
    private final Optional<ReferKind> kind;
    private final boolean required;
    private final boolean unmodifiable;

    private final Optional<Refer> refer;

    public GenericTypeDeclarer(Position pos,
                               GenericType type,
                               Optional<ReferKind> kind,
                               boolean required,
                               boolean unmodifiable) {
        super(pos);
        this.type = type;
        this.kind = kind;
        this.required = required;
        this.unmodifiable = unmodifiable;
        refer = kind.has()
                ? Optional.of(new Refer(pos, kind.get(), required, unmodifiable))
                : deriveRefer();
    }

    /**
     * 语法无 {@code *} 标记时，由约束投影派生引用属性。
     * 仅当约束投影为"必引用"（{@code referenced()==YES}）才派生 {@link Refer}，
     * 否则裸类型参数视为值类型，不具引用属性。
     * <p>
     * required/unmodifiable 由 optional/unmodifiable 投影按保守方向收敛：
     * <ul>
     *   <li>{@code optional()==NO}（必非空）→ required=true；
     *       {@code YES/BOTH}（必可空 / 不确定）→ required=false（不确定则判空，需判空检查）；</li>
     *   <li>{@code unmodifiable()==NO}（必可修）→ unmodifiable=false；
     *       {@code YES/BOTH}（必不可改 / 不确定）→ unmodifiable=true（不确定则判不可改）。</li>
     * </ul>
     */
    private Optional<Refer> deriveRefer() {
        var p = type.param();
        if (p.referenced() != Tri.YES) return Optional.empty();
        return Optional.of(new Refer(pos(), ReferKind.STRONG,
                p.optional() == Tri.NO,         // 仅 NO → 非空；YES/BOTH → 可空（BOTH 判空）
                p.unmodifiable() != Tri.NO));   // 仅 NO → 可修；YES/BOTH → 不可改
    }

    public GenericTypeDeclarer(
            Position pos, GenericType type) {
        this(pos, type, Optional.empty(), true, false);
    }

    /**
     * 构造一个语法无引用记号（{@code kind} 空）、但携带派生引用的类型参数。
     * <p>
     * 由分析器在解析约束后补全引用形态（如 interface 域必引用）时使用：
     * {@code kind} 空使 {@code analyse(GenericTypeDeclarer)} 幂等（重分析不触发
     * 语法 {@code *T} 的多级引用拒绝），而 {@code refer} 有值使消费点
     * （可调用/判空/requiredInit 等）将其视为引用。
     */
    public GenericTypeDeclarer(Position pos, GenericType type, Refer refer) {
        super(pos);
        this.type = type;
        this.kind = Optional.empty();
        this.required = refer.required();
        this.unmodifiable = refer.unmodifiable();
        this.refer = Optional.of(refer);
    }

    public GenericType type() {
        return type;
    }

    public Optional<ReferKind> kind() {
        return kind;
    }

    public boolean required() {
        // 语法无 * 标记且派生引用存在时，required 由派生 refer（约束投影收敛）决定
        if (refer.has()) return refer.get().required();
        return required;
    }

    /**
     * 语法层面的 required（是否写了 {@code ?} 标记），不受约束投影影响。
     * 供 {@code ?T} 形态检查使用——{@code ?T} 是语法构造，其可空性不能由约束投影改写。
     */
    public boolean syntacticRequired() {
        return required;
    }

    public boolean unmodifiable() {
        // 语法无 * 标记且派生引用存在时，unmodifiable 由派生 refer（约束投影收敛）决定
        if (refer.has()) return refer.get().unmodifiable();
        return unmodifiable;
    }

    public TypeParameter param() {
        return type.param();
    }

    public Optional<Refer> refer() {
        return refer;
    }

    public boolean hasTypeVar() {
        return true;
    }

    public boolean requiredInit() {
        if (refer.has()) return refer.get().required();
        return type.param().members().hasRequiredInit();
    }

    //

    @Override
    public Optional<TypeDeclarer> derefer() {
        if (kind.none()) {
            // 裸 T 语法无 * 标记：若约束投影出引用（派生 refer），
            // deref 只对语法标记的 *T 有效——T 本身是引用则无法解引用。
            if (refer.has())
                return semantic("can't de-refer a reference-type parameter: %s", pos());
            return Optional.of(this);
        }
        return Optional.of(new GenericTypeDeclarer(pos(),
                type, Optional.empty(), required, unmodifiable));
    }


    //

    @Override
    public final boolean equals(Object o) {
        if (!(o instanceof GenericTypeDeclarer t)) return false;

        return type.equals(t.type) &&
                kind.equals(t.kind) &&
                required == t.required &&
                unmodifiable == t.unmodifiable;
    }

    @Override
    public int hashCode() {
        int result = type.hashCode();
        result = 31 * result + kind.hashCode();
        result = 31 * result + Boolean.hashCode(required);
        result = 31 * result + Boolean.hashCode(unmodifiable);
        return result;
    }


    //

    @Override
    public String toString() {
        if (kind.none()) return (required ? "" : "?") + type;
        return kind.get().symbol + (required ? "" : "?") +
                (unmodifiable ? "#" : "") + type;
    }
}
