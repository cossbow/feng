package org.cossbow.feng.coder;

import org.cossbow.feng.analysis.SemanticAnalysis;
import org.cossbow.feng.ast.mod.FModule;
import org.cossbow.feng.mod.ModuleAnalysis;
import org.cossbow.feng.mod.ModuleParser;
import org.cossbow.feng.parser.SourceParser;
import org.cossbow.feng.util.ResourceUtil;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.UTF_8;

public class CppGeneratorTest {

    static Path replaceExt(Path file, String ext) {
        var name = file.toString();
        var i = name.lastIndexOf('.');
        if (i < 0) return Path.of(name + '.' + ext);
        return Path.of(name.substring(0, i + 1) + ext);
    }

    static Path compileFile(Path file) {
        var dir = file.getParent();
        var src = new SourceParser(UTF_8).parse(file);
        var ast = new SemanticAnalysis(src.table(), false)
                .analyse();
        var name = file.getFileName();
        var cpp = replaceExt(name, "cpp");
        System.out.printf("[compile]%s%s{%s -> %s}\n",
                dir, File.separator, name, cpp);
        ResourceUtil.write(dir.resolve(cpp), w -> {
            new CppGenerator(ast, w, true).write();
        });
        CppGenerator.copyBaseHeader(dir);
        return cpp;
    }

    static void compileCpp(boolean ld, Path dir, Path name, Path... cpps) {
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
            try (var es = p.getInputStream()) {
                var msg = new String(es.readAllBytes());
                if (msg.isEmpty()) return;
                System.out.println(msg);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            try (var es = p.getErrorStream()) {
                var er = new String(es.readAllBytes());
                if (er.isEmpty()) return;
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

    @Test
    public void testSampleSource() {
        var dir = ResourceUtil.getDir("coder");
        ResourceUtil.go(ResourceUtil.list(dir), file -> {
            var cpp = compileFile(file);
            compileCpp(false, dir, cpp, cpp);
        });
    }

    @Test
    public void testSampleModule() {
        var base = ResourceUtil.getDir("mod");
        var dir = base.resolve("aaa");
        var fm = new ModuleParser(base, UTF_8).parseModule(dir);
        var ast = new ModuleAnalysis().analyse(fm);
        var cpp = Path.of("aaa.cpp");
        ResourceUtil.write(dir.resolve(cpp), w -> {
            new CppGenerator(ast, w, true).write();
        });
        ResourceUtil.write(dir.resolve("aaa.h"), w -> {
            new CppGenerator(ast, w, true, true).write();
        });
        CppGenerator.copyBaseHeader(dir);
        compileCpp(false, dir, Path.of("aaa"), cpp);
    }

    @Test
    public void testMultiModule() {
        var dir = ResourceUtil.getDir("mod");
        var dag = new ModuleParser(dir, UTF_8)
                .scanAndParse();
        var map = dag.stream().collect(Collectors.toMap(
                FModule::path, Function.identity()));
        new ModuleAnalysis().analyse(dag);
        var cpps = new ArrayList<Path>();
        for (var fm : dag) {
            var name = fm.path().filename();
            var cpp = Path.of(name + ".cpp");
            System.out.printf("gen: %s.cpp\n", name);
            ResourceUtil.write(dir.resolve(cpp), w -> {
                new CppGenerator(fm.result.must(), w, true).write();
            });
            System.out.printf("gen: %s.h\n", name);
            ResourceUtil.write(dir.resolve(name + ".h"), w -> {
                new CppGenerator(fm.result.must(), w, true, false).write();
            });
            System.out.printf("c++: %s.o\n", name);
            compileCpp(false, dir, Path.of(name), cpp);
            cpps.add(cpp);
        }
        CppGenerator.copyBaseHeader(dir);
        System.out.println("c++ main");
        compileCpp(true, dir, Path.of("mod"), cpps.toArray(Path[]::new));
    }

}
