package org.cossbow.feng.gen;

import org.cossbow.feng.ast.expr.Expression;
import org.cossbow.feng.ast.stmt.AssignmentsStatement;
import org.cossbow.feng.parser.BaseParseTest;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;

public class GenCCodeTest {

    public static Expression parseExpr(String s) {
        var stmt = (AssignmentsStatement) BaseParseTest.doParseLocal("a = " + s + ";");
        return BaseParseTest.first(stmt.tuple());
    }

    public static String toC(Expression expr) {
        try {
            var sb = new StringBuilder();
            new GenCCode().writeExpression(sb, expr);
            return sb.toString();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    public void testUnaryExpr1() {
        var expr = parseExpr("!+--127");
        System.out.println(toC(expr));
    }

    @Test
    public void testUnaryExpr2() {
        var expr = parseExpr("!+--a");
        System.out.println(toC(expr));
    }

    @Test
    public void testUnaryExpr3() {
        var expr = parseExpr("!+--math#PI");
        System.out.println(toC(expr));
    }

    @Test
    public void testBinaryExpr1() {
        var expr = parseExpr("12+7-9");
        System.out.println(toC(expr));
    }

    @Test
    public void testBinaryExpr2() {
        var expr = parseExpr("12+7/9-66");
        System.out.println(toC(expr));
    }

    @Test
    public void testBinaryExpr3() {
        var expr = parseExpr("12+7/9^-6.6");
        System.out.println(toC(expr));
    }

    @Test
    public void testBinaryExpr4() {
        var expr = parseExpr("a+b/c^-d");
        System.out.println(toC(expr));
    }

    @Test
    public void testBinaryExpr5() {
        var expr = parseExpr("m#a+l#b/u#c^-v#d");
        System.out.println(toC(expr));
    }

}
