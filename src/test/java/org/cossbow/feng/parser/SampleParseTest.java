package org.cossbow.feng.parser;

import org.cossbow.feng.ast.Source;
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


    //

    static Source parseSample(String name) throws IOException {
        try (var is = getSample(name)) {
            Assertions.assertNotNull(is);
            return doParseFile(is, name);
        }
    }

    @Test
    public void testSamples() throws IOException {
        for (String name : list) {
            System.out.printf("Test sample: [%s].\n", name);
            var f = parseSample(name);
            Assertions.assertNotNull(f);
        }
    }

    //

}
