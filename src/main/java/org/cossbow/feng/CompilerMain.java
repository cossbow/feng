package org.cossbow.feng;

import com.beust.jcommander.*;
import com.beust.jcommander.converters.PathConverter;
import org.cossbow.feng.analysis.AnalyseSymbolTable;
import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.dcl.PrimitiveTypeDeclarer;
import org.cossbow.feng.ast.dcl.TypeDeclarer;
import org.cossbow.feng.ast.mod.FModule;
import org.cossbow.feng.ast.proc.FixedParameter;
import org.cossbow.feng.coder.CppGenerator;
import org.cossbow.feng.dag.DAGGraph;
import org.cossbow.feng.mod.ModuleAnalysis;
import org.cossbow.feng.mod.ModuleParser;
import org.cossbow.feng.util.CommonUtil;
import org.cossbow.feng.util.ErrorUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

public class CompilerMain {
    @Parameter(names = {"-p", "-pkg"},
            description = "package name")
    private String pkg;
    @Parameter(names = {"-t", "--source-type"},
            description = "the source type",
            converter = SourceTypeConverter.class)
    private SourceType sourceType = SourceType.FILE;
    @Parameter(names = {"-i", "--input"},
            description = "path of input file/dir",
            converter = PathConverter.class, required = true)
    private Path input;
    @Parameter(names = {"-o", "--output"},
            description = "path of output dir, default to input dir",
            converter = PathConverter.class)
    private Path output;
    @DynamicParameter(names = "-L",
            description = "search libraries: [name=path] ...")
    private Map<String, String> lib = new HashMap<>();
    @Parameter(names = {"-b", "--build"},
            description = "build system type: make (default), cmake",
            converter = BuildConverter.class)
    private Build build = Build.MAKE;

    //

    static boolean debug;

    static {
        debug = Boolean.parseBoolean(System.getProperty("feng.memchk"));
    }

    private static Path toPath(String value) {
        try {
            return Paths.get(value);
        } catch (InvalidPathException e) {
            return ErrorUtil.argument(
                    "invalid path: %s", value);
        }
    }

    private Map<Identifier, ModuleParser> getLibParsers() {
        if (lib == null || lib.isEmpty()) return Map.of();
        var parsers = new HashMap<Identifier, ModuleParser>();
        if (lib.containsKey(pkg)) {
            return ErrorUtil.argument(
                    "package conflict with library '%s'", pkg);
        }
        for (var le : lib.entrySet()) {
            var base = toPath(le.getValue());
            var p = new ModuleParser(le.getKey(), base, UTF_8);
            if (parsers.put(p.pkg(), p) != null) {
                return ErrorUtil.argument(
                        "package '%s' conflict in libraries",
                        le.getKey());
            }
        }
        return parsers;
    }

    private ModuleParser parser(Path dir) {
        return new ModuleParser(pkg, dir, UTF_8, getLibParsers());
    }

    private void generateCpp(AnalyseSymbolTable ast, Path dir, String name,
                             List<String> moduleNames)
            throws IOException {
        var cpp = dir.resolve(name + ".cpp");
        try (var w = Files.newBufferedWriter(cpp, UTF_8)) {
            new CppGenerator(ast, w, false, debug).write();
        }
        var header = dir.resolve(name + ".h");
        try (var w = Files.newBufferedWriter(header, UTF_8)) {
            new CppGenerator(ast, w, true, debug).write();
        }
        moduleNames.add(name);
    }

    private void analyzeAndGenCpp(DAGGraph<FModule> dag) throws IOException {
        var moduleNames = new ArrayList<String>();
        new ModuleAnalysis().analyse(dag);
        CppGenerator.copyBaseHeader(output);

        // Collect all C source files from all modules
        var allCSources = new ArrayList<Path>();
        for (var fm : dag) {
            var mp = fm.path();
            if (!fm.cSources().isEmpty()) {
                // Pure C module — generate bridge header, copy sources, skip .cpp
                genBridgeHeader(fm, output);
                copyCSources(fm, output);
                allCSources.addAll(fm.cSources());
            } else if (!fm.headerFiles().isEmpty()) {
                // C header-only module — generate .cpp first, then append bridge
                generateCpp(fm.result.must(), output, mp.filename(), moduleNames);
                copyCSources(fm, output);
                genBridgeHeader(fm, output);
            } else {
                generateCpp(fm.result.must(), output, mp.filename(), moduleNames);
            }
        }
        if (build == Build.MAKE) {
            generateMakefile(output, moduleNames, allCSources);
        } else {
            generateCMakeLists(output, moduleNames, allCSources);
        }
        runBuild(output);
    }

