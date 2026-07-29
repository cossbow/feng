package org.cossbow.feng;

import com.beust.jcommander.*;
import com.beust.jcommander.converters.PathConverter;
import org.cossbow.feng.coder.CGenerator;
import org.cossbow.feng.coder.CppGenerator;
import org.cossbow.feng.util.ErrorUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * CLI entry point for the Fēng compiler.
 * Parses arguments, then delegates to {@link Compiler}.
 */
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
    private Compiler.Build build = Compiler.Build.MAKE;
    @Parameter(names = {"-D", "--debug"},
            description = "enable debug mode (assert statements, debug checks)")
    private boolean debug = false;
    @Parameter(names = {"-T", "--test"},
            description = "enable unit test mode")
    private boolean test = false;
    @Parameter(names = {"--test-name"},
            description = "testcase name filter (repeatable)")
    private List<String> testNames = new ArrayList<>();

    // backend selector — controlled by JVM property: -Dfeng.target=c|cpp
    private static final String TARGET_PROP = "feng.target";

    private Compiler compiler() {
        var t = System.getProperty(TARGET_PROP, "c");
        var factory = switch (t) {
            case "cpp", "c++" -> CppGenerator.FACTORY;
            default -> CGenerator.FACTORY;
        };
        var c = new Compiler(factory);
        c.debug(debug);
        c.test(test).testFilter(testNames);
        c.buildSystem(build);
        c.pkg(pkg);
        c.lib(lib);
        return c;
    }

    // ---- run / main ----

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
        var c = compiler();
        switch (sourceType) {
            case FILE -> c.compileFile(input, output);
            case MODULE -> c.compileModule(input, output);
            case PACKAGE -> c.compilePackage(input, output);
            case TEST -> c.compileTest(input, output);
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

    // ---- CLI enums & converters ----

    enum SourceType {
        FILE,
        MODULE,
        PACKAGE,
        TEST,
    }

    static class SourceTypeConverter implements IStringConverter<SourceType> {
        public SourceType convert(String s) {
            SourceType t;
            if (s.length() == 1) {
                t = switch (s.charAt(0)) {
                    case 'f' -> SourceType.FILE;
                    case 'm' -> SourceType.MODULE;
                    case 'p' -> SourceType.PACKAGE;
                    case 't' -> SourceType.TEST;
                    default -> null;
                };
            } else {
                t = switch (s) {
                    case "file" -> SourceType.FILE;
                    case "module" -> SourceType.MODULE;
                    case "package" -> SourceType.PACKAGE;
                    case "test" -> SourceType.TEST;
                    default -> null;
                };
            }
            if (t != null) return t;
            return ErrorUtil.argument("Unknown input type: %s", s);
        }
    }

    static class BuildConverter implements IStringConverter<Compiler.Build> {
        public Compiler.Build convert(String s) {
            Compiler.Build t;
            if (s.length() == 1) {
                t = switch (s.charAt(0)) {
                    case 'm' -> Compiler.Build.MAKE;
                    case 'c' -> Compiler.Build.CMAKE;
                    default -> null;
                };
            } else {
                t = switch (s) {
                    case "make" -> Compiler.Build.MAKE;
                    case "cmake" -> Compiler.Build.CMAKE;
                    default -> null;
                };
            }
            if (t != null) return t;
            return ErrorUtil.argument("Unknown builder: %s", s);
        }
    }
}
