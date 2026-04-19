package org.cossbow.feng.ast.oop;

import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.Method;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.attr.Modifier;
import org.cossbow.feng.ast.gen.TypeParameters;
import org.cossbow.feng.ast.proc.Prototype;
import org.cossbow.feng.util.CommonUtil;

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

    private InterfaceDefinition master;
    private final List<ClassMethod> override = new ArrayList<>();

    public InterfaceDefinition master() {
        return CommonUtil.required(master);
    }

    public void master(InterfaceDefinition master) {
        this.master = CommonUtil.required(master);
    }

    public List<ClassMethod> override() {
        return override;
    }

    //
    @Override
    public String toString() {
        if (master == null)
            return name.value() + generic + prototype;
        return master.symbol() + "." +
                name + generic + prototype;
    }
}
