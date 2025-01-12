package org.cossbow.feng.parser;

import org.cossbow.feng.ast.expr.LiteralExpression;
import org.cossbow.feng.ast.lit.*;
import org.cossbow.feng.ast.stmt.AssignmentsStatement;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;

public class LiteralParseTest extends BaseParseTest {

    Literal parseLiteral(String lit) {
        var stmt = "a = %s;".formatted(lit);
        var as = (AssignmentsStatement) doParseLocal(stmt);
        var expr = (LiteralExpression) first(as.tuple());
        return expr.literal();
    }

    @Test
    public void testInteger() {
        final int N = 10000;

        for (int i = 0; i < N; i++) {
            var s = Integer.toString(i, 10);
            var dec = (IntegerLiteral) parseLiteral(s);
            Assertions.assertSame(10, dec.radix());
            Assertions.assertEquals(new BigInteger(s), dec.value());
        }

        for (int i = 0; i < N; i++) {
            var s = Integer.toHexString(i);
            var hex = (IntegerLiteral) parseLiteral("0x" + s);
            Assertions.assertSame(16, hex.radix());
            Assertions.assertEquals(new BigInteger(s, 16), hex.value());
        }
        for (int i = 0; i < N; i++) {
            var s = Integer.toHexString(i).toUpperCase();
            var hex = (IntegerLiteral) parseLiteral("0X" + s);
            Assertions.assertSame(16, hex.radix());
            Assertions.assertEquals(new BigInteger(s, 16), hex.value());
        }

        for (int i = 0; i < N; i++) {
            var s = Integer.toOctalString(i);
            var oct = (IntegerLiteral) parseLiteral("0o" + s);
            Assertions.assertSame(8, oct.radix());
            Assertions.assertEquals(new BigInteger(s, 8), oct.value());
        }
        for (int i = 0; i < N; i++) {
            var s = Integer.toOctalString(i).toUpperCase();
            var oct = (IntegerLiteral) parseLiteral("0O" + s);
            Assertions.assertSame(8, oct.radix());
            Assertions.assertEquals(new BigInteger(s, 8), oct.value());
        }

        for (int i = 0; i < N; i++) {
            var s = Integer.toBinaryString(i);
            var oct = (IntegerLiteral) parseLiteral("0b" + s);
            Assertions.assertSame(2, oct.radix());
            Assertions.assertEquals(new BigInteger(s, 2), oct.value());
        }
        for (int i = 0; i < N; i++) {
            var s = Integer.toBinaryString(i).toUpperCase();
            var oct = (IntegerLiteral) parseLiteral("0B" + s);
            Assertions.assertSame(2, oct.radix());
            Assertions.assertEquals(new BigInteger(s, 2), oct.value());
        }
    }

    @Test
    public void testFloat() {
        String[] values = {"321.", "321.123", "321.", "321e319", "321E319", "321.321e57"};
        for (String v : values) {
            var fl = (FloatLiteral) parseLiteral(v);
            Assertions.assertEquals(new BigDecimal(v), fl.value());
        }
    }

    @Test
    public void testString() {
        var sb = new StringBuilder();
        sb.append('"');
        for (int i = 0x20; i < 0x7e; i++) {
            if (i == '"' || i == '\\')
                sb.append('\\');
            sb.append((char) i);
        }
        sb.append('"');
        var value = sb.toString();
        var sl = (StringLiteral) parseLiteral(value);
        Assertions.assertEquals(value, '"' + sl.value() + '"');
    }

    @Test
    public void testBool() {
        {
            var bl = (BoolLiteral) parseLiteral("true");
            Assertions.assertTrue(bl.value());
        }
        {
            var bl = (BoolLiteral) parseLiteral("false");
            Assertions.assertFalse(bl.value());
        }
    }

    @Test
    public void testNil() {
        var nl = parseLiteral("nil");
        Assertions.assertInstanceOf(NilLiteral.class, nl);
    }

}
