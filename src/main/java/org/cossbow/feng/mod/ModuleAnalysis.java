package org.cossbow.feng.mod;

import org.antlr.v4.runtime.CharStreams;
import org.cossbow.feng.analysis.AnalyseSymbolTable;
import org.cossbow.feng.analysis.GlobalSymbolContext;
import org.cossbow.feng.analysis.AnonFuncNormalizer;
import org.cossbow.feng.analysis.Monomorphization;
import org.cossbow.feng.analysis.RelayLowering;
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
        // Collect analyzed module tables for cross-module monomorphization
        var analyzedTables = new HashMap<ModulePath, AnalyseSymbolTable>();
        for (var m : modules) {
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
                    m.table(), context).analyse();
            ast.module.set(m);

            // AST lowering: insert temporary variables for
            // lifetime safety before code generation
            new RelayLowering(ast).lower();

            // Normalize anonymous function types to named prototypes
            // so backends don't need to discover them at code-gen time
            new AnonFuncNormalizer(ast).normalize();

            // Monomorphization: discover all concrete generic instantiations
            // and record them in the symbol table.
            // Pass already-analyzed module tables so cross-module generic
            // function definitions can be found.
            var monoImports = new HashMap<ModulePath, AnalyseSymbolTable>();
            for (var i : m.imports()) {
                var analyzed = analyzedTables.get(i);
                if (analyzed != null) monoImports.put(i, analyzed);
            }
            new Monomorphization(ast, monoImports.isEmpty() ? null : monoImports).run();

            m.result.set(ast);
            analyzedTables.put(m.path(), ast);

            // TODO：暂时不导入元数据
            // TODO：暂时使用源码做元数据
//            var pst = buildMetadata(m);
        }
        ;
    }

    public AnalyseSymbolTable analyse(FModule module) {
        var context = new GlobalSymbolContext(module.table());
        var ast = new SemanticAnalysis(
                module.table(), context)
                .analyse();
        ast.module.set(module);

        // AST lowering: insert temporary variables for
        // lifetime safety before code generation
        new RelayLowering(ast).lower();

        // Normalize anonymous function types to named prototypes
        new AnonFuncNormalizer(ast).normalize();

        // Monomorphization: discover all concrete generic instantiations
        // and record them in the symbol table
        new Monomorphization(ast).run();

        module.result.set(ast);
        return ast;
    }
}
