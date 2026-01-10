package org.cossbow.feng.ast.dcl;

import org.cossbow.feng.util.Optional;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum Primitive {
    INT("int", true),
    INT8("int8", true),
    INT16("int16", true),
    INT32("int32", true),
    INT64("int64", true),
    UINT("uint", true),
    UINT8("uint8", true),
    UINT16("uint16", true),
    UINT32("uint32", true),
    UINT64("uint64", true),
    FLOAT("float", false),
    FLOAT32("float32", false),
    FLOAT64("float64", false),
    BOOL("bool", true),
    ;

    public final String code;
    public final boolean integer;

    Primitive(String code, boolean integer) {
        this.code = code;
        this.integer = integer;
    }

    //

    private static final Map<String, Primitive> CodeMap;

    public static Optional<Primitive> ofCode(String code) {
        return Optional.of(CodeMap.get(code));
    }

    static {
        CodeMap = Arrays.stream(values()).collect(Collectors
                .toUnmodifiableMap(v -> v.code, Function.identity()));
    }
}
