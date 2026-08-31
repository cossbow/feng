package org.cossbow.feng.ast.dcl;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.gen.GenericType;
import org.cossbow.feng.ast.gen.TypeParameter;
import org.cossbow.feng.util.Optional;

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
        refer = kind.map(k ->
                new Refer(pos, k, required, unmodifiable));
    }

    public GenericTypeDeclarer(
            Position pos, GenericType type) {
        this(pos, type, Optional.empty(), true, false);
    }

    public GenericType type() {
        return type;
    }

    public Optional<ReferKind> kind() {
        return kind;
    }

    public boolean required() {
        return required;
    }

    public boolean unmodifiable() {
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
        if (kind.has()) return required;
        var ov = type.param().view();
        if (ov.none()) return false;
        return ov.get().hasRequiredInit();
    }

    //

    @Override
    public Optional<TypeDeclarer> derefer() {
        if (kind.none()) return Optional.of(this);
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
