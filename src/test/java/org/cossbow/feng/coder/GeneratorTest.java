package org.cossbow.feng.coder;

import org.cossbow.feng.Compiler;
import org.cossbow.feng.mod.ModuleParserTest;
import org.cossbow.feng.util.CommonUtil;
import org.cossbow.feng.util.ResourceUtil;
import org.junit.jupiter.api.Assertions;
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

    private void exec(Path dir, String name) {
        // find the actual executable (with or without .exe on Windows)
        var exe = dir.resolve(name);
        if (!Files.exists(exe)) {
            var exeWin = dir.resolve(name + ".exe");
            if (Files.exists(exeWin)) {
                exe = exeWin;
            } else {
                // library-only compilation (no main), skip execution
                return;
            }
        }
        try {
            var pb = new ProcessBuilder(exe.toString())
                    .directory(dir.toFile())
                    .redirectErrorStream(true);
            var p = pb.start();
            var out = new String(p.getInputStream().readAllBytes());
            int code = p.waitFor();
            if (code != 0) {
                Assertions.fail("execution failed (exit " + code + ")\n" + out);
            }
        } catch (IOException | InterruptedException e) {
            Assertions.fail("execution error: " + e.getMessage());
        }
    }

    // ---- single-file tests ----

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
            exec(subDir, PkgName);
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
        exec(subDir, PkgName);
    }

    @Test
    public void testMultiModule(@TempDir Path tempDir) throws IOException {
        var dir = ModuleParserTest.getDir();
        var c = compiler(PkgName);
        var subDir = tempDir.resolve("out");
        Files.createDirectories(subDir);
        c.compilePackage(dir, subDir);
        exec(subDir, PkgName);
    }

    // ---- mixed module test (Feng + pure-C) ----

    @Test
    public void testMixedModule(@TempDir Path tempDir) throws IOException {
        var dir = ResourceUtil.getDir("mixed");
        var c = compiler("mixed");
        var subDir = tempDir.resolve("out");
        Files.createDirectories(subDir);
        c.compilePackage(dir, subDir);
        exec(subDir, "mixed");
    }
}
