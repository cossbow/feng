package org.cossbow.feng.coder;

import org.cossbow.feng.analysis.SemanticAnalysis;
import org.cossbow.feng.ast.Source;
import org.cossbow.feng.parser.BaseParseTest;
import org.cossbow.feng.visit.GlobalSymbolContext;

import java.io.*;

public class CppGeneratorMain {

    static Source doParse(String filename) {
        try (var is = new FileInputStream(filename)) {
            return BaseParseTest.doParseFile(is);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static void generate(String srcFile, String cppFile) {
        System.out.printf("[compile]%s >>> %s\n", srcFile, cppFile);
        // 解析ast
        var source = doParse(srcFile);
        // 分析ast
        var ctx = new GlobalSymbolContext(source.table());
        new SemanticAnalysis(ctx).visit(source);
        // 生成C++代码
        try (var out = new FileOutputStream(cppFile);
             var bo = new BufferedOutputStream(out);
             var w = new OutputStreamWriter(bo)) {
            new CppGenerator(ctx, w, true).write(source);
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
