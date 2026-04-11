package org.cossbow.feng.mod;

import org.antlr.v4.runtime.CharStreams;
import org.cossbow.feng.analysis.AnalyseSymbolTable;
import org.cossbow.feng.analysis.GlobalSymbolContext;
import org.cossbow.feng.analysis.SemanticAnalysis;
import org.cossbow.feng.ast.mod.FModule;
import org.cossbow.feng.ast.mod.ModulePath;
import org.cossbow.feng.dag.DAGGraph;
import org.cossbow.feng.parser.ParseSymbolTable;
import org.cossbow.feng.parser.SourceParser;
import org.cossbow.feng.util.BufferOutputStream;
import org.cossbow.feng.util.ErrorUtil;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.UTF_8;

public class ModuleAnalysis {

    private ParseSymbolTable buildMetadata(FModule m) {
        var buf = new BufferOutputStream(4096);
        try (var osw = new OutputStreamWriter(buf);
             var w = new BufferedWriter(osw)) {
            new MetaDataExtractor(m, w).write();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        try {
            var src = new SourceParser(m.path(), UTF_8, true)
                    .parse("buffer",
                            CharStreams.fromStream(buf.read()));
            return src.table();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void analyse(DAGGraph<FModule> modules) {
        var tabMap = modules.stream().collect(
                Collectors.toMap(FModule::path, FModule::table));
        modules.bfs(m -> {
            var imports = new HashMap<ModulePath, ParseSymbolTable>(
                    m.imports().size() + 1);
            for (var i : m.imports()) {
                var im = tabMap.get(i);
                if (im.main.has()) {
                    ErrorUtil.semantic("can't import main-module: %s", i, i.pos());
                    return;
                }
                imports.put(i, im);
            }

            var context = new GlobalSymbolContext(imports, m.table());
            var ast = new SemanticAnalysis(
                    m.table(), context, false).analyse();
            ast.module.set(m);
            m.result.set(ast);

            // TODO：暂时不导入元数据
            // TODO：暂时使用源码做元数据
//            var pst = buildMetadata(m);
        });
    }

    public AnalyseSymbolTable analyse(FModule module) {
        var context = new GlobalSymbolContext(module.table());
        var ast = new SemanticAnalysis(
                module.table(), context, false)
                .analyse();
        ast.module.set(module);
        return ast;
    }
}
