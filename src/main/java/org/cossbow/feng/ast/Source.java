package org.cossbow.feng.ast;

import org.cossbow.feng.ast.mod.Global;
import org.cossbow.feng.ast.mod.Import;
import org.cossbow.feng.ast.proc.FunctionDefinition;
import org.cossbow.feng.ast.proc.Procedure;

import java.util.ArrayList;
import java.util.List;

public class Source extends Entity {
    private List<Import> imports;
    private List<Global> globals;

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
