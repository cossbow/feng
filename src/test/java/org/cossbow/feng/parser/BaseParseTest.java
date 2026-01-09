package org.cossbow.feng.parser;


import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.cossbow.feng.ast.*;
import org.cossbow.feng.ast.Optional;
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
import org.cossbow.feng.ast.stmt.*;
import org.junit.jupiter.api.Assertions;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.stream.Collectors;

abstract
public class BaseParseTest {

    public static final IntFunction<Identifier> RandVarFuncName = BaseParseTest::randVarName;
    public static final IntFunction<Identifier> RandTypeName = BaseParseTest::randTypeName;
    public static final IntFunction<Symbol> RandVarSymbol = BaseParseTest::randVarSymbol;
    public static final IntFunction<Symbol> RandTypeSymbol = BaseParseTest::randTypeSymbol;

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

    static ParseResult doParse(CharStream cs) {
        return new SourceParser("", new GlobalSymbolTable()).parse(cs);
    }

    static Source doParseFile(String code, String name) {
        var r = doParse(CharStreams.fromString(code, name));
        Assertions.assertTrue(r.errors().isEmpty(),
                "parse %s error: %s".formatted(name, code));
        return r.root();
    }

    static Source doParseFile(String code) {
        return doParseFile(code, "unknow");
    }

    static Source doParseFile(InputStream is, String name) {
        try {
            var r = doParse(CharStreams.fromStream(is));
            Assertions.assertTrue(r.errors().isEmpty(),
                    "parse %s error: %s".formatted(name, r.errors()));
            return r.root();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static Global doParseGlobal(String def) {
        var sf = doParseFile(def, "global");
        Assertions.assertEquals(1, sf.definitions().size());
        return sf.definitions().getFirst();
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

    public static String operator(BinaryOperator op) {
        return Objects.requireNonNull(operatorSymbols.get(op));
    }

    public static String operator(UnaryOperator op) {
        return Objects.requireNonNull(operatorSymbols.get(op));
    }

    //

    public static FunctionDefinition doParseProc(String def) {
        return (FunctionDefinition) doParseDefinition(def);
    }

    public static Statement doParseLocal(String stmt) {
        var fun = "func main() { %s }".formatted(stmt);
        var func = doParseProc(fun);
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

    public static Identifier randVarName(int len) {
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

    public static Symbol symbol(Identifier name) {
        return new Symbol(name.pos(), name);
    }

    public static Symbol symbol(String name) {
        return symbol(identifier(name));
    }

    public static Symbol randTypeSymbol(int len) {
        return symbol(randTypeName(len));
    }

    public static Symbol randVarSymbol(int len) {
        return symbol(randVarName(len));
    }

    public static <I> List<I> anyNames(IntFunction<I> gen, int len, int size) {
        var names = new LinkedHashSet<I>(size);
        for (int i = 0; i < size; i++) {
            names.add(gen.apply(len));
        }
        return new ArrayList<>(names);
    }

    public static <I> String idList(Collection<I> src) {
        return src.stream().map(Objects::toString)
                .collect(Collectors.joining(","));
    }

    public static <I> void appendList(StringBuilder sb, List<I> li,
                                      String prefix, String suffix) {
        for (var f : li) sb.append(prefix).append(f).append(suffix);
    }

    //

    public static BigInteger randInt(int origin, int bound) {
        var v = ThreadLocalRandom.current().nextInt(origin, bound);
        return BigInteger.valueOf(v);
    }

    //

    public static Literal lit(Expression expr) {
        return ((LiteralExpression) expr).literal();
    }

    public static String string(Expression expr) {
        return ((StringLiteral) lit(expr)).value();
    }

    public static IntegerLiteral integer(Expression expr) {
        return (IntegerLiteral) lit(expr);
    }

    public static IntegerLiteral integer(Optional<Expression> expr) {
        return integer(expr.must());
    }

    public static Symbol varName(Expression expr) {
        return ((ReferExpression) expr).symbol();
    }

    public static Symbol calleeName(Statement stmt) {
        return varName(((CallStatement) stmt).call().callee());
    }

    public static List<Expression> exprs(Tuple tuple) {
        return ((ArrayTuple) tuple).values();
    }

    public static Expression first(Tuple tuple) {
        if (tuple instanceof ReturnTuple rt)
            return rt.call();
        return exprs(tuple).getFirst();
    }


    public static <T, R> void checkIds(List<R> names,
                                       IdentifierTable<T> table) {
        Assertions.assertEquals(names.size(), table.size());
        for (int i = 0; i < names.size(); i++) {
            Assertions.assertEquals(names.get(i), table.getKey(i));
        }
    }

    public static <I, T> void checkIds(List<I> names,
                                       List<T> list,
                                       Function<T, I> trans) {
        Assertions.assertEquals(names.size(), list.size());
        for (int i = 0; i < names.size(); i++) {
            Assertions.assertEquals(names.get(i), trans.apply(list.get(i)));
        }
    }

    public static Symbol typeName(TypeDeclarer td) {
        return ((DefinedTypeDeclarer) td).definedType().symbol();
    }

}
