package org.cossbow.feng.ast.lit;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.util.CommonUtil;

import java.nio.charset.Charset;
import java.util.Arrays;

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

    public String string() {
        return new String(value, charset);
    }

    public StringLiteral concat(StringLiteral o) {
        return new StringLiteral(pos(), charset,
                CommonUtil.concat(value, o.value));
    }

    @Override
    public String type() {
        return "string";
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof StringLiteral f)) return false;
        return Arrays.equals(value, f.value);
    }

    @Override
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
