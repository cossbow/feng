package org.cossbow.feng.parser;


import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.cossbow.feng.ast.*;
import org.cossbow.feng.ast.dcl.DefinedTypeDeclarer;
import org.cossbow.feng.ast.dcl.TypeDeclarer;
import org.cossbow.feng.ast.expr.Expression;
import org.cossbow.feng.ast.expr.LiteralExpression;
import org.cossbow.feng.ast.expr.ReferExpression;
import org.cossbow.feng.ast.lit.IntegerLiteral;
import org.cossbow.feng.ast.lit.Literal;
import org.cossbow.feng.ast.lit.StringLiteral;
import org.cossbow.feng.ast.mod.Global;
import org.cossbow.feng.ast.mod.GlobalDefinition;
import org.cossbow.feng.ast.proc.FunctionDefinition;
import org.cossbow.feng.ast.stmt.ArrayTuple;
import org.cossbow.feng.ast.stmt.CallStatement;
import org.cossbow.feng.ast.stmt.Statement;
import org.cossbow.feng.ast.stmt.Tuple;
import org.junit.jupiter.api.Assertions;

import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.stream.Collectors;

abstract
public class BaseParseTest {

    public static final IntFunction<Identifier> RandVarFuncName = BaseParseTest::randVarFuncName;
    public static final IntFunction<Identifier> RandTypeName = BaseParseTest::randTypeName;

    static final String ALL = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz_";
    static final int EDGE_DIGITS = 10;

    static void checkInstances(List<?> instances,
                               List<Class<?>> types) {
        Assertions.assertEquals(instances.size(), types.size());
        for (int i = 0; i < instances.size(); i++) {
            Assertions.assertInstanceOf(types.get(i), instances.get(i));
        }
    }

    static final int EDGE_UPPERCASE = EDGE_DIGITS + 26;
    static final int EDGE_LOWERCASE = EDGE_UPPERCASE + 26;
    static final int EDGE_ALL = EDGE_LOWERCASE + 1;

    static FileParser doParse(CharStream cs) {
        var p = new FileParser();
        p.parse(cs);
        return p;
    }

    static FileParser doParse(String code) {
        var p = doParse(CharStreams.fromString(code));
        Assertions.assertTrue(p.errors().isEmpty(), "parse error: " + code);
        return p;
    }

    public static Global doParseGlobal(String def) {
        var p = doParse(def);
        Assertions.assertEquals(1, p.root().definitions().size());
        return p.root().definitions().getFirst();
    }

    public static Definition doParseDefinition(String def) {
        var gd = (GlobalDefinition) doParseGlobal(def);
        return gd.definition();
    }

    public static final Map<Enum<?>, String> operatorSymbols = Map.ofEntries(
            Map.entry(BinaryOperator.POW, "^"),
            Map.entry(BinaryOperator.MUL, "*"),
            Map.entry(BinaryOperator.DIV, "/"),
            Map.entry(BinaryOperator.MOD, "%"),
            Map.entry(BinaryOperator.ADD, "+"),
            Map.entry(BinaryOperator.SUB, "-"),
            Map.entry(BinaryOperator.LSHIFT, "<<"),
            Map.entry(BinaryOperator.RSHIFT, ">>"),
            Map.entry(BinaryOperator.BITAND, "&"),
            Map.entry(BinaryOperator.BITXOR, "~"),
            Map.entry(BinaryOperator.BITOR, "|"),
            Map.entry(BinaryOperator.EQ, "=="),
            Map.entry(BinaryOperator.NE, "!="),
            Map.entry(BinaryOperator.GT, ">"),
            Map.entry(BinaryOperator.LT, "<"),
            Map.entry(BinaryOperator.LE, "<="),
            Map.entry(BinaryOperator.GE, ">="),
            Map.entry(BinaryOperator.AND, "&&"),
            Map.entry(BinaryOperator.OR, "||"),

            Map.entry(UnaryOperator.POSITIVE, "+"),
            Map.entry(UnaryOperator.NEGATIVE, "-"),
            Map.entry(UnaryOperator.INVERT, "!")
    );

