package org.cossbow.feng.ast;

import org.cossbow.feng.ast.mod.Global;
import org.cossbow.feng.ast.mod.Import;

import java.util.List;

public class Source extends Entity {
    private final List<Import> imports;
    private final List<Global> globals;

    public Source(Position pos,
                  List<Import> imports,
                  List<Global> globals) {
        super(pos);
        this.imports = imports;
        this.globals = globals;
    }

    public List<Import> imports() {
        return imports;
    }

    public List<Global> definitions() {
        return globals;
    }
}
