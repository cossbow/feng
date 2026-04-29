package org.cossbow.feng.ast;

import org.cossbow.feng.ast.gen.TypeParameters;
import org.cossbow.feng.ast.oop.ClassDefinition;
import org.cossbow.feng.ast.oop.ClassMethod;
import org.cossbow.feng.ast.proc.Prototype;
import org.cossbow.feng.util.Lazy;

import java.util.List;
import java.util.function.Consumer;

abstract
public class Method extends Entity {

    public Method(Position pos) {
        super(pos);
    }

    abstract
    public Identifier name();

    abstract
    public Prototype prototype();

    abstract
    public TypeParameters generic();

    abstract
    public TypeDefinition master();

    abstract
    public List<ClassMethod> override();

    public void seeOverride(Consumer<ClassMethod> user) {
        for (var m : override()) {
            user.accept(m);
            m.seeOverride(user);
        }
    }

    public boolean unmodifiable() {
        return false;
    }
}
