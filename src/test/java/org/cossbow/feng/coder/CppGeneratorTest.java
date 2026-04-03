package org.cossbow.feng.coder;

import org.cossbow.feng.analysis.SemanticAnalysis;
import org.cossbow.feng.mod.ModuleParserTest;
import org.cossbow.feng.parser.ParseSymbolTable;
import org.cossbow.feng.parser.SourceParser;
import org.cossbow.feng.util.Constants;
import org.junit.jupiter.api.Test;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.cossbow.feng.util.CommonUtil.required;

public class CppGeneratorTest {

    static String replaceExt(String name, String ext) {
        var i = name.lastIndexOf('.');
        if (i < 0) return name + '.' + ext;
        return name.substring(0, i + 1) + ext;
    }

    static void analyseAndGenerate(ParseSymbolTable table, String target)
            throws Exception {
        // 分析ast
        new SemanticAnalysis(table, false).analyse();
        // 生成C++代码
        try (var out = new FileOutputStream(target);
             var bo = new BufferedOutputStream(out);
             var w = new OutputStreamWriter(bo)) {
            new CppGenerator(table, w, true).write();
            w.flush();
        }
    }

    static String compileFile(File file) throws Exception {
        var src = file.getName();
        var cpp = replaceExt(src, "cpp");
        var target = "target/cpp/" + cpp;
        System.out.printf("[compile]%s >>> %s\n", src, target);
        // 解析ast
        var parser = new SourceParser(UTF_8, new ParseSymbolTable());
        var source = parser.parse(file.toPath());
        analyseAndGenerate(source.table(), target);
        return target;
    }

    static void compileCpp(String cpp) throws Exception {
        var obj = replaceExt(cpp, "o");
        String[] cmd = {"c++", "--std=c++20", "-c", cpp, "-o", obj};
        var p = Runtime.getRuntime().exec(cmd);
        if (p.waitFor() != 0) {
            try (var es = p.getErrorStream()) {
                var er = new String(es.readAllBytes());
                System.out.println(er);
            }
        }
    }

    @Test
    public void testSampleFile() throws Exception {
        Files.createDirectories(Path.of("target/cpp/"));
        var cl = Thread.currentThread().getContextClassLoader();
        var res = cl.getResource("coder");
        var dir = new File(required(res).getFile());
        for (var file : required(dir.listFiles(Constants.srcFilter()))) {
            var cpp = compileFile(file);
            compileCpp(cpp);
        }
    }

    @Test
    public void testSampleModule() throws Exception {
        var mods = ModuleParserTest.listMod();
        for (var mod : mods) {
            var mp = mod.toPath();
            System.out.printf("======== test module %s ========\n", mp);
            var fm = ModuleParserTest.parseMod(mp);
            var target = mp.resolve("out.cpp");
            analyseAndGenerate(fm.table(), target.toString());

        }
    }
}
