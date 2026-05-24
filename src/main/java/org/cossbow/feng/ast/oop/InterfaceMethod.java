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

/**
 * Unlike {@link ClassMethod}, {@link InterfaceMethod} only have
 * prototype declarations and do not include implementation.
 */
public class InterfaceMethod extends Method {
    /**
     * {@link ClassMethod#modifier}
     */
    private final Modifier modifier;
    /**
     * {@link ClassMethod#name}
     */
    private final Identifier name;
    /**
     * {@link ClassMethod#generic}
     */
    private final TypeParameters generic;
    /**
     * {@link ClassMethod#escaped}
     */
    private final boolean escaped;
    /**
     * {@link ClassMethod#unmodifiable}
     */
    private final boolean unmodifiable;
    /**
     * {@link ClassMethod#prototype}
     */
    private final Prototype prototype;
    /**
     * {@link ClassMethod#returnThis}
     */
    private final boolean returnThis;

    public InterfaceMethod(Position pos,
                           Modifier modifier,
                           Identifier name,
                           TypeParameters generic,
                           boolean escaped,
                           boolean unmodifiable,
                           Prototype prototype,
                           boolean returnThis) {
        super(pos);
        this.modifier = modifier;
        this.name = name;
        this.generic = generic;
        this.escaped = escaped;
        this.unmodifiable = unmodifiable;
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

    public boolean escaped() {
        return escaped;
    }

    public boolean unmodifiable() {
        return unmodifiable;
    }

    public Prototype prototype() {
        return prototype;
    }

    public boolean returnThis() {
        return returnThis;
    }

    //

    /**
     * Which interface this method belong to
     */
    private InterfaceDefinition master;
    /**
     * Recorded all implementations of this method
     */
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
