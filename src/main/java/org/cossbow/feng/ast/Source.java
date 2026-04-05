package org.cossbow.feng.ast;

import org.cossbow.feng.ast.mod.ModulePath;
import org.cossbow.feng.parser.ParseSymbolTable;

import java.util.List;

public class Source extends Entity {
    private List<ModulePath> imports;
    private ParseSymbolTable table;

    public Source(Position pos,
                  List<ModulePath> imports,
                  ParseSymbolTable table) {
        super(pos);
        this.imports = imports;
        this.table = table;
    }

    public List<ModulePath> imports() {
        return imports;
    }

    public ParseSymbolTable table() {
        return table;
    }
}
