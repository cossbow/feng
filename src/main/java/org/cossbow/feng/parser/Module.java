package org.cossbow.feng.parser;

import org.cossbow.feng.ast.Source;

import java.util.List;
import java.util.Map;

public class Module {
    private final List<String> path;
    private final Map<String, Source> sources;

    public Module(List<String> path, Map<String, Source> sources) {
        this.path = path;
        this.sources = sources;
    }

    public List<String> path() {
        return path;
    }

    public Map<String, Source> sources() {
        return sources;
    }



}
