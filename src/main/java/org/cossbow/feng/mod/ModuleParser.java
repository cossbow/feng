package org.cossbow.feng.mod;

import org.cossbow.feng.ast.Source;
import org.cossbow.feng.ast.mod.FModule;
import org.cossbow.feng.ast.mod.ModulePath;
import org.cossbow.feng.dag.DAGGraph;
import org.cossbow.feng.dag.DAGUtil;
import org.cossbow.feng.parser.ParseSymbolTable;
import org.cossbow.feng.parser.SourceParser;
import org.cossbow.feng.util.*;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

public class ModuleParser {
    private final Path base;
    private final Charset charset;

    public ModuleParser(Path base, Charset charset) {
        this.base = base;
        this.charset = charset;
    }

    private Path absPath(Path relPath) {
        return base.resolve(relPath);
    }

    public FModule parseFile(Path file) {
        var name = Constants.trimExt(file.getFileName());
        var mp = new ModulePath(name);
        if (!file.isAbsolute()) {
            file = base.resolve(file);
        }
        var src = new SourceParser(mp, charset).parse(file);
        if (src.imports().isEmpty())
            return new FModule(name, mp, List.of(), src.table());
        return ErrorUtil.unsupported("import library");
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

                return ErrorUtil.modFail(
                        "cyclic import detected '%s': %s", i, i.pos());
            }
        }
        return new FModule(base.resolve(path), mp, paths.toList(), table);
    }

    private FModule parseModule(Path path, List<Path> files) {
        var mp = new ModulePath(path);
        var sources = parseFiles(mp, files);
        return mergeFiles(path, mp, sources);
    }

    public FModule parseModule(Path module) {
        Path path, dir;
        if (module.isAbsolute()) {
            dir = module;
            path = base.relativize(dir);
        } else {
            path = module;
            dir = absPath(path);
        }
        try (var ls = Files.list(dir)) {
            var a = ls.filter(Constants.srcTest()).toList();
            return parseModule(path, a);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public DAGGraph<FModule> scanAndParse() {
        var list = scanModule();
        var map = new LinkedHashMap<ModulePath, FModule>();
        var modules = new ArrayList<FModule>(list.size());
        for (var ms : list) {
            var fm = parseModule(ms);
            modules.add(fm);
            map.put(fm.path(), fm);
        }
        var edges = new ArrayList<Groups.G2<FModule, FModule>>();
        for (var fm : modules) {
            for (var i : fm.imports()) {
                var im = map.get(i);
                edges.add(Groups.g2(im, fm));
            }
        }
        return DAGUtil.make(map.values(), edges);
    }

    private FModule parseModule(ModuleSource ms) {
        var mp = new ModulePath(ms.path);
        var sources = parseFiles(mp, ms.files);
        return mergeFiles(ms.path, mp, sources);
    }

    private List<ModuleSource> scanModule() {
        var projBase = this.base.getParent();
        var projName = this.base.getFileName();
        var result = new ArrayList<ModuleSource>();
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

                result.add(new ModuleSource(projBase.relativize(dir), files));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return result;
    }

    record ModuleSource(Path path, List<Path> files) {
    }
}
