package org.cossbow.feng.ast.oop;

import org.cossbow.feng.ast.Entity;
import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.Method;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.attr.Modifier;
import org.cossbow.feng.ast.gen.TypeParameters;
import org.cossbow.feng.ast.proc.Prototype;
import org.cossbow.feng.util.Lazy;

public class InterfaceMethod extends Entity implements Method {
    private Modifier modifier;
    private Identifier name;
    private TypeParameters generic;
    private Prototype prototype;
    private boolean returnThis;

    public InterfaceMethod(Position pos,
                           Modifier modifier,
                           Identifier name,
                           TypeParameters generic,
                           Prototype prototype,
                           boolean returnThis) {
        super(pos);
        this.modifier = modifier;
        this.name = name;
        this.generic = generic;
        this.prototype = prototype;
        this.returnThis = returnThis;
    }

    public Modifier modifier() {
        return modifier;
    }

    public Identifier name() {
        return name;
    }

    public TypeParameters generic() {
        return generic;
    }

    public Prototype prototype() {
        return prototype;
    }

    public boolean returnThis() {
        return returnThis;
    }

    private transient Lazy<InterfaceDefinition> master = Lazy.nil();

    public Lazy<InterfaceDefinition> master() {
        return master;
    }
}
