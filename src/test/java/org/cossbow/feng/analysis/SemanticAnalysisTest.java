package org.cossbow.feng.analysis;

import org.cossbow.feng.err.SemanticException;
import org.cossbow.feng.parser.BaseParseTest;
import org.cossbow.feng.visit.GlobalSymbolContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class SemanticAnalysisTest {

    void checkTrue(String code) {
        var src = BaseParseTest.doParseFile(code);
        var ctx = new GlobalSymbolContext(src.table());
        new SemanticAnalysis(ctx).visit(src);
    }

    void checkFalse(String code) {
        try {
            checkTrue(code);
            Assertions.fail("Failed to check for errors");
        } catch (SemanticException e) {
            System.out.println("checked error: " + e.getMessage());
        }
    }

    @Test
    public void testClass0() {
        checkTrue("class A {}");
    }

    @Test
    public void testClassInherit1() {
        checkTrue("class B {} class A : B {}");
    }

    @Test
    public void testClassInherit2() {
        checkFalse("class A : B {}");
    }

    @Test
    public void testClassInherit3() {
        checkTrue("struct B {} class A : B {}");
    }

    @Test
    public void testClassImpl1() {
        checkTrue("class I {} class A (I) {}");
    }

    @Test
    public void testClassImpl2() {
        checkFalse("class A (I) {}");
    }

    @Test
    public void testClassImpl3() {
        checkTrue("struct I {} class A (I) {}");
    }


    @Test
    public void testClassField1() {
        checkTrue("class A { var id int; }");
    }

    @Test
    public void testClassField2() {
        checkFalse("class A { var id ID; }");
    }

    @Test
    public void testClassField3() {
        checkTrue("class ID{} class A { var id ID; }");
    }

    @Test
    public void testClassField4() {
        checkTrue("class ID{} class A { var id ID; func get() ID { return id; } }");
    }

    @Test
    public void testClassField5() {
        checkFalse("class ID{} class A { var id int; func get() ID { return id; } }");
    }

    @Test
    public void testClassField6() {
        checkTrue("class ID{} class A { var id ID; func set(id ID)  { this.id = id; } }");
    }

    @Test
    public void testClassField7() {
        checkFalse("class ID{} class A { var id int; func set(id ID)  { this.id = id; } }");
    }

    @Test
    public void testClassField8() {
        checkTrue("var id int = 10; class A { var id int; func get() int { return id; } }");
    }

    @Test
    public void testClassMethod1() {
        checkTrue("class A { func at() {} func test() { at(); } }");
    }

    @Test
    public void testClassMethod2() {
        checkFalse("class A { func test() { at(); } }");
    }

    @Test
    public void testClassMethod3() {
        checkTrue("func at() {} \n class A { func at() {} \n func test() { at(); } }");
    }

    @Test
    public void testClassMethod4() {
        checkTrue("func at() {} \n class A { func at() {} \n func test() { this.at(); } }");
    }

    //

    @Test
    public void testVar1() {
        checkTrue("func f(v int) { var i int; i = int(v); }");
    }

    @Test
    public void testVar2() {
        checkFalse("func f(v float) { var i int; i = v; }");
    }

    @Test
    public void testVar3() {
        checkTrue("func f(v float) { var i int; i = int(v); }");
    }

    @Test
    public void testVar4() {
        checkFalse("func f(v bool) { var i int; i = int(v); }");
    }

    @Test
    public void testVar5() {
        checkFalse("class ID{} func f(v ID) { var i int; i = int(v); }");
    }

    @Test
    public void testVar6() {
        checkFalse("class ID{} func f(v int) { var i ID; i = ID(v); }");
    }

}
