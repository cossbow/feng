package org.cossbow.feng.ast.mod;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.stmt.DeclarationStatement;

public class GlobalDeclaration extends Global {
    private DeclarationStatement statement;

    public GlobalDeclaration(Position pos,
                             boolean export,
                             DeclarationStatement statement) {
        super(pos, export);
        this.statement = statement;
    }

    public DeclarationStatement statement() {
        return statement;
    }
}
