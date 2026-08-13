package org.cossbow.feng;

import org.cossbow.feng.coder.CGenerator;
import org.cossbow.feng.coder.CppGenerator;
import org.cossbow.feng.util.ErrorUtil;
import org.cossbow.feng.util.TargetOS;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;

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
@Command(name = "feng",
        description = "Fēng compiler — compiles .f sources to C/C++")
public class CompilerMain {

    @Option(names = {"-p", "--pkg"},
            description = "package name")
    private String pkg;

    @Option(names = {"-t", "--source-type"},
            description = "the source type",
            converter = SourceTypeConverter.class)
    private SourceType sourceType = SourceType.FILE;

    @Option(names = {"-i", "--input"},
            description = "path of input file/dir",
            required = true)
    private Path input;

    @Option(names = {"-o", "--output"},
            description = "path of output dir, default to input dir")
    private Path output;

    @Option(names = "-L",
            description = "search libraries: name=path (repeatable)")
    private Map<String, String> lib = new HashMap<>();

    @Option(names = {"-b", "--build"},
            description = "build system type: make (default), cmake",
            converter = BuildConverter.class)
    private Compiler.Build build = Compiler.Build.MAKE;

    @Option(names = {"--os"},
            description = "target os: auto (default), windows, linux",
            converter = TargetOSConverter.class)
    private TargetOS os = TargetOS.AUTO;

    @Option(names = {"-D", "--debug"},
            description = "enable debug mode (assert statements, debug checks)")
    private boolean debug;

    @Option(names = {"-T", "--test"},
            description = "enable unit test mode")
    private boolean test;

    @Option(names = {"--test-name"},
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
        c.sanitizer(System.getProperty("san"));
        c.test(test).testFilter(testNames);
        c.buildSystem(build);
        c.os(os);
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

    public static void main(String[] args) {
        var main = new CompilerMain();
        var cmd = new CommandLine(main);
        try {
            cmd.parseArgs(args);
            main.run();
        } catch (ParameterException e) {
            System.err.println(e.getMessage());
            cmd.usage(System.err);
            System.exit(1);
        } catch (IOException | IllegalArgumentException e) {
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

    static class SourceTypeConverter implements ITypeConverter<SourceType> {
        @Override
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
            throw new IllegalArgumentException("Unknown input type: " + s);
        }
    }

    static class BuildConverter implements ITypeConverter<Compiler.Build> {
        @Override
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
            throw new IllegalArgumentException("Unknown builder: " + s);
        }
    }

    static class TargetOSConverter implements ITypeConverter<TargetOS> {
        @Override
        public TargetOS convert(String s) {
            return switch (s.toLowerCase()) {
                case "auto", "a" -> TargetOS.AUTO;
                case "windows", "win", "w" -> TargetOS.WINDOWS;
                case "linux", "lin", "l" -> TargetOS.LINUX;
                default -> throw new IllegalArgumentException(
                        "Unknown os: " + s + " (use auto/windows/linux)");
            };
        }
    }
}
