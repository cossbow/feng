package org.cossbow.feng.parser;

import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.Source;
import org.cossbow.feng.ast.mod.FModule;
import org.cossbow.feng.ast.mod.ModulePath;
import org.cossbow.feng.util.Constants;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class ModuleParser {

    private final Charset charset;

    public ModuleParser(Charset charset) {
        this.charset = charset;
    }

    private FModule parse(Path module, List<Path> files) {
        var table = new ParseSymbolTable();
        var sources = new ArrayList<Source>(files.size());
        var parser = new SourceParser(charset, table);
        for (var f : files) {
            var src = parser.parse(f);
            sources.add(src);
        }
        var path = new Identifier[module.getNameCount()];
        for (int i = 0; i < path.length; i++) {
            path[i] = new Identifier(module.getName(i).toString());
        }
        return new FModule(new ModulePath(path), sources, table);
    }

    public FModule parse(Path base, Path module) {
        var fp = base.resolve(module);
        try (var ls = Files.list(fp)) {
            var a = ls.filter(Constants.srcTest()).toList();
            return parse(module, a);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}
