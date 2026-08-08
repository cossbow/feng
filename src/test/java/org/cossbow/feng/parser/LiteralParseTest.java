package org.cossbow.feng.parser;

import org.cossbow.feng.ast.expr.LiteralExpression;
import org.cossbow.feng.ast.lit.*;
import org.cossbow.feng.ast.lit.IntegerLiteral.Radix;
import org.cossbow.feng.ast.stmt.AssignmentsStatement;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;

public class LiteralParseTest extends BaseParseTest {

    Literal parseLiteral(String lit) {
        var stmt = "a = %s;".formatted(lit);
        var as = (AssignmentsStatement) doParseLocal(stmt);
        var expr = (LiteralExpression) as.value(0);
        return expr.literal();
    }

    @Test
    public void testInteger() {
        final int N = 10000;

        for (int i = 0; i < N; i++) {
            var s = Integer.toString(i, 10);
            var dec = (IntegerLiteral) parseLiteral(s);
            Assertions.assertSame(Radix.DEC, dec.radix());
            Assertions.assertEquals(new BigInteger(s), dec.value());
        }

        for (int i = 0; i < N; i++) {
            var s = Integer.toHexString(i);
            var hex = (IntegerLiteral) parseLiteral("0x" + s);
            Assertions.assertSame(Radix.HEX, hex.radix());
            Assertions.assertEquals(new BigInteger(s, 16), hex.value());
        }
        for (int i = 0; i < N; i++) {
            var s = Integer.toHexString(i).toUpperCase();
            var hex = (IntegerLiteral) parseLiteral("0X" + s);
            Assertions.assertSame(Radix.HEX, hex.radix());
            Assertions.assertEquals(new BigInteger(s, 16), hex.value());
        }

        for (int i = 0; i < N; i++) {
            var s = Integer.toOctalString(i);
            var oct = (IntegerLiteral) parseLiteral("0o" + s);
            Assertions.assertSame(Radix.OCT, oct.radix());
            Assertions.assertEquals(new BigInteger(s, 8), oct.value());
        }
        for (int i = 0; i < N; i++) {
            var s = Integer.toOctalString(i).toUpperCase();
            var oct = (IntegerLiteral) parseLiteral("0O" + s);
            Assertions.assertSame(Radix.OCT, oct.radix());
            Assertions.assertEquals(new BigInteger(s, 8), oct.value());
        }

        for (int i = 0; i < N; i++) {
            var s = Integer.toBinaryString(i);
            var oct = (IntegerLiteral) parseLiteral("0b" + s);
            Assertions.assertSame(Radix.BIN, oct.radix());
            Assertions.assertEquals(new BigInteger(s, 2), oct.value());
        }
        for (int i = 0; i < N; i++) {
            var s = Integer.toBinaryString(i).toUpperCase();
            var oct = (IntegerLiteral) parseLiteral("0B" + s);
            Assertions.assertSame(Radix.BIN, oct.radix());
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
        for (int i = 0x20; i < 0x7e; i++) {
            sb.append((char) i);
        }
        // 源码中的转义序列 \" 和 \\ 在 unescape 后变为实际字符
        var expected = sb.toString();
        // 构造带转义的源码字符串
        var src = new StringBuilder();
        src.append('"');
        for (int i = 0x20; i < 0x7e; i++) {
            if (i == '"' || i == '\\')
                src.append('\\');
            src.append((char) i);
        }
        src.append('"');
        var sl = (StringLiteral) parseLiteral(src.toString());
        Assertions.assertEquals(expected, sl.string());
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
