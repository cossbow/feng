package org.cossbow.feng.parser;


import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.cossbow.feng.ast.*;
import org.cossbow.feng.ast.dcl.DerivedTypeDeclarer;
import org.cossbow.feng.ast.dcl.TypeDeclarer;
import org.cossbow.feng.ast.expr.Expression;
import org.cossbow.feng.ast.expr.LiteralExpression;
import org.cossbow.feng.ast.expr.SymbolExpression;
import org.cossbow.feng.ast.lit.IntegerLiteral;
import org.cossbow.feng.ast.lit.Literal;
import org.cossbow.feng.ast.lit.StringLiteral;
import org.cossbow.feng.ast.proc.FunctionDefinition;
import org.cossbow.feng.ast.stmt.CallStatement;
import org.cossbow.feng.ast.stmt.Statement;
import org.cossbow.feng.util.ErrorUtil;
import org.cossbow.feng.util.Optional;
import org.junit.jupiter.api.Assertions;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
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

    static Source doParse(CharStream cs) {
        return new SourceParser(StandardCharsets.UTF_8)
                .parse("", cs);
    }

    public static Source doParseFile(String code) {
        return doParse(CharStreams.fromString(code, "test"));
    }

    public static Source doParseFile(InputStream is) {
        try {
            return doParse(CharStreams.fromStream(is));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static ParseSymbolTable parseTable(String code) {
        return doParseFile(code).table();
    }

    public static ParseSymbolTable parseTable(InputStream is) {
        return doParseFile(is).table();
    }

    public static Definition firstDef(Source src) {
        var type = src.table().types.stream()
                .filter(t -> !t.builtin()).findFirst();
        if (type.isPresent()) return type.get();
        var functions = src.table().functions;
        if (!functions.isEmpty()) return functions.getValue(0);
        return ErrorUtil.syntax("parse fail");
    }

    public static TypeDefinition doParseType(String def, Identifier name) {
        var src = doParseFile(def);
        return src.table().types.get(name);
    }

    public static TypeDefinition doParseType(String def, Symbol name) {
        return doParseType(def, name.name());
    }

    public static TypeDefinition doParseType(String def, String name) {
        return doParseType(def, identifier(name));
    }

    public static FunctionDefinition doParseFunc(String def, Identifier name) {
        var src = doParseFile(def);
        return src.table().functions.get(name);
    }

    public static FunctionDefinition doParseFunc(String def, String name) {
        return doParseFunc(def, identifier(name));
    }

    public static GlobalVariable doParseDeclaration(String def) {
        var src = doParseFile(def);
        return src.table().variables.getValue(0);
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
        return op.code;
    }

    public static String operator(UnaryOperator op) {
        return op.code;
    }

    //

    public static FunctionDefinition doParseProc(String def) {
        var src = doParseFile(def);
        return src.table().functions.getValue(0);
    }

    public static Statement doParseLocal(String stmt) {
        var fun = "func main() { %s }".formatted(stmt);
        var src = doParseFile(fun);
        var func = src.table().main.must();
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
        return new Symbol(name);
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
        return ((StringLiteral) lit(expr)).string();
    }

    public static IntegerLiteral integer(Expression expr) {
        return (IntegerLiteral) lit(expr);
    }

    public static IntegerLiteral integer(Optional<Expression> expr) {
        return integer(expr.must());
    }

    public static Symbol varName(Expression expr) {
        return ((SymbolExpression) expr).symbol();
    }

    public static Symbol calleeName(Statement stmt) {
        return varName(((CallStatement) stmt).call().callee());
    }

    public static <T, R extends Entity>
    void checkIds(List<R> names,
                  OrderlyMap<R, T> table) {
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
        return ((DerivedTypeDeclarer) td).derivedType().symbol();
    }

}
