package org.cossbow.feng.ast;

import org.cossbow.feng.ast.dcl.Variable;

import java.util.ArrayList;
import java.util.List;

public interface Scope {

    List<Variable> stack();

    void stack(List<Variable> variables);

}
