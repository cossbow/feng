package org.cossbow.feng.parser;

import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.IdentifierTable;
import org.cossbow.feng.ast.attr.Attribute;
import org.cossbow.feng.ast.attr.AttributeDefinition;
import org.cossbow.feng.ast.expr.ArrayExpression;
import org.cossbow.feng.ast.expr.ObjectExpression;
import org.cossbow.feng.ast.oop.ClassDefinition;
import org.cossbow.feng.ast.oop.InterfaceDefinition;
import org.cossbow.feng.ast.proc.VariableParameterSet;
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

    private final List<Function<CharSequence, IdentifierTable<Attribute>>> atXxx = List.of(
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
        var name = randTypeSymbol(16);
        var code = "attribute %s {}".formatted(name);
        var def = (AttributeDefinition) doParseType(code, name);
        Assertions.assertEquals(name, def.symbol());
        Assertions.assertTrue(def.fields().isEmpty());
    }

    @Test
    public void testField1() {
        var name = randVarName(12);
        var type = randTypeName(20);
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
        var type = randTypeName(20);
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
        var fieldName = randVarName(12);
        var fieldType = randTypeName(20);
        var code = "export attribute Server { %s []%s; }".formatted(fieldName, fieldType);
        var def = (AttributeDefinition) doParseType(code, "Server");
        Assertions.assertEquals(1, def.fields().size());
        var field = def.fields().getValue(0);
        Assertions.assertEquals(fieldName, field.name());
        Assertions.assertEquals(fieldType, field.type());
        Assertions.assertTrue(field.array());
        Assertions.assertTrue(field.init().none());
    }

    @Test
    public void testArrayField2() {
        var fieldName = randVarName(12);
        var fieldType = randTypeName(20);
        var code = "export attribute Server { %s []%s = [10,20]; }".formatted(fieldName, fieldType);
        var def = (AttributeDefinition) doParseType(code, "Server");
        Assertions.assertEquals(1, def.fields().size());
        var field = def.fields().getValue(0);
        Assertions.assertEquals(fieldName, field.name());
        Assertions.assertEquals(fieldType, field.type());
        Assertions.assertTrue(field.array());
        var arr = (ArrayExpression) field.init().must();
        Assertions.assertEquals(2, arr.elements().size());
    }

    private IdentifierTable<Attribute> atDefine(String code) {
        var src = doParseFile(code);
        var def = firstDef(src);
        return def.modifier().attributes();
    }

    private IdentifierTable<Attribute> atDefineClass(CharSequence attr) {
        return atDefine(attr + " class A{}");
    }

    private IdentifierTable<Attribute> atDefineInterface(CharSequence attr) {
        return atDefine(attr + " interface A{}");
    }

    private IdentifierTable<Attribute> atDefineEnum(CharSequence attr) {
        return atDefine(attr + " enum A{V,}");
    }

    private IdentifierTable<Attribute> atDefineStruct(CharSequence attr) {
        return atDefine(attr + " struct A{}");
    }

    private IdentifierTable<Attribute> atDefineUnion(CharSequence attr) {
        return atDefine(attr + " union A{}");
    }

    private IdentifierTable<Attribute> atDefineAttribute(CharSequence attr) {
        return atDefine(attr + " attribute A{}");
    }

    private IdentifierTable<Attribute> atDefineFunction(CharSequence attr) {
        return atDefine(attr + " func all(){}");
    }

    private IdentifierTable<Attribute> atDefinePrototype(CharSequence attr) {
        return atDefine(attr + " func all();");
    }

    private IdentifierTable<Attribute> atClassField(CharSequence attr) {
        var code = "class A{%s var id int;}".formatted(attr);
        var def = (ClassDefinition) doParseType(code, "A");
        return def.fields().get(identifier("id")).modifier().attributes();
    }

    private IdentifierTable<Attribute> atClassMethod(CharSequence attr) {
        var code = "class A{%s func get(){}}".formatted(attr);
        var def = (ClassDefinition) doParseType(code, "A");
        return def.methods().get(identifier("get")).func().modifier().attributes();
    }

    private IdentifierTable<Attribute> atInterfaceMethod(CharSequence attr) {
        var code = "interface A{%s get();}".formatted(attr);
        var def = (InterfaceDefinition) doParseType(code, "A");
        return def.methods().get(identifier("get")).modifier().attributes();
    }

    private IdentifierTable<Attribute> atParameter(CharSequence attr) {
        var code = "func test(%s a A){}".formatted(attr);
        var func = doParseFunc(code, "test");
        var ps = (VariableParameterSet) func.procedure().prototype().parameterSet();
        return ps.variables().get(identifier("a")).modifier().attributes();
    }

    private IdentifierTable<Attribute> atDeclaration(CharSequence attr) {
        var code = attr + " var a A;";
        var stmt = (DeclarationStatement) doParseLocal(code);
        return stmt.variables().getFirst().modifier().attributes();
    }

    private IdentifierTable<Attribute> atTryCatch(CharSequence attr) {
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
                checkIds(names, attrs);
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
                checkIds(names, attrs);
                for (int s = 0; s < size; s++) {
                    var init = inits.get(s);
                    var obj = (ObjectExpression) attrs.getValue(s).init().must();
                    checkIds(init, obj.entries());
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
                checkIds(names, attrs);
                for (int s = 0; s < size; s++) {
                    var init = inits.get(s);
                    var obj = (ObjectExpression) attrs.getValue(s).init().must();
                    checkIds(init, obj.entries());
                }
            }
        }
    }

}
