package org.cossbow.feng.ast.oop;

import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.Optional;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.ProcDefinition;
import org.cossbow.feng.ast.attr.Modifier;
import org.cossbow.feng.ast.gen.TypeParameters;
import org.cossbow.feng.ast.proc.Procedure;

public class ClassMethod extends ProcDefinition {
    private final boolean export;
    private final Procedure procedure;

    public ClassMethod(Position pos,
                       Modifier modifier,
                       Identifier name,
                       TypeParameters generic,
                       boolean export,
                       Procedure procedure) {
        super(pos, modifier, Optional.of(name), generic);
        this.export = export;
        this.procedure = procedure;
    }

    public boolean export() {
        return export;
    }

    public Procedure procedure() {
        return procedure;
    }
}
