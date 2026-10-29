package org.cossbow.feng.ast.expr;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.attr.Modifier;
import org.cossbow.feng.ast.dcl.TypeDeclarer;
import org.cossbow.feng.ast.dcl.Variable;
import org.cossbow.feng.ast.gen.GenericMap;
import org.cossbow.feng.ast.gen.TypeParameter;
import org.cossbow.feng.ast.oop.InterfaceDefinition;
import org.cossbow.feng.ast.proc.Procedure;
import org.cossbow.feng.ast.proc.Prototype;

import java.util.ArrayList;
import java.util.List;

/**
 * Anonymous class definition, written as {@code class(){} }.
 * <p>
 * Unlike a function lambda, a class lambda can capture outer local
 * variables, and its target type must be a single-method interface.
 * At lowering time it is converted into an anonymous class.
 * <p>
 * Analysis-phase products (captures, type variables, target interface,
 * inferred prototype) are filled in during semantic analysis.
 */
public class ClassLambdaExpression extends PrimaryExpression {
    private final Modifier modifier;
    private final Procedure procedure;

    public ClassLambdaExpression(Position pos,
                                 Procedure procedure) {
        this(pos, Modifier.empty(), procedure);
    }

    public ClassLambdaExpression(Position pos,
                                 Modifier modifier,
                                 Procedure procedure) {
        super(pos);
        this.modifier = modifier;
        this.procedure = procedure;
    }

    public Modifier modifier() {
        return modifier;
    }

    public Procedure procedure() {
        return procedure;
    }

    //
    // Analysis-phase products
    //

    /**
     * 捕获集：lambda 体引用的外层局部变量（非本 lambda 声明的变量）。
     * 匿名类生成时转为字段。
     */
    private List<Variable> captures = List.of();
    /**
     * 提升的类型变量集：lambda 引用到的外层类型变量（P6 泛型提升）。
     */
    private List<TypeParameter> typeVars = List.of();
    /**
     * 目标单方法接口（分析期已解析 def）。
     */
    private InterfaceDefinition targetIface;
    /**
     * 推断原型：来自接口唯一方法。
     */
    private Prototype inferredPrototype;
    /**
     * 目标接口的类型（DerivedTypeDeclarer，P4 合成匿名类 impl 用）。
     */
    private TypeDeclarer targetType;

    public List<Variable> captures() {
        return captures;
    }

    public void captures(List<Variable> captures) {
        this.captures = captures;
    }

    public List<TypeParameter> typeVars() {
        return typeVars;
    }

    public void typeVars(List<TypeParameter> typeVars) {
        this.typeVars = typeVars;
    }

    public InterfaceDefinition targetIface() {
        return targetIface;
    }

    public void targetIface(InterfaceDefinition targetIface) {
        this.targetIface = targetIface;
    }

    public Prototype inferredPrototype() {
        return inferredPrototype;
    }

    public void inferredPrototype(Prototype inferredPrototype) {
        this.inferredPrototype = inferredPrototype;
    }

    public TypeDeclarer targetType() {
        return targetType;
    }

    public void targetType(TypeDeclarer targetType) {
        this.targetType = targetType;
    }

    @Override
    public ClassLambdaExpression mirror() {
        return this;
    }

    @Override
    public ClassLambdaExpression mono(GenericMap gm) {
        return this;
    }

}
