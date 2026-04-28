package org.cossbow.feng.mod;

import org.antlr.v4.runtime.CharStreams;
import org.cossbow.feng.ast.mod.FModule;
import org.cossbow.feng.dag.DAGGraph;
import org.cossbow.feng.parser.ParseSymbolTable;
import org.cossbow.feng.parser.SourceParser;
import org.cossbow.feng.util.BufferOutputStream;
import org.junit.jupiter.api.Test;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import static java.nio.charset.StandardCharsets.UTF_8;

public class ModuleAnalyseTest {

    private ParseSymbolTable export(FModule m) throws IOException {
        var buf = new BufferOutputStream();
        try (var osw = new OutputStreamWriter(buf);
             var w = new BufferedWriter(osw)) {
            new MetaDataExtractor(m, w).write();
        }
        var save = ModuleParserTest.getDir()
                .resolve(m.path().toPath()).resolve("feng.meta");
        Files.copy(buf.read(), save, StandardCopyOption.REPLACE_EXISTING);
        System.out.println(save);

        var src = new SourceParser(m.path(), UTF_8, true)
                .parse("buffer", CharStreams.fromStream(buf.read()));
        return src.table();
    }

    public static DAGGraph<FModule> analyseModule() {
        var m = ModuleParserTest.parseModule();
        new ModuleAnalysis().analyse(m);
        return m;
    }

    @Test
    public void testModule() throws IOException {
        var dag = analyseModule();
        for (var fm : dag) {
            if (ModuleParserTest.isTestPkg(fm.path()))
                export(fm);
        }
    }

    public static DAGGraph<FModule> analysePackage() throws IOException {
        var dag = ModuleParserTest.parsePackage();
        new ModuleAnalysis().analyse(dag);
        return dag;
    }

    @Test
    public void testPackage() throws IOException {
        var dag = analysePackage();
        for (var fm : dag) {
            if (ModuleParserTest.isTestPkg(fm.path()))
                export(fm);
        }
    }

    @Test
    public void testLibrary() throws IOException {
        var dag = ModuleParserTest.withLibrary();
        new ModuleAnalysis().analyse(dag);
    }
}