    public static String symbol(BinaryOperator op) {
        return Objects.requireNonNull(operatorSymbols.get(op));
    }

    public static String symbol(UnaryOperator op) {
        return Objects.requireNonNull(operatorSymbols.get(op));
    }

    //

    public static TypeDefinition doParseType(String def) {
        return (TypeDefinition) doParseDefinition(def);
    }

    public static ProcDefinition doParseProc(String def) {
        return (ProcDefinition) doParseDefinition(def);
    }

    public static Statement doParseLocal(String stmt) {
        var fun = "func main() { %s }".formatted(stmt);
        var func = (FunctionDefinition) doParseProc(fun);
        return func.procedure().body().list().getFirst();
    }

    public static Identifier identifier(String value) {
        return new Identifier(Position.ZERO, value);
    }

    public static List<Identifier> identifiers(String... values) {
        return identifiers(Arrays.asList(values));
    }

    public static List<Identifier> identifiers(List<String> values) {
        return values.stream().map(BaseParseTest::identifier).toList();
    }

    public static Identifier randVarFuncName(int len) {
        var random = ThreadLocalRandom.current();
        var sb = new StringBuilder(len);
        sb.append(ALL.charAt(random.nextInt(EDGE_UPPERCASE, EDGE_LOWERCASE)));
        for (int i = 1; i < len; i++) {
            sb.append(ALL.charAt(random.nextInt(EDGE_ALL)));
        }
        return identifier(sb.toString());
    }

    public static Identifier randTypeName(int len) {
        var random = ThreadLocalRandom.current();
        var sb = new StringBuilder(len);
        sb.append(ALL.charAt(random.nextInt(EDGE_DIGITS, EDGE_UPPERCASE)));
        for (int i = 1; i < len; i++) {
            sb.append(ALL.charAt(random.nextInt(EDGE_ALL)));
        }
        return identifier(sb.toString());
    }

    public static List<Identifier> anyNames(IntFunction<Identifier> gen, int len, int size) {
        var names = new LinkedHashSet<Identifier>(size);
        for (int i = 0; i < size; i++) {
            names.add(gen.apply(len));
        }
        return new ArrayList<>(names);
    }

    public static String idList(Collection<Identifier> src) {
        return src.stream().map(Objects::toString).collect(Collectors.joining(","));
    }

    public static String idList(Identifier[] src) {
        return Arrays.stream(src).map(Objects::toString).collect(Collectors.joining(","));
    }

    public static void appendList(StringBuilder sb, List<Identifier> li,
                                  String prefix, String suffix) {
        for (var f : li) sb.append(prefix).append(f).append(suffix);
    }
    //

    public static BigInteger randInt(int origin, int bound) {
        var v = ThreadLocalRandom.current().nextInt(origin, bound);
        return BigInteger.valueOf(v);
    }

    public static Literal lit(Expression expr) {
        return ((LiteralExpression) expr).literal();
    }

    public static String string(Expression expr) {
        return ((StringLiteral) lit(expr)).value();
    }

    public static IntegerLiteral integer(Expression expr) {
        return (IntegerLiteral) lit(expr);
    }

    public static Identifier varName(Expression expr) {
        return ((ReferExpression) expr).name();
    }

    public static Identifier calleeName(Statement stmt) {
        return varName(((CallStatement) stmt).call().callee());
    }

    public static List<Expression> exprs(Tuple tuple) {
        return ((ArrayTuple) tuple).values();
    }

    public static Expression first(Tuple tuple) {
        return exprs(tuple).getFirst();
    }


    public static <T> void checkIds(List<Identifier> names,
                                    List<T> list,
                                    Function<T, Identifier> trans) {
        Assertions.assertEquals(names.size(), list.size());
        for (int i = 0; i < names.size(); i++) {
            Assertions.assertEquals(names.get(i), trans.apply(list.get(i)));
        }
    }

    public static void checkIds(List<Identifier> names, List<Identifier> vars) {
        checkIds(names, vars, Function.identity());
    }


    public static Identifier typeName(TypeDeclarer td) {
        return ((DefinedTypeDeclarer) td).definedType().name();
    }

}
