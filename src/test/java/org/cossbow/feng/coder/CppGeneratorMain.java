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
        var source = doParse(srcFile);
        var ctx = new GlobalSymbolContext(source.table());
        new SemanticAnalysis(ctx).visit(source);
        try (var out = new FileOutputStream(cppFile);
             var w = new OutputStreamWriter(out)) {
            new CppGenerator(source.table(), ctx, w, true).write(source);
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
