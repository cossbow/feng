package org.cossbow.feng;

import org.cossbow.feng.analysis.AnalyseSymbolTable;
import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.mod.FModule;
import org.cossbow.feng.coder.Generator;
import org.cossbow.feng.mod.ModuleAnalysis;
import org.cossbow.feng.mod.ModuleManager;
import org.cossbow.feng.mod.ModuleParser;
import org.cossbow.feng.util.Command;
import org.cossbow.feng.util.CommonUtil;
import org.cossbow.feng.util.ErrorUtil;
import org.cossbow.feng.util.TargetOS;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static org.cossbow.feng.util.CommonUtil.letters;
import static org.cossbow.feng.util.ErrorUtil.argument;

/**
 * Compilation engine: generates C/C++ code for Feng modules,
 * produces build system files, and invokes the native compiler.
 * <p>
 * Reusable from both CLI ({@link CompilerMain}) and tests.
 */
public class Compiler {

    public enum Build {
        MAKE,
        CMAKE,
    }

    private final Generator.Factory factory;
    private boolean debug;
    private String sanitizer;
    private boolean test;
    private Set<String> testFilter = Set.of();
    private String pkg;
    private Map<String, String> lib = Map.of();
    private Build build = Build.MAKE;
    private TargetOS os = TargetOS.AUTO;

    public Compiler(Generator.Factory factory) {
        this.factory = CommonUtil.required(factory);
    }

    public Compiler debug(boolean debug) {
        this.debug = debug;
        if (debug) ErrorUtil.setTraceError(true);
        return this;
    }

    public Compiler sanitizer(String sanitizer) {
        this.sanitizer = sanitizer;
        return this;
    }

    public Compiler test(boolean test) {
        this.test = test;
        if (test) ErrorUtil.setTraceError(true);
        return this;
    }

    public Compiler testFilter(List<String> names) {
        this.testFilter = names.isEmpty() ? Set.of()
                : new HashSet<>(names);
        return this;
    }

    public Compiler pkg(String pkg) {
        this.pkg = pkg;
        return this;
    }

    public Compiler lib(Map<String, String> lib) {
        this.lib = lib;
        return this;
    }

    public Compiler buildSystem(Build build) {
        this.build = build;
        return this;
    }

    public Compiler os(TargetOS os) {
        this.os = CommonUtil.required(os);
        return this;
    }

    // ---- public entry points ----

    /**
     * Compile a single source file.
     */
    public void compileFile(Path input, Path output)
            throws IOException {
        if (!Files.isRegularFile(input)) {
            throw new IllegalArgumentException(input + " is not a regular file");
        }
        var fn = input.getFileName();
        if (this.pkg == null)
            this.pkg = letters(CommonUtil.trimExt(fn.toString()));
        var fm = parser(this.pkg, input.getParent(), this.lib).parseFile(fn);
        compile(fm, output);
    }

    /**
     * Compile a single module (directory).
     */
    public void compileModule(Path input, Path output)
            throws IOException {
        if (!Files.isDirectory(input)) {
            throw new IllegalArgumentException(input + " is not a dir");
        }
        var dir = input.getParent();
        var fn = input.getFileName();
        if (this.pkg == null) this.pkg = letters(fn.toString());
        var dag = parser(this.pkg, dir, this.lib).parseModule(fn);
        compile(dag, output);
    }

    /**
     * Compile an entire package (directory tree).
     */
    public void compilePackage(Path input, Path output)
            throws IOException {
        if (!Files.isDirectory(input)) {
            throw new IllegalArgumentException(input + " is not a dir");
        }
        if (this.pkg == null)
            this.pkg = letters(input.getFileName().toString());
        var dag = parser(this.pkg, input, this.lib).parsePackage();
        compile(dag, output);
    }

    /**
     * Compile a test file or directory.
     * Supports both single test files and test module directories.
     */
    public void compileTest(Path input, Path output)
            throws IOException {
        this.test = true;
        if (Files.isRegularFile(input)) {
            var fn = input.getFileName();
            if (this.pkg == null)
                this.pkg = letters(CommonUtil.trimExt(fn.toString()));
            var dag = parser(this.pkg, input.getParent(), this.lib).parseFile(fn);
            compile(dag, output);
        } else if (Files.isDirectory(input)) {
            if (this.pkg == null)
                this.pkg = letters(input.getFileName().toString());
            var dag = parser(this.pkg, input, this.lib).parseModule(
                    input.getFileName());
            compile(dag, output);
        } else {
            throw new IllegalArgumentException(input + " is not a file or directory");
        }
    }
    // ---- parser helpers ----

