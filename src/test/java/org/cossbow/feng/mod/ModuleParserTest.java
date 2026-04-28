package org.cossbow.feng.mod;

import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.mod.FModule;
import org.cossbow.feng.ast.mod.ModulePath;
import org.cossbow.feng.dag.DAGGraph;
import org.cossbow.feng.util.ErrorUtil;
import org.cossbow.feng.util.ResourceUtil;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;

public class ModuleParserTest {

    static final ModuleParser std;

    static {
        std = new ModuleParser("std", ResourceUtil.getDir("std"), UTF_8);
    }

    public static Map<Identifier, ModuleParser> libs() {
        return Map.of(std.pkg(), std);
    }

    public static final String pkgName = "test";

    public static Path getDir() {
        return ResourceUtil.getDir("mod");
    }

    static ModuleParser testMod() {
        return new ModuleParser(pkgName, getDir(), UTF_8, libs());
    }

    public static boolean isTestPkg(ModulePath mp) {
        return pkgName.equals(mp.pkg().value());
    }

    public static DAGGraph<FModule> parseModule() {
        try {
            return testMod().parseModule(Path.of("aaa"));
        } catch (IOException e) {
            return ErrorUtil.io(e);
        }
    }

    @Test
    public void testParseModule() {
        var fm = parseModule();
        System.out.println(fm);
    }

    public static DAGGraph<FModule> parsePackage() throws IOException {
        return testMod().parsePackage();
    }

    @Test
    public void testParsePackage() throws IOException {
        for (var fm : parsePackage()) {
            System.out.println(fm.path());
        }
    }

    public static DAGGraph<FModule> withLibrary() throws IOException {
        var test = testMod();
        var lib = new ModuleParser("lib",
                ResourceUtil.getDir("lib"),
                UTF_8, Map.of(test.pkg(), test));
        return lib.parsePackage();
    }

    @Test
    public void testLibrary() throws IOException {
        var dag = withLibrary();
        for (var fm : dag) {
            System.out.println(fm);
        }
    }

}
