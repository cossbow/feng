package org.cossbow.feng.coder;

import org.cossbow.feng.ast.mod.FModule;
import org.cossbow.feng.dag.DAGGraph;
import org.cossbow.feng.mod.ModuleAnalyseTest;
import org.cossbow.feng.mod.ModuleAnalysis;
import org.cossbow.feng.mod.ModuleParser;
import org.cossbow.feng.mod.ModuleParserTest;
import org.cossbow.feng.util.CommonUtil;
import org.cossbow.feng.util.ResourceUtil;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

import static java.nio.charset.StandardCharsets.UTF_8;

public class CppGeneratorTest {

    static Path replaceExt(String name, String ext) {
        var i = name.lastIndexOf('.');
        if (i < 0) return Path.of(name + '.' + ext);
        return Path.of(name.substring(0, i + 1) + ext);
    }

    static void compileCpp(boolean ld, Path dir, String name, Path... cpps) {
        assert cpps.length > 0;
        var cmd = new ArrayList<String>(5 + cpps.length);
        cmd.add("c++");
        cmd.add("--std=c++20");
        if (!ld) cmd.add("-c");
        for (var cpp : cpps) cmd.add(cpp.toString());
        cmd.add("-o");
        cmd.add(replaceExt(name, "o").toString());
        try {
            var p = new ProcessBuilder().directory(dir.toFile())
                    .command(cmd).start();
            Thread.startVirtualThread(() -> {
                try (var es = p.getInputStream()) {
                    es.readAllBytes();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
            try (var es = p.getErrorStream()) {
                var er = new String(es.readAllBytes());
                if (!er.isEmpty())
                    System.err.println(er);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            p.waitFor();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    public void testOne(Path dir, Path file) throws IOException {
        CppGenerator.copyBaseHeader(dir);
        var fn = file.getFileName();
        var name = CommonUtil.trimExt(fn.toString())
                .replace("-", "_");
        var subDir = dir.resolve(name);
        var dag = new ModuleParser(name, dir, UTF_8,
                ModuleParserTest.libs()).parseFile(fn);
        new ModuleAnalysis().analyse(dag);
        Files.createDirectories(subDir);
        generate(dag, subDir, true);
    }

    @Test
    public void testSampleSource() throws IOException {
        var dir = ResourceUtil.getDir("coder");
        for (var file : ResourceUtil.list(dir)) {
            testOne(dir, file);
        }
    }

    private Path genCpp(FModule fm, Path dir) {
        var ast = fm.result.must();
        var name = fm.path().filename();
        var cpp = Path.of(name + ".cpp");
        System.out.printf("gen: %s\n", cpp);
        ResourceUtil.write(dir.resolve(cpp), w -> {
            new CppGenerator(ast, w, true).write();
        });
        System.out.printf("gen: %s.h\n", name);
        ResourceUtil.write(dir.resolve(name + ".h"), w -> {
            new CppGenerator(ast, w, true, true).write();
        });
        Files.exists(dir.resolve(name + ".h"));
        System.out.printf("c++: %s.o\n", name);
        compileCpp(false, dir, name, cpp);
        return cpp;
    }

    @Test
    public void testSampleModule() {
        var dir = ModuleParserTest.getDir();
        var fm = ModuleAnalyseTest.analyseModule();
        generate(fm, dir, true);
    }

    @Test
    public void testMultiModule() throws IOException {
        var dir = ModuleParserTest.getDir();
        var dag = ModuleAnalyseTest.analysePackage();
        generate(dag, dir, false);
    }

    private void generate(DAGGraph<FModule> dag,
                          Path dir, boolean libs) {
        CppGenerator.copyBaseHeader(dir);
        var cpps = new ArrayList<Path>();
        for (var fm : dag) {
            var cpp = genCpp(fm, dir);
            cpps.add(cpp);
        }
        if (libs) return;
        System.out.println("c++ main");
        compileCpp(true, dir, ModuleParserTest.pkgName,
                cpps.toArray(Path[]::new));
    }

}
