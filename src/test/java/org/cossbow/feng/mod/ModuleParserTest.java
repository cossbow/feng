package org.cossbow.feng.mod;

import org.cossbow.feng.ast.mod.FModule;
import org.cossbow.feng.dag.DAGGraph;
import org.cossbow.feng.util.ResourceUtil;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static java.nio.charset.StandardCharsets.UTF_8;

public class ModuleParserTest {
    public static final String pkgName = "test";

    public static Path getDir() {
        return ResourceUtil.getDir("mod");
    }

    public static FModule parseModule() {
        var dir = ModuleParserTest.getDir();
        return new ModuleParser(pkgName, dir, UTF_8)
                .parseModule(Path.of("aaa"));
    }

    @Test
    public void testParseModule() {
        var fm = parseModule();
        System.out.println(fm);
    }

    public static DAGGraph<FModule> parseProject() {
        return new ModuleParser(pkgName, getDir(), UTF_8)
                .scanAndParse();
    }

    @Test
    public void testParseProject() {
        for (var fm : parseProject()) {
            System.out.println(fm.path());
        }
    }

}
