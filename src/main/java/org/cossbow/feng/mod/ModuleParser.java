package org.cossbow.feng.mod;

import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.Source;
import org.cossbow.feng.ast.mod.FModule;
import org.cossbow.feng.ast.mod.ModulePath;
import org.cossbow.feng.dag.DAGGraph;
import org.cossbow.feng.dag.DAGUtil;
import org.cossbow.feng.parser.ParseSymbolTable;
import org.cossbow.feng.parser.SourceParser;
import org.cossbow.feng.util.*;
import org.cossbow.feng.util.Optional;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.cossbow.feng.util.ErrorUtil.modFail;

public class ModuleParser {
    private final Identifier pkg;
    private final Path base;
    private final Charset charset;
    // dependencies of libs has been solved outside
    private final Map<Identifier, ModuleParser> libs;

    public ModuleParser(String pkg, Path base, Charset charset,
                        Map<Identifier, ModuleParser> libs) {
        this.pkg = new Identifier(pkg);
        this.base = base;
        this.charset = charset;
        this.libs = libs;
    }

    public ModuleParser(String pkg, Path base, Charset charset) {
        this(pkg, base, charset, Map.of());
    }

    public Identifier pkg() {
        return pkg;
    }

    private Path absPath(Path relPath) {
        return base.resolve(relPath);
    }

    private final Map<ModulePath, FModule> libCache = new HashMap<>();

    private FModule loadLib(ModulePath mp) throws IOException {
        var fm = libCache.get(mp);
        if (fm != null) return fm;
        fm = parseOneModule(mp.toPath());
        libCache.put(mp, fm);
        return fm;
    }

    private FModule searchLib(ModulePath mp) throws IOException {
        var parser = libs.get(mp.pkg());
        if (parser != null) {
            return parser.loadLib(mp);
        }
        return ErrorUtil.modFail("can't find module '%s'", mp);
    }

    public DAGGraph<FModule> parseFile(Path file) throws IOException {
        var mp = new ModulePath(pkg, Path.of(""));
        var src = new SourceParser(mp, charset)
                .parse(absPath(file));
        var fm = new FModule(mp, src.imports(), src.table());
        return makeGraph(List.of(fm));
    }

    private List<Source> parseFiles(ModulePath mp, List<Path> files) {
        var parser = new SourceParser(mp, charset);
        var sources = new ArrayList<Source>(files.size());
        for (Path file : files) {
            sources.add(parser.parse(file));
        }
        return sources;
    }

    private FModule mergeFiles(ModulePath mp, List<Source> list) {
        var table = new ParseSymbolTable(Optional.of(mp), new DedupCache<>());
        var paths = new DedupCache<ModulePath>();
        for (var src : list) {
            table.merge(src.table());
            for (var i : src.imports()) {
                var imp = paths.dedup(i);
                if (!mp.equals(imp))
                    continue;

                return modFail(
                        "can't import self '%s': %s", i, i.pos());
            }
        }
        return new FModule(mp, paths.toList(), table);
    }

    private FModule parseModuleFiles(Path module, List<Path> files) {
        var mp = new ModulePath(pkg, module);
        var sources = parseFiles(mp, files);
        return mergeFiles(mp, sources);
    }

    private FModule parseOneModule(Path module) throws IOException {
        try (var ls = Files.list(absPath(module))) {
            var files = ls.filter(Constants.srcTest()).toList();
            return parseModuleFiles(module, files);
        }
    }

    /**
     * parse one module
     */
    public DAGGraph<FModule> parseModule(Path module)
            throws IOException {
        var fm = parseOneModule(module);
        return makeGraph(List.of(fm));
    }

    /**
     * parse all modules of whole package
     */
    public DAGGraph<FModule> parsePackage() throws IOException {
        var list = scanModule();
        var modules = new ArrayList<FModule>(list.size());
        for (var g : list) {
            var fm = parseModuleFiles(g.a(), g.b());
            modules.add(fm);
        }
        return makeGraph(modules);
    }

    private DAGGraph<FModule> makeGraph(List<FModule> list)
            throws IOException {
        var modules = CommonUtil.toMap(list, FModule::path);
        for (var fm : list) {
            collectLibs(modules, fm.imports());
        }
        var edges = new ArrayList<Groups.G2<FModule, FModule>>();
        for (var fm : modules.values()) {
            for (ModulePath i : fm.imports()) {
                edges.add(Groups.g2(modules.get(i), fm));
            }
        }
        return DAGUtil.make(modules.values(), edges);
    }

    private void collectLibs(Map<ModulePath, FModule> modules,
                             List<ModulePath> imports)
            throws IOException {
        for (var i : imports) {
            if (modules.containsKey(i)) continue;
            var im = searchLib(i);
            modules.put(im.path(), im);
            collectLibs(modules, im.imports());
        }
    }

    private List<Groups.G2<Path, List<Path>>> scanModule()
            throws IOException {
        var result = new ArrayList<Groups.G2<Path, List<Path>>>();
        var q = new ArrayDeque<Path>();
        q.add(base);
        while (!q.isEmpty()) {
            var dir = q.poll();
            try (var l = Files.list(dir)) {
                var list = l.toList();
                List<Path> files = new ArrayList<>();
                for (var it : list) {
                    if (Files.isDirectory(it)) {
                        q.add(it);
                    } else if (Files.isRegularFile(it) &&
                            Constants.isSource(it)) {
                        files.add(it);
                    }
                }
                if (files.isEmpty()) continue;

                result.add(Groups.g2(base.relativize(dir), files));
            }
        }
        return result;
    }

}
