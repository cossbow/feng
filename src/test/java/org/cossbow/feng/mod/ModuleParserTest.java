package org.cossbow.feng.mod;

import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.mod.ModulePath;
import org.cossbow.feng.util.ErrorUtil;
import org.cossbow.feng.util.ResourceUtil;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;

public class ModuleParserTest {

    public static Map<Identifier, ModuleParser> libs() {
        var std = new ModuleParser("std",
                Path.of("std"), UTF_8);
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

    public static ModuleManager parseModule() {
        try {
            return testMod().parseModule(Path.of("aaa"));
        } catch (IOException e) {
            return ErrorUtil.io(e);
        }
    }

    @Test
    public void testParseModule() {
        var m = parseModule();
        System.out.println(m.dag());
    }

    public static ModuleManager parsePackage() throws IOException {
        return testMod().parsePackage();
    }

    @Test
    public void testParsePackage() throws IOException {
        for (var fm : parsePackage().dag()) {
            System.out.println(fm.path());
        }
    }

    public static ModuleManager withLibrary() throws IOException {
        var test = testMod();
        var lib = new ModuleParser("lib",
                ResourceUtil.getDir("lib"),
                UTF_8, Map.of(test.pkg(), test));
        return lib.parsePackage();
    }

    @Test
    public void testLibrary() throws IOException {
        var m = withLibrary();
        for (var fm : m.dag()) {
            System.out.println(fm);
        }
    }

}