    private static Path toPath(String value) {
        try {
            return Paths.get(value);
        } catch (InvalidPathException e) {
            return argument("invalid path: %s", value);
        }
    }

    private static Map<Identifier, ModuleParser> getLibParsers(
            String pkg, Map<String, String> lib, boolean test,
            TargetOS os) {
        if (lib == null || lib.isEmpty()) return Map.of();
        var parsers = new HashMap<Identifier, ModuleParser>();
        if (lib.containsKey(pkg)) {
            return argument("package conflict with library '%s'",
                    pkg);
        }
        for (var le : lib.entrySet()) {
            var base = toPath(le.getValue());
            var p = new ModuleParser(le.getKey(), base, UTF_8,
                    Map.of(), test, os);
            if (parsers.put(p.pkg(), p) != null) {
                return argument("package '%s' conflict in libraries",
                        le.getKey());
            }
        }
        return parsers;
    }

    private ModuleParser parser(
            String pkg, Path dir,
            Map<String, String> lib) {
        return new ModuleParser(pkg, dir, UTF_8,
                getLibParsers(pkg, lib, test, os), test, os);
    }

    // ---- core pipeline ----

    public void compile(ModuleManager manager, Path dir)
            throws IOException {
        // Clean stale generated files from previous compilations to prevent
        // symbol ID mismatches (the static IdGenerator in Variable assigns
        // different IDs across runs).
        cleanBuildDir(dir);

        var ma = new ModuleAnalysis(test);
        ma.analyse(manager);
        ErrorUtil.reportError(ma.errors());
        factory.copyBaseHeader(dir);

        // Collect all C source files from all modules
        var dag = manager.dag();
        var allCSources = new ArrayList<Path>();
        boolean hasMain = test;  // test mode always produces executable
        for (var fm : dag) {
            var mp = fm.path();
            var ast = fm.result.must();
            ast.test = test;
            ast.testFilter = testFilter;
            if (!hasMain) {
                hasMain = ast.main.has();
            }
            generate(ast, dir, mp.filename());
            copyCSources(fm, dir);
        }

        // Collect link libraries from all modules, deduplicate keeping order
        var allLinkLibs = new LinkedHashSet<String>(dag.size() + 2);
        for (var fm : dag) {
            allLinkLibs.addAll(fm.linkLibs());
        }
        allLinkLibs.add("m");

        if (build == Build.MAKE) {
            generateMakefile(dir, allCSources, hasMain,
                    List.copyOf(allLinkLibs));
        } else {
            generateCMakeLists(dir, allCSources, hasMain,
                    List.copyOf(allLinkLibs));
        }
        runBuild(dir);
    }

    // ---- single-module code generation ----

    void generate(AnalyseSymbolTable ast, Path dir, String name)
            throws IOException {
        var ext = factory.extension();
        var src = dir.resolve(name + ext);
        try (var w = Files.newBufferedWriter(src, UTF_8)) {
            var gen = factory.create(ast, w, false, debug);
            gen.write();
        }
        var header = dir.resolve(name + ".h");
        try (var w = Files.newBufferedWriter(header, UTF_8)) {
            var gen = factory.create(ast, w, true, debug);
            gen.write();
        }
    }

    // ---- build execution ----

