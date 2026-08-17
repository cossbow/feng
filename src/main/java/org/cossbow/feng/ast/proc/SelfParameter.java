package org.cossbow.feng.ast.proc;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.dcl.DerivedTypeDeclarer;
import org.cossbow.feng.ast.dcl.ReferKind;
import org.cossbow.feng.ast.oop.ClassDefinition;

/**
 * This is builtin Parameter
 */
public class SelfParameter extends Parameter {
    private final ClassDefinition master;

    public SelfParameter(Position pos,
                         ClassDefinition master) {
        super(pos);
        this.master = master;
    }

    public ClassDefinition master() {
        return master;
    }

    public DerivedTypeDeclarer type() {
        return master.refer(pos(), ReferKind.PHANTOM);
    }
}
