package org.cossbow.feng.parser;

import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.attr.Attribute;
import org.cossbow.feng.ast.attr.AttributeDefinition;
import org.cossbow.feng.ast.expr.ArrayExpression;
import org.cossbow.feng.ast.expr.ObjectExpression;
import org.cossbow.feng.ast.oop.ClassDefinition;
import org.cossbow.feng.ast.oop.InterfaceDefinition;
import org.cossbow.feng.ast.proc.FunctionDefinition;
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

    private final List<Function<CharSequence, List<Attribute>>> atXxx = List.of(
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

    @Test
    public void testDefine() {
        var name = randTypeName(16);
        var code = "attribute %s {}".formatted(name);
        var def = (AttributeDefinition) doParseDefinition(code);
        Assertions.assertEquals(name, def.name().orElseThrow());
        Assertions.assertTrue(def.fields().isEmpty());
    }

    @Test
    public void testField1() {
        var name = randVarFuncName(12);
        var type = randTypeName(20);
        var code = "export attribute Server { %s %s; }".formatted(name, type);
        var def = (AttributeDefinition) doParseDefinition(code);
        Assertions.assertEquals(1, def.fields().size());
        var field = def.fields().getFirst();
        Assertions.assertEquals(name, field.name());
        Assertions.assertEquals(type, field.type());
        Assertions.assertFalse(field.array());
        Assertions.assertTrue(field.init().isEmpty());
    }

    @Test
    public void testField2() {
        var name = randVarFuncName(12);
        var type = randTypeName(20);
        var init = ThreadLocalRandom.current().nextInt(0, Integer.MAX_VALUE);
        var code = "export attribute Server { %s %s = %d; }".formatted(name, type, init);
        var def = (AttributeDefinition) doParseDefinition(code);
        Assertions.assertEquals(1, def.fields().size());
        var field = def.fields().getFirst();
        Assertions.assertEquals(name, field.name());
        Assertions.assertEquals(type, field.type());
        Assertions.assertFalse(field.array());
        var i = integer(field.init().orElseThrow());
        Assertions.assertEquals(BigInteger.valueOf(init), i.value());
    }

    @Test
    public void testArrayField1() {
        var fieldName = randVarFuncName(12);
        var fieldType = randTypeName(20);
        var code = "export attribute Server { %s []%s; }".formatted(fieldName, fieldType);
        var def = (AttributeDefinition) doParseDefinition(code);
        Assertions.assertEquals(1, def.fields().size());
        var field = def.fields().getFirst();
        Assertions.assertEquals(fieldName, field.name());
        Assertions.assertEquals(fieldType, field.type());
        Assertions.assertTrue(field.array());
        Assertions.assertTrue(field.init().isEmpty());
    }

    @Test
    public void testArrayField2() {
        var fieldName = randVarFuncName(12);
        var fieldType = randTypeName(20);
        var code = "export attribute Server { %s []%s = [10,20]; }".formatted(fieldName, fieldType);
        var def = (AttributeDefinition) doParseDefinition(code);
        Assertions.assertEquals(1, def.fields().size());
        var field = def.fields().getFirst();
        Assertions.assertEquals(fieldName, field.name());
        Assertions.assertEquals(fieldType, field.type());
        Assertions.assertTrue(field.array());
        var arr = (ArrayExpression) field.init().orElseThrow();
        Assertions.assertEquals(2, arr.elements().size());
    }

    private List<Attribute> atDefine(String code) {
        var def = doParseDefinition(code);
        return def.modifier().attributes();
    }

    private List<Attribute> atDefineClass(CharSequence attr) {
        return atDefine(attr + " class A{}");
    }

    private List<Attribute> atDefineInterface(CharSequence attr) {
        return atDefine(attr + " interface A{}");
    }

    private List<Attribute> atDefineEnum(CharSequence attr) {
        return atDefine(attr + " enum A{V,}");
    }

    private List<Attribute> atDefineStruct(CharSequence attr) {
        return atDefine(attr + " struct A{}");
    }

    private List<Attribute> atDefineUnion(CharSequence attr) {
        return atDefine(attr + " union A{}");
    }

    private List<Attribute> atDefineAttribute(CharSequence attr) {
        return atDefine(attr + " attribute A{}");
    }

    private List<Attribute> atDefineFunction(CharSequence attr) {
        return atDefine(attr + " func all(){}");
    }

    private List<Attribute> atDefinePrototype(CharSequence attr) {
        return atDefine(attr + " func all();");
    }

    private List<Attribute> atClassField(CharSequence attr) {
        var code = "class A{%s var id int;}".formatted(attr);
        var def = (ClassDefinition) doParseDefinition(code);
        return def.fields().getFirst().modifier().attributes();
    }

    private List<Attribute> atClassMethod(CharSequence attr) {
        var code = "class A{%s func get(){}}".formatted(attr);
        var def = (ClassDefinition) doParseDefinition(code);
        return def.methods().getFirst().modifier().attributes();
    }

    private List<Attribute> atInterfaceMethod(CharSequence attr) {
        var code = "interface A{%s get();}".formatted(attr);
        var def = (InterfaceDefinition) doParseDefinition(code);
        return def.methods().getFirst().modifier().attributes();
    }

    private List<Attribute> atParameter(CharSequence attr) {
        var code = "func test(%s a A){}".formatted(attr);
        var func = (FunctionDefinition) doParseDefinition(code);
        return func.procedure().prototype().parameters().getFirst()
                .variable().orElseThrow().modifier().attributes();
    }

    private List<Attribute> atDeclaration(CharSequence attr) {
        var code = attr + " var a A;";
        var stmt = (DeclarationStatement) doParseLocal(code);
        return stmt.variables().getFirst().modifier().attributes();
    }

    private List<Attribute> atTryCatch(CharSequence attr) {
        var code = "try{}catch(%s e Er){}".formatted(attr);
        var stmt = (TryStatement) doParseLocal(code);
        return stmt.catchClauses().getFirst().argument().modifier().attributes();
    }

    @Test
    public void testWithoutInit() {
        for (var xxx : atXxx) {
            for (int size = 1; size <= 8; size++) {
                var names = anyNames(RandTypeName, 8, size);
                var code = new StringBuilder();
                appendList(code, names, "@", "\n");
                var attrs = xxx.apply(code);
                Assertions.assertEquals(size, attrs.size());
                checkIds(names, attrs, Attribute::type);
            }
        }
    }

    @Test
    public void testWithSingleInit() {
        for (var xxx : atXxx) {
            for (int size = 1; size <= 8; size++) {
                var names = anyNames(RandTypeName, 8, size);
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
                checkIds(names, attrs, Attribute::type);
                for (int s = 0; s < size; s++) {
                    var init = inits.get(s);
                    var obj = (ObjectExpression) attrs.get(s).init().orElseThrow();
                    checkIds(init, obj.entries(), ObjectExpression.Entry::key);
                }
            }
        }
    }

    @Test
    public void testWithArrayInit() {
        for (var xxx : atXxx) {
            for (int size = 1; size <= 8; size++) {
                var names = anyNames(RandTypeName, 8, size);
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
                checkIds(names, attrs, Attribute::type);
                for (int s = 0; s < size; s++) {
                    var init = inits.get(s);
                    var obj = (ObjectExpression) attrs.get(s).init().orElseThrow();
                    checkIds(init, obj.entries(), ObjectExpression.Entry::key);
                }
            }
        }
    }

}
