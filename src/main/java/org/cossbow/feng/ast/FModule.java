package org.cossbow.feng.ast;

import org.cossbow.feng.parser.ParseSymbolTable;

import java.util.List;

public class FModule  {
    private List<Source> sources;
    private ParseSymbolTable table;

    public FModule(List<Source> sources,
                   ParseSymbolTable table) {
        this.sources = sources;
        this.table = table;
    }

    public List<Source> sources() {
        return sources;
    }

}
