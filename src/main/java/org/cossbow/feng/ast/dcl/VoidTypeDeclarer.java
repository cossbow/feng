package org.cossbow.feng.ast.dcl;

import org.cossbow.feng.ast.Position;

/**
 * 非用于创建AST，仅用于语义分析
 */
public class VoidTypeDeclarer extends TypeDeclarer {
    public VoidTypeDeclarer(Position pos) {
        super(pos);
    }

    @Override
    public boolean equals(Object o) {
        return false;
    }
}
