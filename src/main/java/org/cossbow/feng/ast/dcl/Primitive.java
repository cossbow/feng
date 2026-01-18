package org.cossbow.feng.ast.dcl;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.util.Optional;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum Primitive {
    INT("int", Kind.INTEGER),
    INT8("int8", Kind.INTEGER),
    INT16("int16", Kind.INTEGER),
    INT32("int32", Kind.INTEGER),
    INT64("int64", Kind.INTEGER),
    UINT("uint", Kind.INTEGER),
    UINT8("uint8", Kind.INTEGER),
    UINT16("uint16", Kind.INTEGER),
    UINT32("uint32", Kind.INTEGER),
    UINT64("uint64", Kind.INTEGER),
    FLOAT("float", Kind.FLOAT),
    FLOAT32("float32", Kind.FLOAT),
    FLOAT64("float64", Kind.FLOAT),
    BOOL("bool", Kind.BOOL),
    ;

    public final String code;
    public final Kind kind;

    Primitive(String code, Kind kind) {
        this.code = code;
        this.kind = kind;
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

    public PrimitiveTypeDeclarer declarer(Position pos) {
        return new PrimitiveTypeDeclarer(pos, this);
    }

    @Override
    public String toString() {
        return code;
    }

    //

    private static final Map<String, Primitive> CodeMap;

    public static Optional<Primitive> ofCode(String code) {
        return Optional.of(CodeMap.get(code));
    }

    public static Optional<PrimitiveDefinition> findType(String name) {
        var p = CodeMap.get(name);
        if (p == null) return Optional.empty();
        return Optional.of(PrimitiveDefinition.types.get(p));
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
