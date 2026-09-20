package org.cossbow.feng.ast.cmp;

import org.cossbow.feng.ast.Entity;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.gen.GenericMap;

/**
 * 编译期布尔表达式（{@code static if} 的 {@code staticExpr}）。
 * <p>
 * 三态组合：{@code NOT} / {@code AND} / {@code OR}，叶子为类型判断：
 * <ul>
 *   <li>{@code A <: C}（{@link TypeIncludedExpr}）：{@link org.cossbow.feng.ast.dcl.TypeDeclarer}
 *       是否满足约束；</li>
 *   <li>{@code A == B}（{@link TypeEqualsExpr}）：两侧类型结构化相等。</li>
 * </ul>
 * <p>
 * 求值分两阶段：
 * <ul>
 *   <li>分析期：{@link #check()} 求值并校验语义（static if 的所有分支都需分析检查，
 *       死分支同样报错）。无类型变量（{@link #hasTypeVar()} 为 {@code false}）的表达式
 *       在分析期即为常量，static if 直接折叠；</li>
 *   <li>mono 期：{@link #mono(GenericMap)} 映射类型变量后再 {@link #check()} 统一裁决。</li>
 * </ul>
 */
abstract
public class StaticExpr extends Entity {
    public StaticExpr(Position pos) {
        super(pos);
    }

    /**
     * 用 {@code gm} 映射表达式（mono 期裁决前调用）。
     * 映射后表达式中的类型变量应全部被替换。
     */
    abstract public StaticExpr mono(GenericMap gm);

    /**
     * 表达式是否为常量：涉及的类型是否含类型变量。
     * <p>
     * 无类型变量的表达式在分析期即可求值（{@link #check()}），
     * 此时 {@code static if} 可直接折叠；含类型变量的表达式
     * 留待 mono 期映射后统一裁决。
     */
    abstract public boolean hasTypeVar();

    /**
     * 求值：表达式是否为真。
     */
    abstract public boolean check();

}
