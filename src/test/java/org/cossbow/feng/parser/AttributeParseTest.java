package org.cossbow.feng.parser;

import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.SymbolMap;
import org.cossbow.feng.ast.attr.Attribute;
import org.cossbow.feng.ast.attr.AttributeDefinition;
import org.cossbow.feng.ast.attr.AttributeField;
import org.cossbow.feng.ast.expr.ArrayExpression;
import org.cossbow.feng.ast.oop.ClassDefinition;
import org.cossbow.feng.ast.oop.InterfaceDefinition;
import org.cossbow.feng.ast.stmt.DeclarationStatement;
import org.cossbow.feng.ast.stmt.TryStatement;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;

public class AttributeParseTest extends BaseParseTest {

    private final List<Function<CharSequence, SymbolMap<Attribute>>> atXxx = List.of(
            this::atDefineClass,
            this::atDefineInterface,
            this::atDefineEnum,
            this::atDefineStruct,
            this::atDefineUnion,
            this::atDefineAttribute,
            this::atDefineFunction,
            this::atDefinePrototype,

            this::atClassField,
            this::atClassMethod,
            this::atInterfaceMethod,

            this::atParameter,
            this::atDeclaration,
            this::atTryCatch
    );

    private AttributeField.Type randType() {
        var types = AttributeField.Type.values();
        var i = ThreadLocalRandom.current().nextInt(0, types.length);
        return types[i];
    }

    @Test
    public void testDefine() {
        var name = randTypeSymbol(16);
        var code = "attribute %s {}".formatted(name);
        var def = (AttributeDefinition) doParseType(code, name);
        Assertions.assertEquals(name, def.symbol());
        Assertions.assertTrue(def.fields().isEmpty());
    }

    @Test
    public void testField1() {
        var name = randVarName(12);
        var type = randType();
        var code = "export attribute Server { %s %s; }".formatted(name, type);
        var def = (AttributeDefinition) doParseType(code, "Server");
        Assertions.assertEquals(1, def.fields().size());
        var field = def.fields().getValue(0);
        Assertions.assertEquals(name, field.name());
        Assertions.assertEquals(type, field.type());
        Assertions.assertFalse(field.array());
        Assertions.assertTrue(field.init().none());
    }

    @Test
    public void testField2() {
        var name = randVarName(12);
        var type = randType();
        var init = ThreadLocalRandom.current().nextInt(0, Integer.MAX_VALUE);
        var code = "export attribute Server { %s %s = %d; }".formatted(name, type, init);
        var def = (AttributeDefinition) doParseType(code, "Server");
        Assertions.assertEquals(1, def.fields().size());
        var field = def.fields().getValue(0);
        Assertions.assertEquals(name, field.name());
        Assertions.assertEquals(type, field.type());
        Assertions.assertFalse(field.array());
        var i = integer(field.init().must());
        Assertions.assertEquals(BigInteger.valueOf(init), i.value());
    }

    @Test
    public void testArrayField1() {
        var name = randVarName(12);
        var type = randType();
        var code = "export attribute Server { %s []%s; }".formatted(name, type);
        var def = (AttributeDefinition) doParseType(code, "Server");
        Assertions.assertEquals(1, def.fields().size());
        var field = def.fields().getValue(0);
        Assertions.assertEquals(name, field.name());
        Assertions.assertEquals(type, field.type());
        Assertions.assertTrue(field.array());
        Assertions.assertTrue(field.init().none());
    }

    @Test
    public void testArrayField2() {
        var name = randVarName(12);
        var type = randType();
        var code = "export attribute Server { %s []%s = [10,20]; }".formatted(name, type);
        var def = (AttributeDefinition) doParseType(code, "Server");
        Assertions.assertEquals(1, def.fields().size());
        var field = def.fields().getValue(0);
        Assertions.assertEquals(name, field.name());
        Assertions.assertEquals(type, field.type());
        Assertions.assertTrue(field.array());
        var arr = (ArrayExpression) field.init().must();
        Assertions.assertEquals(2, arr.size());
    }

    private SymbolMap<Attribute> atDefine(String code) {
        var src = doParseFile(code);
        var def = firstDef(src);
        return def.modifier().attributes();
    }

    private SymbolMap<Attribute> atDefineClass(CharSequence attr) {
        return atDefine(attr + " class A{}");
    }

    private SymbolMap<Attribute> atDefineInterface(CharSequence attr) {
        return atDefine(attr + " interface A{}");
    }

    private SymbolMap<Attribute> atDefineEnum(CharSequence attr) {
        return atDefine(attr + " enum A{V,}");
    }

