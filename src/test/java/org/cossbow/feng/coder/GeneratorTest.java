package org.cossbow.feng.coder;

import org.cossbow.feng.Compiler;
import org.cossbow.feng.mod.ModuleParserTest;
import org.cossbow.feng.util.Command;
import org.cossbow.feng.util.CommonUtil;
import org.cossbow.feng.util.ErrorUtil;
import org.cossbow.feng.util.ResourceUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;

/**
 * Tests for code generation using {@link Compiler} as the compilation engine.
 */
abstract
public class GeneratorTest {

    private static final String PkgName =
            ModuleParserTest.pkgName;

    abstract
    protected Compiler compiler(String pkg);

    // ---- single-file tests ----

    private void exec(Path subDir) {
        var os = System.getProperty("os.name");
        var name = PkgName;
        if ("win".equals(os)) {
            name += ".exe";
        }
        var r = new Command(subDir, name).exec();
        if (r.code() != 0) {
            ErrorUtil.backend("exec error(%d): %s", r.code(), r.err());
        }
        var err = r.out().lines().filter(s -> s.startsWith("ref="))
                .toList();
        if (err.isEmpty()) return;
        ErrorUtil.backend("exec has memory bug: %s", err);
    }

    @Test
    public void testSampleSource(@TempDir Path tempDir) throws IOException {
        var dir = ResourceUtil.getDir("coder");
        for (var file : ResourceUtil.list(dir)) {
            var fn = file.getFileName().toString();
            System.out.println("=== compile " + fn + " ===");
            var name = CommonUtil.trimExt(fn);
            name = name.replace("-", "_");
            var subDir = tempDir.resolve(name);
            Files.createDirectories(subDir);
            compiler(PkgName).compileFile(file, subDir);
//            exec(subDir);
        }
    }

    // ---- module tests ----

    @Test
    public void testSampleModule(@TempDir Path tempDir) throws IOException {
        var dir = ModuleParserTest.getDir();
        var c = compiler(PkgName);
        var subDir = tempDir.resolve("out");
        Files.createDirectories(subDir);
        c.compileModule(dir.resolve("aaa"), subDir);
    }

    @Test
    public void testMultiModule(@TempDir Path tempDir) throws IOException {
        var dir = ModuleParserTest.getDir();
        var c = compiler(PkgName);
        var subDir = tempDir.resolve("out");
        Files.createDirectories(subDir);
        c.compilePackage(dir, subDir);
    }

    // ---- mixed module test (Feng + pure-C) ----

    @Test
    public void testMixedModule(@TempDir Path tempDir) throws IOException {
        var dir = ResourceUtil.getDir("mixed");
        var c = compiler("mixed");
        var subDir = tempDir.resolve("out");
        Files.createDirectories(subDir);
        c.compilePackage(dir, subDir);
    }
}
