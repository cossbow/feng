package org.cossbow.feng.analysis;

import org.cossbow.feng.parser.BaseParseTest;
import org.cossbow.feng.visit.GlobalSymbolContext;
import org.junit.jupiter.api.Test;

public class ConstCheckerTest {

    void parseAndCheck(String code) {
        var src = BaseParseTest.doParseFile(code);
        var ctx = new GlobalSymbolContext(src.table());
        new SemanticAnalysis(ctx).visit(src);
    }

    @Test
    public void testVar1() {
        parseAndCheck("""
                func main() {
                    var a int;
                    a = 1;
                }
                """);
    }

}