    private void genBridgeHeader(FModule fm, Path dir) throws IOException {
        var name = fm.path().filename();
        var bridgeFile = dir.resolve(name + "_bridge.h");
        var hPath = dir.resolve(name + ".h");
        var hasCppHeader = Files.exists(hPath);

        // Write bridge header with extern "C" includes + inline wrappers
        try (var w = Files.newBufferedWriter(bridgeFile, UTF_8)) {
            w.write("// auto-generated bridge for C functions: " + name + "\n\n");
            // C function declarations are provided by system headers via Header.h
            // Typedef for struct/union types
            var ast = fm.result.must();
            for (var sd : ast.dagStructures) {
                w.write("typedef ");
                w.append(sd.domain().name).append(' ');
                w.append(sd.symbol().name().value()).append(' ');
                w.append(fm.path().toString()).append('$');
                w.write(sd.symbol().name().value());
                w.write(";\n");
            }
            for (var fd : ast.functionList) {
                if (fd.builtin() || fd.procedure().has()) continue;
                var prefix = fm.path().toString() + "$";
                var retType = cTypeOf(fd.prototype().returnType());
                w.write("inline " + retType + " ");
                w.write(prefix);
                w.write(fd.symbol().name().value());
                w.write("(");
                boolean first = true;
                for (var p : fd.prototype().parameterSet()) {
                    if (!first) w.write(", ");
                    first = false;
                    var fp = (FixedParameter) p;
                    w.write(cTypeOf(fp.type()));
                    w.write(' ');
                    w.write(fp.name().get().value());
                }
                w.write(") {\n\tusing F = ");
                w.write(retType);
                w.write("(*)(");
                first = true;
                for (var p : fd.prototype().parameterSet()) {
                    if (!first) w.write(", ");
                    first = false;
                    var fp = (FixedParameter) p;
                    w.write(cTypeOf(fp.type()));
                }
                w.write(");\n\treturn reinterpret_cast<F>(");
                w.write(fd.symbol().name().value());
                w.write(")(");
                first = true;
                for (var p : fd.prototype().parameterSet()) {
                    if (!first) w.write(", ");
                    first = false;
                    var fp = (FixedParameter) p;
                    w.write(fp.name().get().value());
                }
                w.write(");\n}\n");
            }
        }

        // For header-only modules (has Cpp header), add include of bridge
        if (hasCppHeader) {
            try (var w = Files.newBufferedWriter(hPath, UTF_8,
                    java.nio.file.StandardOpenOption.APPEND)) {
                w.write("\n#include \"" + name + "_bridge.h\"\n");
            }
        } else {
            // Pure-C module: rename bridge file to the module header
            Files.move(bridgeFile, hPath,
                    REPLACE_EXISTING);
        }
    }

    private static String cTypeOf(TypeDeclarer td) {
        if (td instanceof PrimitiveTypeDeclarer ptd) {
            return CppGenerator.PrimitiveName.get(ptd.primitive());
        }
        return "Uint64";
    }

