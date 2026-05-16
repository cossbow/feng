package org.cossbow.feng.ast.dcl;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.TypeDefinition;

/**
 * The internal type of the compiler cannot be defined syntactically.
 * <p>
 * When the symbol of an operand points to a defined type, that type
 * will be created to represent it. Currently, it is only used for
 * enumerated types.
 * <p>
 * For example, a defined enumeration: {@code enum State{S1,S2,}}
 * <p>
 * Using the value {@code S1}: {@code var s = State.S1;}
 * <p>
 * Or iterate over all its values: {@code for (s : State) {}}
 */
public class DefinitionDeclarer extends TypeDeclarer {
    private final TypeDefinition def;

    public DefinitionDeclarer(Position pos,
                              TypeDefinition def) {
        super(pos);
        this.def = def;
    }

    public TypeDefinition def() {
        return def;
    }

    //

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof DefinitionDeclarer t))
            return false;
        return def.equals(t.def);
    }

    @Override
    public int hashCode() {
        return def.hashCode();
    }

    //

    @Override
    public String toString() {
        return def.toString();
    }
}
