package org.cossbow.feng.mod;

import org.antlr.v4.runtime.CharStreams;
import org.cossbow.feng.ast.mod.FModule;
import org.cossbow.feng.parser.ParseSymbolTable;
import org.cossbow.feng.parser.SourceParser;
import org.cossbow.feng.util.BufferOutputStream;
import org.cossbow.feng.util.Constants;
import org.cossbow.feng.util.ResourceUtil;
import org.junit.jupiter.api.Test;

import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import static java.nio.charset.StandardCharsets.UTF_8;

public class ModuleAnalyseTest {

    private ParseSymbolTable export(FModule m) throws Exception {
        var buf = new BufferOutputStream();
        try (var osw = new OutputStreamWriter(buf);
             var w = new BufferedWriter(osw)) {
            new MetaDataExtractor(m, w).write();
        }
        var save = m.dir().resolve(Constants.META);
        Files.copy(buf.read(), save, StandardCopyOption.REPLACE_EXISTING);
        System.out.println(save);

        var src = new SourceParser(m.path(), UTF_8, true)
                .parse("buffer", CharStreams.fromPath(save));
        return src.table();
    }

    @Test
    public void testSingle() throws Exception {
        var dir = ResourceUtil.getDir("mod");
        var md = dir.resolve("aaa");
        var m = new ModuleParser(dir, UTF_8).parseModule(md);
        new ModuleAnalysis().analyse(m);
        export(m);
    }

    @Test
    public void testAnalysis() {
        var dag = new ModuleParser(
                ResourceUtil.getDir("mod"), UTF_8)
                .scanAndParse();
        new ModuleAnalysis().analyse(dag);
    }

}