    void runBuild(Path dir) {
        if (build == Build.CMAKE) {
            var isC = factory.extension().equals(".c");
            var cmakeArgs = new ArrayList<String>();
            cmakeArgs.add("cmake");

            // Prefer Ninja on all os for fast parallel builds
            if (TargetOS.commandExists("ninja")) {
                cmakeArgs.add("-G");
                cmakeArgs.add("Ninja");
            } else if (TargetOS.isHostWindows()) {
                cmakeArgs.add("-G");
                cmakeArgs.add("MinGW Makefiles");
            }

            cmakeArgs.add(".");
            cmakeArgs.add("-DCMAKE_C_COMPILER=" + factory.compiler());
            cmakeArgs.add("--log-level=ERROR");
            if (!isC) cmakeArgs.add("-DCMAKE_CXX_COMPILER=" + factory.compiler());
            var ret = new Command(dir, cmakeArgs).exec();
            if (ret.code() != 0) {
                ErrorUtil.backend("cmake configure failed (exit %d): %s",
                        ret.code(), ret.err());
                return;
            }

            // --build: Ninja auto-parallelizes; Make needs -j
            var buildArgs = new ArrayList<String>();
            buildArgs.add("cmake");
            buildArgs.add("--build");
            buildArgs.add(".");
            if (!TargetOS.commandExists("ninja")) {
                buildArgs.add("-j");
            }
            buildArgs.add("--");
            buildArgs.add("-s");
            ret = new Command(dir, buildArgs).exec();
            if (ret.code() != 0) {
                ErrorUtil.backend("build failed (exit %d): %s",
                        ret.code(), ret.err());
            }
        } else {
            var makeCmd = TargetOS.detectMake();
            var ret = new Command(dir, makeCmd, "-s", "-j").exec();
            if (ret.code() != 0) {
                ErrorUtil.backend("build failed (exit %d): %s",
                        ret.code(), ret.err());
            }
        }
    }

    // ---- build system generation ----

    void generateCMakeLists(Path dir,
                            List<Path> cSources, boolean hasMain,
                            List<String> allLinkLibs) throws IOException {
        var isC = factory.extension().equals(".c");
        var langStandard = isC ? "C_STANDARD 11" : "CXX_STANDARD 20";
        try (var w = Files.newBufferedWriter(dir.resolve("CMakeLists.txt"), UTF_8)) {
            w.write("cmake_minimum_required(VERSION 3.16)\n");
            w.write("project(" + pkg + ")\n\n");
            w.write("set(CMAKE_" + langStandard + ")\n");
            w.write("set(CMAKE_" + (isC ? "C" : "CXX") + "_STANDARD_REQUIRED ON)\n\n");

            w.write("file(GLOB SOURCES \"*." + (isC ? "c" : "cpp") + "\")\n");
            if (!isC && !cSources.isEmpty()) {
                w.write("file(GLOB C_SOURCES \"*.c\")\n");
                w.write("set_source_files_properties(${C_SOURCES} PROPERTIES LANGUAGE C)\n");
            }
            if (hasMain) {
                w.write("\nadd_executable(${PROJECT_NAME} ${SOURCES}");
            } else {
                w.write("\nadd_library(${PROJECT_NAME} STATIC ${SOURCES}");
            }
            if (!isC && !cSources.isEmpty()) w.write(" ${C_SOURCES}");
            w.write(")\n");

            w.write("\ntarget_compile_options(${PROJECT_NAME} PRIVATE " +
                    (debug ? "-g -Og" : "-O2") +
                    " -Wno-incompatible-pointer-types)");
            if (sanitizer != null && !sanitizer.isEmpty()) {
                w.write("\ntarget_compile_options(${PROJECT_NAME} PRIVATE"
                        + " -fsanitize=" + sanitizer + " -fno-omit-frame-pointer)\n");
                w.write("target_link_options(${PROJECT_NAME} PRIVATE"
                        + " -fsanitize=" + sanitizer + ")\n");
            }

            if (os.isCross()) {
                var crossFlags = " --target=" + os.targetTriple()
                        + " -D" + os.osDefine();
                w.write("\ntarget_compile_options(${PROJECT_NAME} PRIVATE"
                        + crossFlags + ")\n");
            }

            if (!allLinkLibs.isEmpty()) {
                w.write("\ntarget_link_libraries(${PROJECT_NAME}");
                for (var lib : allLinkLibs) {
                    w.write(" ");
                    w.write(lib);
                }
                w.write(")\n");
            }
        }
    }

