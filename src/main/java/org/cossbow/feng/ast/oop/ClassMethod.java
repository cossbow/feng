package org.cossbow.feng.ast.oop;

import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.Optional;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.attr.Modifier;
import org.cossbow.feng.ast.gen.TypeParameters;
import org.cossbow.feng.ast.proc.FunctionDefinition;
import org.cossbow.feng.ast.proc.Procedure;

public class ClassMethod extends FunctionDefinition {
    private boolean export;

    public ClassMethod(Position pos,
                       Modifier modifier,
                       Identifier name,
                       TypeParameters generic,
                       boolean export,
                       Procedure procedure) {
        super(pos, modifier, Optional.of(name),
                generic, procedure);
        this.export = export;
    }

    public boolean export() {
        return export;
    }

}
