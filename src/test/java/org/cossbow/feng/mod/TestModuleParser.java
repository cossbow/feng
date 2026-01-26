package org.cossbow.feng.mod;

import org.cossbow.feng.ast.FModule;
import org.cossbow.feng.parser.ModuleParser;
import org.cossbow.feng.util.ErrorUtil;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

public class TestModuleParser {
    static final Path base = Path.of(System.getProperty("user.dir"),
            "src", "test", "feng");

    FModule parse(Path mp) {
        try {
            return new ModuleParser(StandardCharsets.UTF_8).parse(base, mp);
        } catch (IOException e) {
            return ErrorUtil.io(e);
        }
    }

    @Test
    public void testSingle1() {
        var fm = parse(Path.of("mm"));
        System.out.println(fm.sources().size());
    }


}
