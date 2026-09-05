package org.cossbow.feng.mod;

import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.mod.ModulePath;
import org.cossbow.feng.util.ErrorUtil;
import org.cossbow.feng.util.ResourceUtil;
import org.cossbow.feng.util.TargetOS;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the {@code feng =} whitelist in {@code feng.cfg}:
 * when a whitelist is configured (common and/or os-specific section), only
 * the listed .feng files of the module directory are compiled; platforms
 * without any whitelist keep all files.
 */
public class FengCfgWhitelistTest {

    private static Path dir(String sub) {
        return ResourceUtil.getDir("fengcfg/" + sub);
    }

    private static ModuleManager parse(String module, TargetOS os) throws Exception {
        var parser = new ModuleParser("fengcfg",
                ResourceUtil.getDir("fengcfg"), UTF_8,
                Map.of(), false, os);
        return parser.parseModule(Path.of(module));
    }

    private static boolean hasFunc(ModuleManager m, String module, String func) {
        for (var fm : m.dag()) {
            if (fm.path().equals(new ModulePath(
                    new Identifier("fengcfg"), Path.of(module)))) {
                var f = fm.table().findFunc(new Identifier(func));
                if (f.has()) return true;
            }
        }
        return false;
    }

    @Test
    public void testConfigParsing() throws Exception {
        var cfg = ModuleConfig.load(dir("plat"));
        // common section + [linux] section merged for linux
        assertEquals(List.of("a.feng", "b.feng"), cfg.fengFiles(TargetOS.LINUX));
        // common section only for windows
        assertEquals(List.of("a.feng"), cfg.fengFiles(TargetOS.WINDOWS));
    }

    @Test
    public void testLinuxOnlyCompilesWhitelist() throws Exception {
        var m = parse("plat", TargetOS.LINUX);
        assertTrue(hasFunc(m, "plat", "onlyA"), "common a.feng should be compiled");
        assertTrue(hasFunc(m, "plat", "onlyB"), "linux b.feng should be compiled");
        assertFalse(hasFunc(m, "plat", "onlyC"), "non-whitelisted c.feng should be ignored");
    }

    @Test
    public void testCommonWhitelistAppliesToAllOs() throws Exception {
        var m = parse("plat", TargetOS.WINDOWS);
        assertTrue(hasFunc(m, "plat", "onlyA"), "common a.feng should be compiled");
        assertFalse(hasFunc(m, "plat", "onlyB"), "linux-only b.feng should be ignored");
        assertFalse(hasFunc(m, "plat", "onlyC"));
    }

    @Test
    public void testNoConfigKeepsAllFiles() throws Exception {
        var ml = parse("noconfig", TargetOS.LINUX);
        var mw = parse("noconfig", TargetOS.WINDOWS);
        assertTrue(hasFunc(ml, "noconfig", "ncA"));
        assertTrue(hasFunc(ml, "noconfig", "ncB"));
        assertTrue(hasFunc(mw, "noconfig", "ncA"));
        assertTrue(hasFunc(mw, "noconfig", "ncB"));
    }

    @Test
    public void testMissingWhitelistFileFails() {
        assertThrows(ErrorUtil.ModuleException.class,
                () -> parse("bad", TargetOS.LINUX));
    }
}
