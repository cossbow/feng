package org.cossbow.feng.ast;

import org.cossbow.feng.ast.gen.TypeParameters;
import org.cossbow.feng.ast.proc.Prototype;

public interface Method {

    Prototype prototype();

    TypeParameters generic();

}
