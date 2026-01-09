package org.cossbow.feng.parser;

import org.antlr.v4.runtime.CharStreams;
import org.cossbow.feng.ast.FModule;
import org.cossbow.feng.ast.Source;
import org.cossbow.feng.util.Constants;
import org.cossbow.feng.util.ErrorUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class ModuleParser {

    private FModule parse(List<Path> list) throws IOException {
        var table = new ParseSymbolTable();
        var sources = new ArrayList<Source>(list.size());
        for (var f : list) {
            try (var is = Files.newInputStream(f)) {
                var fn = f.getFileName().toString();
                if (!fn.endsWith(Constants.SRC_EXT)) continue;
                var cs = CharStreams.fromStream(is);
                var pr = new SourceParser(fn, table).parse(cs);
                if (!pr.errors().isEmpty())
                    ErrorUtil.syntax("parse error: %s", pr.errors());
                sources.add(pr.root());
            }
        }
        return new FModule(sources, table);
    }

    public FModule parse(Path base, Path module) throws IOException {
        var fp = base.resolve(module);
        try (var ls = Files.list(fp)) {
            var list = ls.filter(Files::isRegularFile).toList();
            return parse(list);
        }
    }

}
