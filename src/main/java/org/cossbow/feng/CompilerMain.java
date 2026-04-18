package org.cossbow.feng;

import com.beust.jcommander.*;
import com.beust.jcommander.converters.PathConverter;
import org.cossbow.feng.analysis.AnalyseSymbolTable;
import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.mod.FModule;
import org.cossbow.feng.coder.CppGenerator;
import org.cossbow.feng.dag.DAGGraph;
import org.cossbow.feng.mod.ModuleAnalysis;
import org.cossbow.feng.mod.ModuleParser;
import org.cossbow.feng.util.CommonUtil;
import org.cossbow.feng.util.ErrorUtil;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;

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

    //

    private static Path toPath(String value) {
        try {
            return Paths.get(value);
        } catch (InvalidPathException e) {
            throw new ParameterException(
                    "invalid path: %s".formatted(value));
        }
    }

    private static Path getDir(String name) {
        var cl = Thread.currentThread().getContextClassLoader();
        var dir = cl.getResource(name);
        assert dir != null;
        return new File(dir.getFile()).toPath();
    }

    private static final ModuleParser stdInner =
            new ModuleParser("std", getDir("std"), UTF_8);

    private Map<Identifier, ModuleParser> getLibParsers() {
        if (lib == null || lib.isEmpty())
            return Map.of(stdInner.pkg(), stdInner);
        var parsers = new HashMap<Identifier, ModuleParser>(1 + lib.size());
        parsers.put(stdInner.pkg(), stdInner);
        for (var le : lib.entrySet()) {
            var base = toPath(le.getValue());
            var p = new ModuleParser(le.getKey(), base, UTF_8);
            parsers.put(p.pkg(), p);
        }
        return parsers;
    }

    private ModuleParser parser(Path dir) {
        return new ModuleParser(pkg, dir, UTF_8, getLibParsers());
    }

    private void generateCpp(AnalyseSymbolTable ast, Path dir, String name)
            throws IOException {
        var cpp = dir.resolve(name + ".cpp");
        try (var w = Files.newBufferedWriter(cpp, UTF_8)) {
            new CppGenerator(ast, w, true).write();
        }
        var header = dir.resolve(name + ".h");
        try (var w = Files.newBufferedWriter(header, UTF_8)) {
            new CppGenerator(ast, w, true, false).write();
        }
    }

    private void analyzeAndGenCpp(DAGGraph<FModule> dag) throws IOException {
        new ModuleAnalysis().analyse(dag);
        CppGenerator.copyBaseHeader(output);
        for (var fm : dag) {
            var mp = fm.path();
            generateCpp(fm.result.must(), output, mp.filename());
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
        try {
            var main = new CompilerMain();
            JCommander.newBuilder().addObject(main).build().parse(args);
            main.run();
        } catch (ParameterException e) {
            e.getJCommander().usage();
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

}
