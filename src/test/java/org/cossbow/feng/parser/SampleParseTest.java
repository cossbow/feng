package org.cossbow.feng.parser;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public class SampleParseTest extends BaseParseTest {

    static final List<String> list = List.of(
            "hello",
            "literal",
            "attribute",
            "class",
            "statement",
            "declaration",
            "enumeration",
            "expression",
            "procedure",
            "generic",
            "imports",
            "interface",
            "structure"
    );

    static InputStream getSample(String name) {
        return SampleParseTest.class.getResourceAsStream("/samples/" + name + ".feng");
    }

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

    //

    static FileParser parseSample(String name) throws IOException {

        try (var is = getSample(name)) {
            Assertions.assertNotNull(is);
            return doParse(CharStreams.fromStream(is));
        }
    }

    @Test
    public void testSamples() throws IOException {
        for (String name : list) {
            System.out.printf("Test sample: [%s].\n", name);
            var p = parseSample(name);
            Assertions.assertTrue(p.errors().isEmpty(), name + " error");
        }
    }

    //

}
