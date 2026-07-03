package org.cossbow.feng.coder;

import org.cossbow.feng.ast.TypeDomain;
import org.cossbow.feng.ast.mod.FModule;
import org.cossbow.feng.ast.proc.FunctionDefinition;
import org.cossbow.feng.ast.proc.FixedParameter;
import org.cossbow.feng.dag.DAGGraph;
import org.cossbow.feng.mod.ModuleAnalyseTest;
import org.cossbow.feng.mod.ModuleAnalysis;
import org.cossbow.feng.mod.ModuleParser;
import org.cossbow.feng.mod.ModuleParserTest;
import org.cossbow.feng.util.CommonUtil;
import org.cossbow.feng.util.ResourceUtil;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.BufferedWriter;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;

public class CppGeneratorTest {

    static Path replaceExt(String name, String ext) {
        var i = name.lastIndexOf('.');
        if (i < 0) return Path.of(name + '.' + ext);
        return Path.of(name.substring(0, i + 1) + ext);
    }

    static void compileC(boolean ld, Path dir, String name, Path... srcs) {
        assert srcs.length > 0;
        var cmd = new ArrayList<String>(5 + srcs.length);
        cmd.add("cc");
        if (!ld) cmd.add("-c");
        for (var s : srcs) cmd.add(s.toString());
        cmd.add("-o");
        cmd.add(replaceExt(name, "o").toString());
        runCmd(dir, cmd);
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
        runCmd(dir, cmd);
    }

    static void runCmd(Path dir, List<String> cmd) {
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
        System.out.printf("parse: %s\n", fn);
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

    private void genBridgeHeader(FModule fm, Path dir) throws IOException {
        var name = fm.path().filename();
        var h = dir.resolve(name + ".h");
        try (var w = Files.newBufferedWriter(h)) {
            w.write("// auto-generated bridge for pure C module: "
                    + name + "\n");
            w.write("#ifdef __cplusplus\n");
            w.write("extern \"C\" {\n");
            w.write("#endif\n");
            for (var hf : fm.headerFiles()) {
                var rel = dir.relativize(hf).toString().replace('\\', '/');
                w.write("#include \"" + rel + "\"\n");
            }
            w.write("#ifdef __cplusplus\n");
            w.write("}\n");
            w.write("#endif\n");
            // Typedef for struct/union types
            var ast = fm.result.must();
            for (var sd : ast.dagStructures) {
                var prefix = fm.path().toString() + "$";
                w.write("typedef ");
                w.write(sd.domain() == TypeDomain.UNION ? "union " : "struct ");
                w.write(sd.symbol().name().value());
                w.write(" " + prefix);
                w.write(sd.symbol().name().value());
                w.write(";\n");
            }
            // Generate inline wrappers with $ prefix for each C function
            for (var fd : fm.result.must().functionList) {
                if (fd.builtin() || fd.procedure().has()) continue;
                var prefix = fm.path().toString() + "$";
                w.write("inline ");
                writeCSignature(w, fd, prefix);
                w.write(" { return ");
                w.write(fd.symbol().name().value());
                w.write("(");
                var first = true;
                for (var p : fd.prototype().parameterSet()) {
                    if (!first) w.write(", ");
                    first = false;
                    var fp = (FixedParameter) p;
                    w.write(fp.name().get().value());
                }
                w.write("); }\n");
            }
        }
        System.out.printf("gen: %s.h (bridge)\n", name);
    }

    private void genBridgeCpp(FModule fm, Path dir) throws IOException {
        var name = fm.path().filename();
        var cpp = dir.resolve(name + ".cpp");
        try (var w = Files.newBufferedWriter(cpp)) {
            w.write("// auto-generated wrappers for pure C module: "
                    + name + "\n");
            for (var hf : fm.headerFiles()) {
                w.write("#include \"" + hf.getFileName() + "\"\n");
            }
            // Generate C++ wrapper for each C function
            for (var fd : fm.result.must().functionList) {
                if (fd.builtin() || fd.procedure().has()) continue;
                w.write("\n");
                // Write function signature with module-prefixed name
                writeCSignature(w, fd, name + "$");
                w.write("{\n");
                // Generate call to the real C function
                w.write("\treturn ");
                w.write(fd.symbol().name().value());
                w.write("(");
                var first = true;
                for (var p : fd.prototype().parameterSet()) {
                    if (!first) w.write(", ");
                    first = false;
                    var fp = (FixedParameter) p;
                    w.write(fp.name().get().value());
                }
                w.write(");\n}\n");
            }
        }
        System.out.printf("gen: %s.cpp (bridge)\n", name);
        compileCpp(false, dir, name, cpp);
    }

    private void writeCSignature(BufferedWriter w,
                                 FunctionDefinition fd,
                                 String prefix) throws IOException {
        if (fd.prototype().returnSet().has()) {
            // Need to map Feng types back to C types (simplified)
            w.write("int ");
        } else {
            w.write("void ");
        }
        w.write(prefix);
        w.write(fd.symbol().name().value());
        w.write("(");
        var first = true;
        for (var p : fd.prototype().parameterSet()) {
            if (!first) w.write(", ");
            first = false;
            w.write("int "); // simplified — assume int for test
            var fp = (FixedParameter) p;
            w.write(fp.name().get().value());
        }
        w.write(")");
    }

    @Test
    public void testMixedModule() throws IOException {
        var dir = ResourceUtil.getDir("mixed");
        var parser = new ModuleParser("mixed", dir, UTF_8);
        var dag = parser.parsePackage();
        new ModuleAnalysis().analyse(dag);

        CppGenerator.copyBaseHeader(dir);
        var cpps = new ArrayList<Path>();
        for (var fm : dag) {
            // Pure-C modules — generate bridge header with $prefix wrappers
            if (!fm.cSources().isEmpty()) {
                genBridgeHeader(fm, dir);
                continue;
            }
            var cpp = genCpp(fm, dir);
            cpps.add(cpp);
        }

        // Compile pure-C sources collected from non-Feng directories
        var cObjs = new ArrayList<Path>();
        for (var cSrc : parser.allCSources()) {
            var oFile = replaceExt(cSrc.getFileName().toString(), "o");
            System.out.printf("cc: %s\n", cSrc.getFileName());
            compileC(false, dir, cSrc.getFileName().toString(), cSrc);
            cObjs.add(oFile);
        }

        // Link everything
        System.out.println("c++ link");
        var all = new ArrayList<Path>();
        all.addAll(cpps);
        all.addAll(cObjs);
        compileCpp(true, dir, "mixed", all.toArray(Path[]::new));
    }

}