    private void runBuild(Path dir) throws IOException {
        var cmd = build == Build.CMAKE ?
                List.of("cmake", "--build", ".") :
                List.of("make");
        var pb = new ProcessBuilder(cmd)
                .directory(dir.toFile())
                .inheritIO();
        try {
            int ec = pb.start().waitFor();
            if (ec != 0) {
                System.err.println("build failed (exit " + ec + ")");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("build interrupted");
        }
    }

    private void generateCMakeLists(Path dir, List<String> moduleNames,
                                    List<Path> cSources) throws IOException {
        try (var w = Files.newBufferedWriter(dir.resolve("CMakeLists.txt"), UTF_8)) {
            w.write("project(" + pkg + ")\n\n");
            w.write("set(CMAKE_CXX_STANDARD 20)\n");
            w.write("set(CMAKE_CXX_STANDARD_REQUIRED ON)\n\n");

            w.write("set(SOURCES\n");
            for (var n : moduleNames) {
                w.write("\t" + n + ".cpp\n");
            }
            for (var c : cSources) {
                w.write("\t" + c.getFileName() + "\n");
            }
            w.write(")\n\n");

            w.write("add_executable(${PROJECT_NAME} ${SOURCES})\n");
        }
    }

    private void generateMakefile(Path dir, List<String> moduleNames,
                                  List<Path> cSources) throws IOException {
        try (var w = Files.newBufferedWriter(dir.resolve("Makefile"), UTF_8)) {
            w.write("# Makefile for Fēng generated C++ code\n");
            w.write("# Generated by the Fēng compiler\n\n");

            w.write("CXX ?= c++\n");
            w.write("CC ?= cc\n");
            w.write("CXXFLAGS ?= --std=c++20 -O2\n\n");

            w.write("SRCS := ");
            boolean first = true;
            for (var name : moduleNames) {
                if (!first) w.write(" \\\n\t");
                else first = false;
                w.write(name + ".cpp");
            }
            w.write("\n\n");

            w.write("OBJS := $(SRCS:.cpp=.o)\n");
            w.write("TARGET := " + pkg + "\n\n");

            if (!cSources.isEmpty()) {
                w.write("C_SRCS := ");
                first = true;
                for (var c : cSources) {
                    if (!first) w.write(" \\\n\t");
                    else first = false;
                    w.write(c.toString());
                }
                w.write("\n");
                w.write("C_OBJS := $(C_SRCS:.c=.o)\n\n");

                w.write("$(TARGET): $(OBJS) $(C_OBJS)\n");
                w.write("\t$(CXX) $(CXXFLAGS) -o $@ $^\n\n");

                w.write("$(OBJS) $(C_OBJS): Header.h\n\n");

                w.write("$(C_OBJS): %.o: %.c\n");
                w.write("\t$(CC) -c $< -o $@\n\n");
            } else {
                w.write("$(TARGET): $(OBJS)\n");
                w.write("\t$(CXX) $(CXXFLAGS) -o $@ $^\n\n");

                w.write("$(OBJS): Header.h\n\n");
            }

            w.write("%.o: %.cpp\n");
            w.write("\t$(CXX) $(CXXFLAGS) -c $< -o $@\n\n");

            w.write("clean:\n");
            w.write("\trm -f $(OBJS)");
            if (!cSources.isEmpty()) {
                w.write(" $(C_OBJS)");
            }
            w.write(" $(TARGET)\n");
        }
    }

    private void copyCSources(FModule fm, Path dir) throws IOException {
        for (var src : fm.cSources()) {
            var target = dir.resolve(src.getFileName());
            Files.copy(src, target, REPLACE_EXISTING);
        }
        for (var hdr : fm.headerFiles()) {
            var target = dir.resolve(hdr.getFileName());
            Files.copy(hdr, target, REPLACE_EXISTING);
        }
    }


    private void compileFile() throws IOException {
        if (!Files.isRegularFile(input)) {
            ErrorUtil.argument("%s is not a regular file", input);
            return;
        }

        var fn = input.getFileName();
        if (pkg == null) {
            pkg = CommonUtil.trimExt(fn.toString());
        }
        var fm = parser(input.getParent())
                .parseFile(fn);
        analyzeAndGenCpp(fm);
    }

    private void compileModule() throws IOException {
        if (!Files.isDirectory(input)) {
            ErrorUtil.argument("%s is not a dir", input);
            return;
        }

        var dir = input.getParent();
        var fn = input.getFileName();
        if (pkg == null) pkg = fn.toString();
        var dag = parser(dir).parseModule(fn);
        analyzeAndGenCpp(dag);
    }

    private void compilePackage() throws IOException {
        if (!Files.isDirectory(input)) {
            ErrorUtil.argument("%s is not a dir", input);
            return;
        }
        if (pkg == null) pkg = input.getFileName().toString();

        var dag = parser(input).parsePackage();
        analyzeAndGenCpp(dag);
    }

    void run() throws IOException {
        if (output == null) {
            output = input;
            if (Files.isRegularFile(input))
                output = input.getParent();
        }
        if (!Files.isDirectory(output)) {
            ErrorUtil.argument("must specify a valid dir: %s", output);
            return;
        }
        switch (sourceType) {
            case FILE -> compileFile();
            case MODULE -> compileModule();
            case PACKAGE -> compilePackage();
        }
    }

    public static void main(String[] args) throws IOException {
        var main = new CompilerMain();
        var cmd = JCommander.newBuilder().addObject(main).build();
        try {
            cmd.parse(args);
            main.run();
        } catch (ParameterException e) {
            cmd.usage();
            System.exit(1);
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }
    }

    enum SourceType {
        FILE,
        MODULE,
        PACKAGE,
    }

    static class SourceTypeConverter implements IStringConverter<SourceType> {
        public SourceType convert(String s) {
            SourceType t;
            if (s.length() == 1) {
                t = switch (s.charAt(0)) {
                    case 'f' -> SourceType.FILE;
                    case 'm' -> SourceType.MODULE;
                    case 'p' -> SourceType.PACKAGE;
                    default -> null;
                };
            } else {
                t = switch (s) {
                    case "file" -> SourceType.FILE;
                    case "module" -> SourceType.MODULE;
                    case "package" -> SourceType.PACKAGE;
                    default -> null;
                };
            }
            if (t != null) return t;
            return ErrorUtil.argument("Unknown input type: %s", s);
        }
    }

    enum Build {
        MAKE,
        CMAKE,
    }

    static class BuildConverter implements IStringConverter<Build> {
        public Build convert(String s) {
            Build t;
            if (s.length() == 1) {
                t = switch (s.charAt(0)) {
                    case 'm' -> Build.MAKE;
                    case 'c' -> Build.CMAKE;
                    default -> null;
                };
            } else {
                t = switch (s) {
                    case "make" -> Build.MAKE;
                    case "cmake" -> Build.CMAKE;
                    default -> null;
                };
            }
            if (t != null) return t;
            return ErrorUtil.argument("Unknown builder: %s", s);
        }
    }

}
