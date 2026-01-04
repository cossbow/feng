package org.cossbow.feng.ast.dcl;

import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.util.Optional;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum Primitive {
    BYTE("byte", Kind.INTEGER, 8),
    INT("int", Kind.INTEGER, 64),
    INT8("int8", Kind.INTEGER, 8),
    INT16("int16", Kind.INTEGER, 16),
    INT32("int32", Kind.INTEGER, 32),
    INT64("int64", Kind.INTEGER, 64),
    UINT("uint", Kind.INTEGER, 64),
    UINT8("uint8", Kind.INTEGER, 8),
    UINT16("uint16", Kind.INTEGER, 16),
    UINT32("uint32", Kind.INTEGER, 32),
    UINT64("uint64", Kind.INTEGER, 64),
    FLOAT("float", Kind.FLOAT, 64),
    FLOAT32("float32", Kind.FLOAT, 32),
    FLOAT64("float64", Kind.FLOAT, 64),
    BOOL("bool", Kind.BOOL, 8),
    ;

    public final String code;
    public final Kind kind;
    public final int width;

    Primitive(String code, Kind kind, int width) {
        this.code = code;
        this.kind = kind;
        this.width = width;
    }

    public PrimitiveTypeDeclarer declarer(
            Position pos, Optional<Refer> ref) {
        return new PrimitiveTypeDeclarer(pos, this, ref);
    }

    public PrimitiveTypeDeclarer declarer(Position pos) {
        return declarer(pos, Optional.empty());
    }

    public PrimitiveDefinition type() {
        return PrimitiveDefinition.types.get(this);
    }

    public int size() {
        return width >> 3;
    }

    public int align() {
        return size();
    }

    public boolean isInteger() {
        return kind == Kind.INTEGER;
    }

    public boolean isFloat() {
        return kind == Kind.FLOAT;
    }

    public boolean isBool() {
        return kind == Kind.BOOL;
    }

    public boolean isNumber() {
        return kind != Kind.BOOL;
    }

    //

    @Override
    public String toString() {
        return code;
    }

    //

    private static final Map<String, Primitive> CodeMap;

    public static Optional<Primitive> ofCode(String code) {
        return Optional.of(CodeMap.get(code));
    }

    public static Optional<Primitive> ofCode(Identifier id) {
        return ofCode(id.value());
    }

    public static Optional<PrimitiveDefinition> findType(String name) {
        var p = CodeMap.get(name);
        if (p == null) return Optional.empty();
        return Optional.of(p.type());
    }

    static {
        CodeMap = Arrays.stream(values()).collect(Collectors
                .toUnmodifiableMap(v -> v.code, Function.identity()));
    }

    //

    public enum Kind {
        INTEGER,
        FLOAT,
        BOOL,
        ;
    }
}
