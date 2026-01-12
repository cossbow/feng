package org.cossbow.feng.analysis;

import org.cossbow.feng.err.SemanticException;
import org.cossbow.feng.parser.BaseParseTest;
import org.cossbow.feng.visit.GlobalSymbolContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class SemanticAnalysisTest {

    void parseAndCheck(String code) {
        var src = BaseParseTest.doParseFile(code);
        var ctx = new GlobalSymbolContext(src.table());
        new SemanticAnalysis(ctx).visit(src);
    }

    @Test
    public void testClass1() {
        parseAndCheck("class A {}");
    }

    @Test
    public void testClass2() {
        parseAndCheck("class A { var id int; }");
    }

    @Test
    public void testClass3() {
        try {
            parseAndCheck("class A { var id ID; }");
        } catch (SemanticException e) {
            System.err.println(e.getMessage());
        }
    }

    @Test
    public void testClass4() {
        parseAndCheck("class ID{} class A { var id ID; }");
    }

    @Test
    public void testClass5() {
        parseAndCheck("class ID{} class A { var id ID; func get() ID { return id; } }");
    }

    @Test
    public void testClass6() {
        try {
            parseAndCheck("class ID{} class A { var id int; func get() ID { return id; } }");
        } catch (SemanticException e) {
            System.err.println(e.getMessage());
        }
    }

    @Test
    public void testClass7() {
        parseAndCheck("class ID{} class A { var id ID; func set(id ID)  { this.id = id; } }");
    }

    @Test
    public void testClass8() {
        try {
            parseAndCheck("class ID{} class A { var id int; func set(id ID)  { this.id = id; } }");
        } catch (SemanticException e) {
            System.err.println(e.getMessage());
        }
    }


    @Test
    public void testVar1() {
        parseAndCheck("func f(v int) { var i int; i = int(v); }");
    }

    @Test
    public void testVar2() {
        try {
            parseAndCheck("func f(v float) { var i int; i = v; }");
        } catch (SemanticException e) {
            System.err.println(e.getMessage());
        }
    }

    @Test
    public void testVar3() {
        parseAndCheck("func f(v float) { var i int; i = int(v); }");
    }

    @Test
    public void testVar4() {
        try {
            parseAndCheck("func f(v bool) { var i int; i = int(v); }");
        } catch (SemanticException e) {
            System.err.println(e.getMessage());
        }
    }

    @Test
    public void testVar5() {
        try {
            parseAndCheck("class ID{} func f(v ID) { var i int; i = int(v); }");
        } catch (SemanticException e) {
            System.err.println(e.getMessage());
        }
    }

    @Test
    public void testVar6() {
        try {
            parseAndCheck("class ID{} func f(v int) { var i ID; i = ID(v); }");
        } catch (SemanticException e) {
            System.err.println(e.getMessage());
        }
    }

}
