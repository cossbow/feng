package org.cossbow.feng.mod;

import org.antlr.v4.runtime.CharStreams;
import org.cossbow.feng.ast.mod.FModule;
import org.cossbow.feng.parser.ParseSymbolTable;
import org.cossbow.feng.parser.SourceParser;
import org.cossbow.feng.util.BufferOutputStream;
import org.cossbow.feng.util.ErrorUtil;
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

        var file = m.path().toPath().resolve("feng.meta");
        var save = ModuleParserTest.getDir().resolve(file);
        Files.copy(buf.read(), save, StandardCopyOption.REPLACE_EXISTING);
        System.out.println(save);

        var src = new SourceParser(m.path(), UTF_8, true)
                .parse(file.toString(), CharStreams.fromStream(buf.read()));
        return src.table();
    }

    ModuleManager analyseModule() {
        var m = ModuleParserTest.parseModule();
        var ma = new ModuleAnalysis();
        ma.analyse(m);
        ErrorUtil.reportError(ma.errors());
        return m;
    }

    @Test
    public void testModule() throws IOException {
        var m = analyseModule();
        for (var fm : m.dag()) {
            if (ModuleParserTest.isTestPkg(fm.path()))
                export(fm);
        }
    }

    public static ModuleManager analysePackage() throws IOException {
        var m = ModuleParserTest.parsePackage();
        var ma = new ModuleAnalysis();
        ma.analyse(m);
        ErrorUtil.reportError(ma.errors());
        return m;
    }

    @Test
    public void testPackage() throws IOException {
        var m = analysePackage();
        for (var fm : m.dag()) {
            if (ModuleParserTest.isTestPkg(fm.path()))
                export(fm);
        }
    }

    @Test
    public void testLibrary() throws IOException {
        var m = ModuleParserTest.withLibrary();
        var ma = new ModuleAnalysis();
        ma.analyse(m);
        ErrorUtil.reportError(ma.errors());
    }
}
