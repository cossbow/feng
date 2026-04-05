package org.cossbow.feng.mod;

import org.cossbow.feng.util.ResourceUtil;
import org.junit.jupiter.api.Test;

import static java.nio.charset.StandardCharsets.UTF_8;

public class ModuleParserTest {

    @Test
    public void parseSample() {
        var base = ResourceUtil.getDir("mod");
        var dag = new ModuleParser(base, UTF_8)
                .scanAndParse();
        for (var fm : dag) {
            System.out.println(fm);
        }
    }

}
