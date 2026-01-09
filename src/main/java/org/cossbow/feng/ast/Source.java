package org.cossbow.feng.ast;

import org.cossbow.feng.ast.mod.Global;
import org.cossbow.feng.ast.mod.Import;
import org.cossbow.feng.ast.stmt.DeclarationStatement;

import java.util.List;

public class Source extends Entity {
    private List<Import> imports;
    private List<Global> globals;
    private List<Definition> definitions;
    private List<DeclarationStatement> declarations;

    public Source(Position pos,
                  List<Import> imports,
                  List<Definition> definitions,
                  List<DeclarationStatement> declarations) {
        super(pos);
        this.imports = imports;
        this.definitions = definitions;
        this.declarations = declarations;
    }

    public List<Import> imports() {
        return imports;
    }

    public List<Definition> definitions() {
        return definitions;
    }

    public List<DeclarationStatement> declarations() {
        return declarations;
    }

    public List<Global> globals() {
        return globals;
    }
}
