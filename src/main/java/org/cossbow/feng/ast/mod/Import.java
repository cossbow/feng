package org.cossbow.feng.ast.mod;

import org.cossbow.feng.ast.Entity;
import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.Position;

import java.util.List;

public class Import extends Entity {
    private final List<Identifier> package_;
    private final List<ImportSymbol> symbols;

    public Import(Position pos,
                  List<Identifier> package_,
                  List<ImportSymbol> symbols) {
        super(pos);
        this.package_ = package_;
        this.symbols = symbols;
    }

    public List<Identifier> package_() {
        return package_;
    }

    public List<ImportSymbol> symbols() {
        assert !symbols.isEmpty();
        return symbols;
    }

    public boolean importAll() {
        return symbols.isEmpty();
    }
}
