package org.cossbow.feng.mod;

import org.antlr.v4.runtime.CharStreams;
import org.cossbow.feng.analysis.*;
import org.cossbow.feng.analysis.meta.ClassMetadata;
import org.cossbow.feng.analysis.meta.CopyBuilder;
import org.cossbow.feng.analysis.meta.VTableBuilder;
import org.cossbow.feng.analysis.mono.Monomorphization;
import org.cossbow.feng.analysis.meta.ReleaserBuilder;
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
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.UTF_8;

public class ModuleAnalysis {
    // In test model
    private final boolean test;
    private final List<String> errors = new ArrayList<>();

    public ModuleAnalysis(boolean test) {
        this.test = test;
    }

    public ModuleAnalysis() {
        this(false);
    }

    public List<String> errors() {
        return errors;
    }

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

    private AnalyseSymbolTable analyse(
            ParseSymbolTable table, GlobalSymbolContext context) {
        var analyzer = new SemanticAnalyzer(
                table, context, test);
        var ast = analyzer.analyse();
        errors.addAll(analyzer.errors());
        return ast;
    }

    public void analyse(DAGGraph<FModule> modules) {
        var tabMap = modules.stream().collect(
                Collectors.toMap(FModule::path, Function.identity()));

        for (var m : modules) {
            var imports = new HashMap<ModulePath, ParseSymbolTable>(
                    m.imports().size() + 1);
            for (var i : m.imports()) {
                var dm = tabMap.get(i);
                if (dm == null) continue;
                var im = dm.table();
                if (im.main.has()) {
                    ErrorUtil.semantic("can't import main-module: %s", i, i.pos());
                    return;
                }
                imports.put(i, im);
            }

            var context = new GlobalSymbolContext(imports, m.table());
            var ast = analyse(m.table(), context);
            ast.module.set(m);


            m.result.set(ast);

            // TODO：暂时不导入元数据
            // TODO：暂时使用源码做元数据
//            var pst = buildMetadata(m);
        }

        normalize(modules);

        mono(modules, tabMap);

        lowering(modules);

        classFlat(modules);

        classVtable(modules);

        releaser(modules);
    }

    private void normalize(DAGGraph<FModule> modules) {
        // Normalize anonymous function types to named prototypes
        // so backends don't need to discover them at code-gen time
        for (FModule fm : modules) {
            new AnonFuncNormalizer(fm.result.must()).normalize();
        }
    }

    private void mono(DAGGraph<FModule> modules,
                      Map<ModulePath, FModule> map) {
        // Cross-module mono instances (definition-site ownership) keyed by path.
        var monoMap = new LinkedHashMap<ModulePath, Monomorphization>();
        for (var fm : modules) {
            // Monomorphization: discover all concrete generic instantiations
            // and record them in the symbol table.
            // Pass already-analyzed module tables so cross-module generic
            // function definitions can be found.
            var mono = new Monomorphization(fm.result.must(), monoMap);
            monoMap.put(fm.path(), mono);
        }
        // Phase 1: run monomorphization on every module. Each run() concretizes
        // types and writes cross-module instances into their *owner* module's
        // concretized set (definition-site ownership). Phase 1 must complete for
        // ALL modules before any buildDeps() runs, otherwise an instance written
        // by a later module (e.g. main's IntPair`bool` owned by aad) is missed by
        // the owner's already-completed bucketing.
        for (var mono : monoMap.values()) {
            mono.run();
        }
        // Phase 2: build dependency graphs after all modules' concretization
        // (including cross-module instances) has been written.
        for (var mono : monoMap.values()) {
            mono.buildDeps();
        }
    }

    private void lowering(DAGGraph<FModule> modules) {
        // AST lowering: insert temporary variables for
        // lifetime safety before code generation
        for (var fm : modules) {
            new RelayLowering(fm.result.must()).lower();
        }
    }

    private void classFlat(DAGGraph<FModule> modules) {
        // Phase 3: assemble class metadata (method → function symbol) after
        // monomorphization, so concrete instances and method-level generic
        // instantiations are all present.
        for (var m : modules) {
            var ast = m.result.must();
            new ClassMetadata(ast).build();
        }
    }

    private void classVtable(DAGGraph<FModule> modules) {
        var tableMap = new LinkedHashMap<ModulePath, AnalyseSymbolTable>();
        for (var fm : modules) {
            tableMap.put(fm.path(), fm.result.must());
        }
        for (var m : modules) {
            var ast = m.result.must();
            new VTableBuilder(ast, tableMap).build();
        }
    }

    private void releaser(DAGGraph<FModule> modules) {
        for (var m : modules) {
            var ast = m.result.must();
            ReleaserBuilder.build(ast);
            // copy 复用 cleanups 已收集的类型键集，须在 releaser 之后执行
            CopyBuilder.build(ast);
        }
    }
}
