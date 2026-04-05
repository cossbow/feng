package org.cossbow.feng;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.converters.BaseConverter;
import com.beust.jcommander.converters.PathConverter;
import org.cossbow.feng.analysis.AnalyseSymbolTable;
import org.cossbow.feng.coder.CppGenerator;
import org.cossbow.feng.mod.ModuleAnalysis;
import org.cossbow.feng.mod.ModuleParser;
import org.cossbow.feng.parser.ParseSymbolTable;
import org.cossbow.feng.parser.SourceParser;
import org.cossbow.feng.util.Constants;
import org.cossbow.feng.util.ErrorUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static java.nio.charset.StandardCharsets.UTF_8;

public class CompilerMain {
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

    //
    private ParseSymbolTable parse(Path src) {
        if (Files.isRegularFile(src)) {
            return new SourceParser(UTF_8)
                    .parse(src).table();
        }
        return new ModuleParser(src.getParent(), UTF_8)
                .parseModule(src.getFileName())
                .table();
    }

    private void generateCpp(AnalyseSymbolTable ast, Path dir, String name)
            throws IOException {
        var cpp = dir.resolve(name + ".cpp");
        try (var w = Files.newBufferedWriter(cpp, UTF_8)) {
            new CppGenerator(ast, w, true).write();
        }
        if (ast.main.has()) return;
        var header = dir.resolve(replaceExt(cpp.getFileName(), "h"));
        try (var w = Files.newBufferedWriter(header, UTF_8)) {
            new CppGenerator(ast, w, true, false).write();
        }
    }

    private void compileFile() throws IOException {
        if (!Files.isRegularFile(input)) {
            ErrorUtil.argument("%s is not a regular file", input);
            return;
        }

        var fm = new ModuleParser(input.getParent(), UTF_8)
                .parseFile(input);
        var ast = new ModuleAnalysis().analyse(fm);
        var name = input.getFileName().toString();
        if (Constants.isSource(name))
            name = name.substring(0, name.length() - Constants.SRC_EXT.length());
        generateCpp(ast, output, name);
        CppGenerator.copyBaseHeader(output.getParent());
    }

    private void compileModule() throws IOException {
        if (!Files.isDirectory(input)) {
            ErrorUtil.argument("%s is not a dir", input);
            return;
        }

        var dir = input.getParent();
        var fn = input.getFileName();
        var fm = new ModuleParser(dir, UTF_8)
                .parseModule(fn);
        var ast = new ModuleAnalysis().analyse(fm);
        generateCpp(ast, output, fn.toString());
        CppGenerator.copyBaseHeader(output.getParent());
    }

    private void compileProject() throws IOException {
        if (!Files.isDirectory(input)) {
            ErrorUtil.argument("%s is not a dir", input);
            return;
        }
        var proj = input.getFileName().toString();

        // TODO：多文件联合编译，需要头文件或直接添加声明
        var dag = new ModuleParser(input, UTF_8).scanAndParse();
        new ModuleAnalysis().analyse(dag);
        for (var fm : dag) {
            var mp = fm.path();
            generateCpp(fm.result.must(), output, mp.filename());
        }
        CppGenerator.copyBaseHeader(output);
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
            case PROJECT -> compileProject();
        }
    }

    public static void main(String[] args) throws IOException {
        var main = new CompilerMain();
        JCommander.newBuilder().addObject(main).build().parse(args);
        main.run();
    }

    enum SourceType {
        FILE,
        MODULE,
        PROJECT,
    }

    static class SourceTypeConverter extends BaseConverter<SourceType> {
        public SourceTypeConverter(String optionName) {
            super(optionName);
        }

        public SourceType convert(String s) {
            SourceType t;
            if (s.length() == 1) {
                t = switch (s.charAt(0)) {
                    case 'f' -> SourceType.FILE;
                    case 'm' -> SourceType.MODULE;
                    case 'p' -> SourceType.PROJECT;
                    default -> null;
                };
            } else {
                t = switch (s) {
                    case "file" -> SourceType.FILE;
                    case "module" -> SourceType.MODULE;
                    case "project" -> SourceType.PROJECT;
                    default -> null;
                };
            }
            if (t != null) return t;
            return ErrorUtil.argument("Unknown input type: %s", s);
        }
    }

    static Path replaceExt(Path file, String ext) {
        var name = file.getFileName().toString();
        var i = name.lastIndexOf('.');
        if (i < 0) return Path.of(name + '.' + ext);
        return Path.of(name.substring(0, i + 1) + ext);
    }

}
