package org.cossbow.feng.parser;

import org.antlr.v4.runtime.CharStreams;
import org.cossbow.feng.ast.Source;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;


public class ModuleParser {

    final SourceParser sourceParser = new SourceParser();

    public Module parse(List<String> path, List<Path> files) throws IOException {
        var sources = new HashMap<String, Source>();
        for (var file : files) {
            var name = file.getFileName().toString();
            try (var is = Files.newInputStream(file)) {
                var result = sourceParser.parse(CharStreams.fromStream(is));
                if (!result.errors().isEmpty()) {
                    throw new ParseException(result.errors());
                }
                sources.put(name, result.root());
            }
        }
        return new Module(path, sources);
    }

}
