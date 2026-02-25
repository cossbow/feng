package org.cossbow.feng.ast.stmt;

import org.cossbow.feng.ast.Entity;
import org.cossbow.feng.ast.MayNeedRelay;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.dcl.Variable;
import org.cossbow.feng.util.Lazy;

import java.util.ArrayList;
import java.util.List;

abstract
public class Statement extends Entity
        implements MayNeedRelay {
    public Statement(Position pos) {
        super(pos);
    }

    private final List<Variable> relay = new ArrayList<>();

    public List<Variable> relay() {
        return relay;
    }

    public void relay(Variable v) {
        relay.add(v);
    }
}