    private SymbolMap<Attribute> atDefineStruct(CharSequence attr) {
        return atDefine(attr + " struct A{}");
    }

    private SymbolMap<Attribute> atDefineUnion(CharSequence attr) {
        return atDefine(attr + " union A{}");
    }

    private SymbolMap<Attribute> atDefineAttribute(CharSequence attr) {
        return atDefine(attr + " attribute A{}");
    }

    private SymbolMap<Attribute> atDefineFunction(CharSequence attr) {
        return atDefine(attr + " func all(){}");
    }

    private SymbolMap<Attribute> atDefinePrototype(CharSequence attr) {
        return atDefine(attr + " func all=();");
    }

    private SymbolMap<Attribute> atClassField(CharSequence attr) {
        var code = "class A{%s var id int;}".formatted(attr);
        var def = (ClassDefinition) doParseType(code, "A");
        return def.fields().get(identifier("id")).modifier().attributes();
    }

    private SymbolMap<Attribute> atClassMethod(CharSequence attr) {
        var code = "class A{%s func get(){}}".formatted(attr);
        var def = (ClassDefinition) doParseType(code, "A");
        return def.methods().get(identifier("get")).modifier().attributes();
    }

    private SymbolMap<Attribute> atInterfaceMethod(CharSequence attr) {
        var code = "interface A{%s get();}".formatted(attr);
        var def = (InterfaceDefinition) doParseType(code, "A");
        return def.methods().get(identifier("get")).modifier().attributes();
    }

    private SymbolMap<Attribute> atParameter(CharSequence attr) {
        var code = "func test(%s a A){}".formatted(attr);
        var func = doParseFunc(code, "test");
        var ps = func.procedure().prototype().parameterSet();
        return ps.variables().get(identifier("a")).modifier().attributes();
    }

    private SymbolMap<Attribute> atDeclaration(CharSequence attr) {
        var code = attr + " var a A;";
        var stmt = (DeclarationStatement) doParseLocal(code);
        return stmt.variables().getFirst().modifier().attributes();
    }

    private SymbolMap<Attribute> atTryCatch(CharSequence attr) {
        var code = "try{}catch(%s e Er){}".formatted(attr);
        var stmt = (TryStatement) doParseLocal(code);
        return stmt.catchClauses().getFirst().argument().modifier().attributes();
    }

    @Test
    public void testWithoutInit() {
        for (var xxx : atXxx) {
            for (int size = 1; size <= 8; size++) {
                var names = anyNames(RandTypeSymbol, 8, size);
                var code = new StringBuilder();
                appendList(code, names, "@", "\n");
                var attrs = xxx.apply(code);
                Assertions.assertEquals(size, attrs.size());
                checkIds(names, attrs);
            }
        }
    }

    @Test
    public void testWithSingleInit() {
        for (var xxx : atXxx) {
            for (int size = 1; size <= 8; size++) {
                var names = anyNames(RandTypeSymbol, 8, size);
                var inits = new ArrayList<List<Identifier>>();
                var code = new StringBuilder();
                for (int s = 1; s <= size; s++) {
                    var init = anyNames(RandVarFuncName, 8, s);
                    inits.add(init);
                    code.append("@").append(names.get(s - 1)).append("({");
                    appendList(code, init, "", "=1,");
                    code.setLength(code.length() - 1);
                    code.append("})");
                }
                var attrs = xxx.apply(code);
                Assertions.assertEquals(size, attrs.size());
                checkIds(names, attrs);
                for (int s = 0; s < size; s++) {
                    var init = inits.get(s);
                    var obj = attrs.getValue(s).init().must();
                    checkIds(init, obj.entries());
                }
            }
        }
    }

    @Test
    public void testWithArrayInit() {
        for (var xxx : atXxx) {
            for (int size = 1; size <= 8; size++) {
                var names = anyNames(RandTypeSymbol, 8, size);
                var inits = new ArrayList<List<Identifier>>();
                var code = new StringBuilder();
                for (int s = 1; s <= size; s++) {
                    var init = anyNames(RandVarFuncName, 8, s);
                    inits.add(init);
                    code.append("@").append(names.get(s - 1)).append("({");
                    appendList(code, init, "", "=[1,2],");
                    code.setLength(code.length() - 1);
                    code.append("})");
                }
                var attrs = xxx.apply(code);
                Assertions.assertEquals(size, attrs.size());
                checkIds(names, attrs);
                for (int s = 0; s < size; s++) {
                    var init = inits.get(s);
                    var obj = attrs.getValue(s).init().must();
                    checkIds(init, obj.entries());
                }
            }
        }
    }

}
