package org.cossbow.feng.coder;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.cossbow.feng.analysis.SemanticAnalysis;
import org.cossbow.feng.ast.Source;
import org.cossbow.feng.parser.ParseSymbolTable;
import org.cossbow.feng.parser.SourceParser;
import org.cossbow.feng.util.ErrorUtil;
import org.cossbow.feng.visit.GlobalSymbolContext;

import java.io.*;
import java.nio.charset.StandardCharsets;

public class GeneratorMain {

    static Source doParse(String filename, CharStream cs) {
        var pr = new SourceParser(filename,
                StandardCharsets.UTF_8,
                new ParseSymbolTable()).parse(cs);
        if (pr.errors().isEmpty()) return pr.root();
        return ErrorUtil.syntax("parse error: %s".formatted(pr.errors()));
    }

    static Source doParse(String filename) {
        try (var is = new FileInputStream(filename)) {
            return doParse(filename, CharStreams.fromStream(is));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static void generate(String srcFile, String cppFile) {
        System.out.printf("[compile]%s >>> %s\n", srcFile, cppFile);
        var low = "low".equals(System.getProperty("gen.cpp"));
        // 解析ast
        var source = doParse(srcFile);
        // 分析ast
        var ctx = new GlobalSymbolContext(source.table());
        new SemanticAnalysis(ctx, low).analyse(source);
        // 生成C++代码
        try (var out = new FileOutputStream(cppFile);
             var bo = new BufferedOutputStream(out);
             var w = new OutputStreamWriter(bo)) {
            if (low) {
                new LowCppGenerator(ctx, w, true).write(source);
            } else {
                new CppGenerator(ctx, w, true).write(source);
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
        generate(args[0], args[1]);
    }

}
