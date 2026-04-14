package org.cossbow.feng.mod;

import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.Source;
import org.cossbow.feng.ast.mod.FModule;
import org.cossbow.feng.ast.mod.ModulePath;
import org.cossbow.feng.dag.DAGGraph;
import org.cossbow.feng.dag.DAGUtil;
import org.cossbow.feng.parser.ParseSymbolTable;
import org.cossbow.feng.parser.SourceParser;
import org.cossbow.feng.util.Constants;
import org.cossbow.feng.util.DedupCache;
import org.cossbow.feng.util.Groups;
import org.cossbow.feng.util.Optional;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import static org.cossbow.feng.util.ErrorUtil.modFail;
import static org.cossbow.feng.util.ErrorUtil.unsupported;

public class ModuleParser {
    private final Identifier pkg;
    private final Path base;
    private final Charset charset;

    public ModuleParser(String pkg, Path base, Charset charset) {
        this.pkg = new Identifier(pkg);
        this.base = base;
        this.charset = charset;
    }

    private Path absPath(Path relPath) {
        return base.resolve(relPath);
    }

    public FModule parseFile(Path file) {
        var mp = new ModulePath(pkg, Path.of(""));
        var src = new SourceParser(mp, charset)
                .parse(absPath(file));
        if (src.imports().isEmpty())
            return new FModule(mp, List.of(), src.table());
        return unsupported("import library");
    }

    private List<Source> parseFiles(ModulePath mp, List<Path> files) {
        var parser = new SourceParser(mp, charset);
        var sources = new ArrayList<Source>(files.size());
        for (Path file : files) {
            sources.add(parser.parse(file));
        }
        return sources;
    }

    private FModule mergeFiles(
            Path path, ModulePath mp, List<Source> list) {
        var table = new ParseSymbolTable(Optional.of(mp), new DedupCache<>());
        var paths = new DedupCache<ModulePath>();
        for (var src : list) {
            table.merge(src.table());
            for (var i : src.imports()) {
                var imp = paths.dedup(i);
                if (!mp.equals(imp))
                    continue;

                return modFail(
                        "cyclic import detected '%s': %s", i, i.pos());
            }
        }
        return new FModule(mp, paths.toList(), table);
    }

    /**
     * 解析指定模块
     * @param module 模块名称
     */
    public FModule parseModule(Path module) {
        try (var ls = Files.list(absPath(module))) {
            var files = ls.filter(Constants.srcTest()).toList();
            var mp = new ModulePath(pkg, module);
            var sources = parseFiles(mp, files);
            return mergeFiles(module, mp, sources);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public DAGGraph<FModule> scanAndParse() {
        var list = scanModule();
        var map = new LinkedHashMap<ModulePath, FModule>();
        var modules = new ArrayList<FModule>(list.size());
        for (var g2 : list) {
            var mp = new ModulePath(pkg, g2.a());
            var sources = parseFiles(mp, g2.b());
            var fm = mergeFiles(g2.a(), mp, sources);
            modules.add(fm);
            map.put(fm.path(), fm);
        }
        var edges = new ArrayList<Groups.G2<FModule, FModule>>();
        for (var fm : modules) {
            for (var i : fm.imports()) {
                var im = map.get(i);
                if (im == null) {
                    modFail("module '%s' not found: %s",
                            i, i.pos());
                }
                edges.add(Groups.g2(im, fm));
            }
        }
        return DAGUtil.make(map.values(), edges);
    }

    private List<Groups.G2<Path, List<Path>>> scanModule() {
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
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return result;
    }

}
