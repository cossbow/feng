package org.cossbow.feng.ast.mod;

import org.cossbow.feng.ast.Source;
import org.cossbow.feng.parser.ParseSymbolTable;

import java.util.List;

public class FModule {
    private ModulePath path;
    private List<Source> sources;
    private ParseSymbolTable table;

    public FModule(ModulePath path,
                   List<Source> sources,
                   ParseSymbolTable table) {
        this.path = path;
        this.sources = sources;
        this.table = table;
    }

    public List<Source> sources() {
        return sources;
    }

    public ParseSymbolTable table() {
        return table;
    }

    //
    @Override
    public String toString() {
        return path.toString();
    }
}
