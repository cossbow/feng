package org.cossbow.feng.ast.mod;

import org.cossbow.feng.ast.Entity;
import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.Position;

import java.util.List;

public class Import extends Entity {
    private final List<Identifier> module;
    private final boolean all;
    private final List<ImportSymbol> symbols;

    public Import(Position pos,
                  List<Identifier> module,
                  boolean all,
                  List<ImportSymbol> symbols) {
        super(pos);
        this.module = module;
        this.all = all;
        this.symbols = symbols;
    }

    public List<Identifier> module() {
        return module;
    }

    public List<ImportSymbol> symbols() {
        assert !symbols.isEmpty();
        return symbols;
    }

    public boolean importAll() {
        return symbols.isEmpty();
    }
}
