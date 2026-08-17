package org.cossbow.feng.ast.stmt;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.expr.Expression;
import org.cossbow.feng.ast.gen.GenericMap;

import java.util.ArrayList;
import java.util.List;

public class SwitchBranch extends Branch {
    private List<Expression> constants;

    public SwitchBranch(Position pos,
                        List<Expression> constants,
                        BlockStatement body) {
        super(pos, body);
        this.constants = constants;
    }

    public List<Expression> constants() {
        return constants;
    }

    public void constants(List<Expression> constants) {
        this.constants = constants;
    }

    //

    @Override
    public SwitchBranch mirror() {
        var c = new ArrayList<Expression>(constants.size());
        for (var e : constants) c.add(e.mirror());
        return new SwitchBranch(pos(), c, body().mirror());
    }

    @Override
    public SwitchBranch mono(GenericMap gm) {
        var c = new ArrayList<Expression>(constants.size());
        for (var e : constants) c.add(e.mono(gm));
        return new SwitchBranch(pos(), c, body().mono(gm));
    }
}
