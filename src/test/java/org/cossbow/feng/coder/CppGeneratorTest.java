package org.cossbow.feng.coder;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.cossbow.feng.analysis.SemanticAnalysis;
import org.cossbow.feng.ast.Source;
import org.cossbow.feng.parser.ParseSymbolTable;
import org.cossbow.feng.parser.SourceParser;
import org.cossbow.feng.visit.GlobalSymbolContext;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class CppGeneratorTest {

    static Source doParse(String filename, CharStream cs) {
        var pr = new SourceParser(filename,
                StandardCharsets.UTF_8,
                new ParseSymbolTable()).parse(cs);
        return pr.root();
    }

    static Source doParse(File file) {
        try (var is = new FileInputStream(file)) {
            return doParse(file.getName(), CharStreams.fromStream(is));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static void generate(File file) {
        var cpp = file.getName().replace(".feng", ".cpp");
        var target = "target/cpp/" + cpp;
        System.out.printf("[compile]%s >>> %s\n", file, target);
        // 解析ast
        var source = doParse(file);
        // 分析ast
        var ctx = new GlobalSymbolContext(source.table());
        new SemanticAnalysis(ctx, false).analyse(source);
        // 生成C++代码
        try (var out = new FileOutputStream(target);
             var bo = new BufferedOutputStream(out);
             var w = new OutputStreamWriter(bo)) {
            new CppGenerator(ctx, w, true).write(source);
            w.flush();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    public void testSample() throws IOException {
        Files.createDirectories(Path.of("target/cpp/"));
        var cl = Thread.currentThread().getContextClassLoader();
        var dir = new File(cl.getResource("coder").getFile());
        for (var file : dir.listFiles()) {
            generate(file);
        }
    }
}
