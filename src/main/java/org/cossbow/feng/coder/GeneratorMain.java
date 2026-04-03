package org.cossbow.feng.coder;

import org.cossbow.feng.analysis.SemanticAnalysis;
import org.cossbow.feng.parser.ModuleParser;
import org.cossbow.feng.parser.ParseSymbolTable;
import org.cossbow.feng.parser.SourceParser;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static java.nio.charset.StandardCharsets.UTF_8;

public class GeneratorMain {

    static ParseSymbolTable parse(Path src) {
        var table = new ParseSymbolTable();
        if (Files.isRegularFile(src)) {
            return new SourceParser(UTF_8, table)
                    .parse(src).table();
        }
        return new ModuleParser(UTF_8)
                .parse(src.getParent(), src.getFileName())
                .table();
    }

    static void generate(Path src, Path cppFile) {
        System.out.printf("[compile]%s >>> %s\n", src, cppFile);
        var low = "low".equals(System.getProperty("gen.cpp"));
        // 解析ast
        var table = parse(src);
        // 分析ast
        new SemanticAnalysis(table, low).analyse();
        // 生成C++代码
        try (var out = Files.newOutputStream(cppFile);
             var bo = new BufferedOutputStream(out);
             var w = new OutputStreamWriter(bo)) {
            if (low) {
                new LowCppGenerator(table, w, true).write();
            } else {
                new CppGenerator(table, w, true).write();
            }
            w.flush();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: cppGen sourceFile cppFile");
            return;
        }
        generate(Path.of(args[0]), Path.of(args[1]));
    }

}
