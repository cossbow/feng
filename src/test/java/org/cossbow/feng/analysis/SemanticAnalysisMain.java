package org.cossbow.feng.analysis;

import org.cossbow.feng.ast.Source;
import org.cossbow.feng.parser.BaseParseTest;
import org.cossbow.feng.visit.GlobalSymbolContext;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

public class SemanticAnalysisMain {

    static Source doParse(String filename) {
        try (var is = new FileInputStream(filename)) {
            return BaseParseTest.doParseFile(is);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static void checkSucc(String file) {
        System.out.printf("[check]>>> %s\n", file);
        var src = doParse(file);
        var ctx = new GlobalSymbolContext(src.table());
        new SemanticAnalysis(ctx, false).analyse(src);
    }

    public static void main(String[] args) {
        for (var a : args) {
            checkSucc(a);
        }
    }
}
