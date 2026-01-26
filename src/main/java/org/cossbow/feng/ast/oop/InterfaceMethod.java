package org.cossbow.feng.ast.oop;

import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.Method;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.attr.Modifier;
import org.cossbow.feng.ast.gen.TypeParameters;
import org.cossbow.feng.ast.proc.Prototype;
import org.cossbow.feng.util.Lazy;

import java.util.ArrayList;
import java.util.List;

public class InterfaceMethod extends Method {
    private final Modifier modifier;
    private final Identifier name;
    private final TypeParameters generic;
    private final Prototype prototype;
    private final boolean returnThis;

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
    private final List<ClassMethod> impls = new ArrayList<>();

    public Lazy<InterfaceDefinition> master() {
        return master;
    }

    public List<ClassMethod> impls() {
        return impls;
    }
}
