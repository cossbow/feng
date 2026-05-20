package org.cossbow.feng.ast.lit;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.dcl.ArrayTypeDeclarer;
import org.cossbow.feng.ast.dcl.Primitive;
import org.cossbow.feng.ast.dcl.Refer;
import org.cossbow.feng.ast.dcl.ReferKind;
import org.cossbow.feng.util.CommonUtil;
import org.cossbow.feng.util.Optional;

import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

import static org.cossbow.feng.ast.Position.*;

public class StringLiteral extends Literal {
    private final Charset charset;
    private final byte[] value;

    public StringLiteral(Position pos,
                         Charset charset,
                         byte[] value) {
        super(pos);
        this.charset = charset;
        this.value = value;
    }

    public Charset charset() {
        return charset;
    }

    public byte[] value() {
        return value;
    }

    public int length() {
        return value.length;
    }

    //

    private String s;

    public String string() {
        if (s == null) s = new String(value, charset);
        return s;
    }

    public StringLiteral concat(StringLiteral o) {
        return new StringLiteral(pos(), charset,
                CommonUtil.concat(value, o.value));
    }

    public ArrayTypeDeclarer array(Optional<ReferKind> kind) {
        var et = Primitive.BYTE.declarer(ZERO);
        if (kind.none())
            return ArrayTypeDeclarer.make(et, value.length, ZERO);

        var r = kind.map(k ->
                new Refer(ZERO, k, true, true));
        return ArrayTypeDeclarer.make(et, r, ZERO);
    }

    //

    private final int id = idGenerator.getAndIncrement();

    public int id() {
        return id;
    }

    private static final AtomicInteger idGenerator = new AtomicInteger();

    //

    public String type() {
        return "string";
    }

    public boolean equals(Object o) {
        return o instanceof StringLiteral f &&
                Arrays.equals(value, f.value);
    }

    public int hashCode() {
        return 31 * charset.hashCode() +
                Arrays.hashCode(value);
    }

    //

    @Override
    public String toString() {
        return '"' + string() + '"';
    }
}
