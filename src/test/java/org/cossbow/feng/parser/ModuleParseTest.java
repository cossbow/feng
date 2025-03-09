package org.cossbow.feng.parser;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

public class ModuleParseTest {
    @Test
    public void testModuleParse() throws IOException {
        var mp = new ModuleParser();
        var mod = mp.parse(List.of(), List.of());
        System.out.println(mod.path());
        System.out.println(mod.sources());
    }

}