    void generateMakefile(Path dir,
                          List<Path> cSources, boolean hasMain,
                          List<String> allLinkLibs) throws IOException {
        var isC = factory.extension().equals(".c");
        var srcExt = isC ? ".c" : ".cpp";
        var compilerVar = isC ? "CC" : "CXX";
        var flagsVar = isC ? "CFLAGS" : "CXXFLAGS";
        var stdFlag = isC ? "--std=c11" : "--std=c++20";
        var arVar = "AR";
        var ccDefault = factory.compiler();

        var linkFlags = new StringBuilder();
        for (var lib : allLinkLibs) {
            linkFlags.append(" -l").append(lib);
        }

        var moreFlags = (sanitizer == null || sanitizer.isEmpty())
                ? "" : " -fsanitize=" + sanitizer + " -fno-omit-frame-pointer";
        if (debug) moreFlags += " -g -Og";
        else moreFlags += " -O2";
        moreFlags += " -Wno-incompatible-pointer-types";

        // cross-compilation: add target triple + OS define so C #ifdef matches
        if (os.isCross()) {
            moreFlags += " --target=" + os.targetTriple();
            moreFlags += " -D" + os.osDefine();
        }

        try (var w = Files.newBufferedWriter(dir.resolve("Makefile"), UTF_8)) {
            w.write("# Makefile for Fēng generated " + (isC ? "C" : "C++") + " code\n");
            w.write("# Generated by the Fēng compiler\n\n");

            w.write(compilerVar + " ?= " + ccDefault + "\n");
            if (!isC) w.write("CC ?= clang\n");
            w.write(flagsVar + " ?= " + stdFlag + moreFlags + "\n");
            if (!isC) w.write("CFLAGS ?= --std=c11 " + moreFlags + "\n\n");
            else w.write("\n");

            w.write("SRCS := $(wildcard *" + srcExt + ")\n");
            if (!isC && !cSources.isEmpty()) {
                w.write("C_SRCS := $(wildcard *.c)\n");
                w.write("C_OBJS := $(C_SRCS:.c=.o)\n");
            }
            w.write("OBJS := $(SRCS:" + srcExt + "=.o)");
            if (!isC && !cSources.isEmpty()) w.write(" $(C_OBJS)");
            w.write("\n");
            if (hasMain) {
                w.write("TARGET := " + pkg + "\n\n");
                w.write("$(TARGET): $(OBJS)\n");
                w.write("\t$(" + compilerVar + ") $(" + flagsVar + ") -o $@ $^");
                w.write(linkFlags.toString());
                w.write("\n\n");
            } else {
                w.write("TARGET := lib" + pkg + ".a\n\n");
                w.write("$(TARGET): $(OBJS)\n");
                w.write("\t" + arVar + " rcs $@ $^\n\n");
            }

            w.write("$(OBJS): Header.h\n\n");

            w.write("%.o: %" + srcExt + "\n");
            w.write("\t$(" + compilerVar + ") $(" + flagsVar + ") -c $< -o $@\n");
            if (!isC) {
                w.write("\n%.o: %.c\n");
                w.write("\t$(CC) $(CFLAGS) -c $< -o $@\n");
            }
            w.write("\nclean:\n");
            w.write("\trm -f $(OBJS) $(TARGET)\n");
        }
    }

    // ---- utilities ----

    /**
     * Remove stale generated C/H files from a previous compilation.
     * Without this, old {@code .c} / {@code .h} files holding
     * different variable IDs ({@link org.cossbow.feng.ast.dcl.Variable#id()})
     * cause symbol mismatches at link time.
     */
    private static void cleanBuildDir(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) return;
        try (var stream = Files.list(dir)) {
            for (var f : stream.toList()) {
                var name = f.getFileName().toString();
                // Clean generated source/header/object/build files.
                // C source files belonging to modules are re‑copied by
                // copyCSources() later in the pipeline.
                if (name.endsWith(".c") || name.endsWith(".h")
                        || name.endsWith(".o") || name.endsWith(".obj")
                        || name.equals("Makefile")
                        || name.equals("CMakeLists.txt")) {
                    Files.deleteIfExists(f);
                }
            }
        }
    }

    void copyCSources(FModule fm, Path dir) throws IOException {
        for (var src : fm.cSources()) {
            var target = dir.resolve(src.getFileName());
            Files.copy(src, target, REPLACE_EXISTING);
        }
        for (var hdr : fm.headerFiles()) {
            var target = dir.resolve(hdr.getFileName().toString());
            Files.copy(hdr, target, REPLACE_EXISTING);
        }
    }
}
