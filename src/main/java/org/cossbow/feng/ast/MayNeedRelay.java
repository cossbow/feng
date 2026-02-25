package org.cossbow.feng.ast;

import org.cossbow.feng.ast.dcl.Variable;

import java.util.List;

public interface MayNeedRelay {

    List<Variable> relay();

    void relay(Variable v);

}
