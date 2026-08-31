package org.cossbow.feng.analysis;

import org.cossbow.feng.ast.*;
import org.cossbow.feng.ast.attr.Attribute;
import org.cossbow.feng.ast.attr.Modifier;
import org.cossbow.feng.ast.dcl.Primitive;
import org.cossbow.feng.ast.gen.*;
import org.cossbow.feng.ast.micro.MacroTable;
import org.cossbow.feng.ast.oop.InterfaceDefinition;
import org.cossbow.feng.ast.type.*;
import org.cossbow.feng.util.Optional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.cossbow.feng.ast.Position.ZERO;

public class ConceptTest {

    private Identifier identifier(String id) {
        return new Identifier(id);
    }

    private Symbol symbol(String id) {
        return new Symbol(identifier(id));
    }

    private Attribute attr(String name) {
        return new Attribute(symbol(name));
    }

    private InterfaceDefinition emptyDef(Symbol s) {
        return new InterfaceDefinition(ZERO, Modifier.empty(), s,
                TypeParameters.empty(), new IdentifierMap<>(), new SymbolMap<>(),
                new MacroTable());
    }

    /**
     * 构造「具名具体类型」约束：类型 {@code name} 上挂 {@code attrs} 指定的 attribute。
     * 具体类型的属性是确定事实 —— 有 → {@link Tri#YES}，无 → {@link Tri#NO}。
     */
    private DefinedTypeConstraint definedType(String name, String... attrs) {
        var s = symbol(name);
        var def = emptyDef(s);
        for (var n : attrs) {
            var a = attr(n);
            def.modifier().attributes().add(a.type(), a);
        }
        var dt = new DerivedType(ZERO, s, TypeArguments.EMPTY);
        dt.def(def);
        return new DefinedTypeConstraint(ZERO, dt);
    }

    @Test
    public void testAttr1() {
        TypeConstraint tc;
        tc = new AttributeTypeConstraint(ZERO, attr("S1"));
        Assertions.assertSame(Tri.YES, tc.hasAttr(symbol("S1")));
        Assertions.assertSame(Tri.BOTH, tc.hasAttr(symbol("S2")));
        tc = new ExcludeTypeConstraint(ZERO, tc);
        Assertions.assertSame(Tri.NO, tc.hasAttr(symbol("S1")));
        Assertions.assertSame(Tri.BOTH, tc.hasAttr(symbol("S2")));
    }

    @Test
    public void testAttr2() {
        var tc = definedType("A", "S1");
        Assertions.assertSame(Tri.YES, tc.hasAttr(symbol("S1")));
        Assertions.assertSame(Tri.NO, tc.hasAttr(symbol("S2")));
    }

    @Test
    public void testAttr3() {
        var ac = new AttributeTypeConstraint(ZERO, attr("S1"));
        var def = emptyDef(symbol("A"));
        var dt = new DerivedType(ZERO, symbol("A"), TypeArguments.EMPTY);
        dt.def(def);
        var dc = new DefinedTypeConstraint(ZERO, dt);
        {
            var tc = new BinaryTypeConstraint(ZERO, TypeOperator.AND,
                    ac, dc);
            Assertions.assertSame(Tri.BOTH, tc.hasAttr(symbol("S1")));
            Assertions.assertSame(Tri.NO, tc.hasAttr(symbol("S2")));
        }
        {
            var tc = new BinaryTypeConstraint(ZERO, TypeOperator.AND,
                    dc, ac);
            Assertions.assertSame(Tri.BOTH, tc.hasAttr(symbol("S1")));
            Assertions.assertSame(Tri.NO, tc.hasAttr(symbol("S2")));
        }
    }

    @Test
    public void testAttr4() {
        var ac = new AttributeTypeConstraint(ZERO, attr("S1"));
        var dc = new DomainTypeConstraint(ZERO, TypeDomain.CLASS);
        {
            var tc = new BinaryTypeConstraint(ZERO, TypeOperator.AND,
                    ac, dc);
            Assertions.assertSame(Tri.YES, tc.hasAttr(symbol("S1")));
            Assertions.assertSame(Tri.BOTH, tc.hasAttr(symbol("S2")));
        }
        {
            var tc = new BinaryTypeConstraint(ZERO, TypeOperator.AND,
                    dc, ac);
            Assertions.assertSame(Tri.YES, tc.hasAttr(symbol("S1")));
            Assertions.assertSame(Tri.BOTH, tc.hasAttr(symbol("S2")));
        }
    }

    // ──────────────── hasAttr：无信息叶子约束 ────────────────

    @Test
    public void testAttr5() {
        // 域 / 引用性 / 可空 / 不可变维度都不断言 attribute → 无信息 BOTH
        var s1 = symbol("S1");
        Assertions.assertSame(Tri.BOTH,
                new DomainTypeConstraint(ZERO, TypeDomain.CLASS).hasAttr(s1));
        Assertions.assertSame(Tri.BOTH,
                new DomainTypeConstraint(ZERO, TypeDomain.INTERFACE).hasAttr(s1));
        Assertions.assertSame(Tri.BOTH,
                new ReferTypeConstraint(ZERO).hasAttr(s1));
        Assertions.assertSame(Tri.BOTH,
                new OptionalTypeConstraint(ZERO).hasAttr(s1));
        Assertions.assertSame(Tri.BOTH,
                new UnmodifiableTypeConstraint(ZERO).hasAttr(s1));
    }

    @Test
    public void testAttr6() {
        // 原始类型是值类型，永不带 attribute → NO
        var pt = new PrimitiveType(ZERO, identifier("int32"), Primitive.INT32);
        var tc = new DefinedTypeConstraint(ZERO, pt);
        Assertions.assertSame(Tri.NO, tc.hasAttr(symbol("S1")));
        Assertions.assertSame(Tri.NO, tc.hasAttr(symbol("S2")));
    }

    @Test
    public void testAttr7() {
        // 无约束类型变量 = 全集 → BOTH
        var free = new TypeParameter(ZERO, identifier("T"),
                Optional.empty(), false);
        Assertions.assertSame(Tri.BOTH, free.hasAttr(symbol("S1")));
        Assertions.assertSame(Tri.BOTH,
                new DefinedTypeConstraint(ZERO, new GenericType(ZERO, free))
                        .hasAttr(symbol("S1")));

        // 类型变量约束 @S1：投影委托其自身约束
        var p1 = new TypeParameter(ZERO, identifier("T"),
                Optional.empty(), false);
        p1.constraint(new AttributeTypeConstraint(ZERO, attr("S1")));
        Assertions.assertSame(Tri.YES, p1.hasAttr(symbol("S1")));
        Assertions.assertSame(Tri.BOTH, p1.hasAttr(symbol("S2")));
        var d1 = new DefinedTypeConstraint(ZERO, new GenericType(ZERO, p1));
        Assertions.assertSame(Tri.YES, d1.hasAttr(symbol("S1")));
        Assertions.assertSame(Tri.BOTH, d1.hasAttr(symbol("S2")));

        // 类型变量约束 !@S1
        var p2 = new TypeParameter(ZERO, identifier("T"),
                Optional.empty(), false);
        p2.constraint(new ExcludeTypeConstraint(ZERO,
                new AttributeTypeConstraint(ZERO, attr("S1"))));
        Assertions.assertSame(Tri.NO, p2.hasAttr(symbol("S1")));
        Assertions.assertSame(Tri.NO,
                new DefinedTypeConstraint(ZERO, new GenericType(ZERO, p2))
                        .hasAttr(symbol("S1")));
    }

    @Test
    public void testAttr8() {
        var ac = new AttributeTypeConstraint(ZERO, attr("S1"));
        // 括号透明委托，可嵌套
        var p1 = new ParenTypeConstraint(ZERO, ac);
        Assertions.assertSame(Tri.YES, p1.hasAttr(symbol("S1")));
        Assertions.assertSame(Tri.BOTH, p1.hasAttr(symbol("S2")));

        var p2 = new ParenTypeConstraint(ZERO, new ParenTypeConstraint(ZERO,
                new ExcludeTypeConstraint(ZERO, ac)));
        Assertions.assertSame(Tri.NO, p2.hasAttr(symbol("S1")));
    }

    @Test
    public void testAttr9() {
        var ac = new AttributeTypeConstraint(ZERO, attr("S1"));

        var c = new Concept(ZERO, false, symbol("C"), ac);
        var cc = new ConceptTypeConstraint(ZERO, c);
        Assertions.assertSame(Tri.YES, cc.hasAttr(symbol("S1")));
        Assertions.assertSame(Tri.BOTH, cc.hasAttr(symbol("S2")));

        // 与域约束 AND：域侧 BOTH 是 meet 单位元，不改变结论
        Assertions.assertSame(Tri.YES,
                new BinaryTypeConstraint(ZERO, TypeOperator.AND, cc,
                        new DomainTypeConstraint(ZERO, TypeDomain.CLASS))
                        .hasAttr(symbol("S1")));

        // 嵌套概念：投影一路委托到最内层表达式
        var cc2 = new ConceptTypeConstraint(ZERO,
                new Concept(ZERO, false, symbol("C2"), cc));
        Assertions.assertSame(Tri.YES, cc2.hasAttr(symbol("S1")));

        // 概念表达式可后续替换，投影随之翻转
        c.expr(new ExcludeTypeConstraint(ZERO, ac));
        Assertions.assertSame(Tri.NO, cc.hasAttr(symbol("S1")));
        Assertions.assertSame(Tri.NO, cc2.hasAttr(symbol("S1")));
        Assertions.assertSame(Tri.BOTH, cc.hasAttr(symbol("S2")));
    }

    // ──────────────── hasAttr：二元组合 ────────────────

    @Test
    public void testAttr10() {
        var s1 = symbol("S1");
        var s2 = symbol("S2");
        var ac = new AttributeTypeConstraint(ZERO, attr("S1"));
        var with = definedType("D1", "S1");     // 确定带 @S1
        var without = definedType("D2");        // 确定不带任何 attribute

        // OR 取投影并 join
        // YES | YES = YES
        Assertions.assertSame(Tri.YES,
                new BinaryTypeConstraint(ZERO, TypeOperator.OR,
                        ac, ac).hasAttr(s1));
        // YES | NO = BOTH（集合中既有带 @S1 的，也有不带的）
        Assertions.assertSame(Tri.BOTH,
                new BinaryTypeConstraint(ZERO, TypeOperator.OR,
                        ac, without).hasAttr(s1));
        // 与顺序无关
        Assertions.assertSame(Tri.BOTH,
                new BinaryTypeConstraint(ZERO, TypeOperator.OR,
                        without, ac).hasAttr(s1));
        // NO | NO = NO（两侧都确定不带 @S2）
        Assertions.assertSame(Tri.NO,
                new BinaryTypeConstraint(ZERO, TypeOperator.OR,
                        with, without).hasAttr(s2));
        // YES | BOTH = BOTH
        Assertions.assertSame(Tri.BOTH,
                new BinaryTypeConstraint(ZERO, TypeOperator.OR, with,
                        new DomainTypeConstraint(ZERO, TypeDomain.CLASS))
                        .hasAttr(s1));
    }

    @Test
    public void testAttr11() {
        var s1 = symbol("S1");
        var s2 = symbol("S2");
        var ac = new AttributeTypeConstraint(ZERO, attr("S1"));
        var with = definedType("D1", "S1");
        var without = definedType("D2");

        // AND 取投影交 meet
        // YES & YES = YES
        Assertions.assertSame(Tri.YES,
                new BinaryTypeConstraint(ZERO, TypeOperator.AND,
                        ac, with).hasAttr(s1));
        // YES & NO 保守归 BOTH（YES⊓NO 理论为 ∅，投影层交由检查阶段）
        Assertions.assertSame(Tri.BOTH,
                new BinaryTypeConstraint(ZERO, TypeOperator.AND,
                        ac, without).hasAttr(s1));
        // NO & NO = NO
        Assertions.assertSame(Tri.NO,
                new BinaryTypeConstraint(ZERO, TypeOperator.AND,
                        with, without).hasAttr(s2));

        // 嵌套 AND：逐层取 meet，(NO & NO) & NO = NO
        var innerAnd = new BinaryTypeConstraint(ZERO, TypeOperator.AND,
                with, without);
        Assertions.assertSame(Tri.NO,
                new BinaryTypeConstraint(ZERO, TypeOperator.AND,
                        innerAnd, without).hasAttr(s2));

        // 混合嵌套：(YES | NO) & YES = meet(BOTH, YES) = YES
        var or = new BinaryTypeConstraint(ZERO, TypeOperator.OR, ac, without);
        Assertions.assertSame(Tri.YES,
                new BinaryTypeConstraint(ZERO, TypeOperator.AND,
                        or, with).hasAttr(s1));

        // 混合嵌套：(YES & YES) | BOTH = join(YES, BOTH) = BOTH
        var and = new BinaryTypeConstraint(ZERO, TypeOperator.AND, ac, with);
        Assertions.assertSame(Tri.BOTH,
                new BinaryTypeConstraint(ZERO, TypeOperator.OR, and,
                        new DomainTypeConstraint(ZERO, TypeDomain.CLASS))
                        .hasAttr(s1));
    }

    @Test
    public void testAttr12() {
        var s1 = symbol("S1");
        var ac = new AttributeTypeConstraint(ZERO, attr("S1"));
        var with = definedType("D1", "S1");
        var without = definedType("D2");

        // 补集取反投影：具体类型的属性是确定事实，取反即翻转
        Assertions.assertSame(Tri.NO,
                new ExcludeTypeConstraint(ZERO, with).hasAttr(s1));
        Assertions.assertSame(Tri.YES,
                new ExcludeTypeConstraint(ZERO, without).hasAttr(s1));
        // !BOTH = BOTH（无信息取反仍无信息）
        Assertions.assertSame(Tri.BOTH,
                new ExcludeTypeConstraint(ZERO,
                        new DomainTypeConstraint(ZERO, TypeDomain.CLASS))
                        .hasAttr(s1));
        // !!C = C
        Assertions.assertSame(Tri.YES,
                new ExcludeTypeConstraint(ZERO,
                        new ExcludeTypeConstraint(ZERO, ac)).hasAttr(s1));
        // !(YES & YES) = !YES = NO
        Assertions.assertSame(Tri.NO,
                new ExcludeTypeConstraint(ZERO,
                        new BinaryTypeConstraint(ZERO, TypeOperator.AND,
                                ac, with)).hasAttr(s1));
        // !(YES & NO) = !BOTH = BOTH
        Assertions.assertSame(Tri.BOTH,
                new ExcludeTypeConstraint(ZERO,
                        new BinaryTypeConstraint(ZERO, TypeOperator.AND,
                                ac, without)).hasAttr(s1));
        // 括号透明：!(@S1) 与 !@S1 一致
        Assertions.assertSame(Tri.NO,
                new ExcludeTypeConstraint(ZERO,
                        new ParenTypeConstraint(ZERO, ac)).hasAttr(s1));
    }

    // ──────────────── Tri 投影格（hasAttr 组合的基元） ────────────────

    private static final Tri[][] MEET = {
            // 右操作数: YES      NO        BOTH
            /* YES  */ {Tri.YES, Tri.BOTH, Tri.YES},
            /* NO   */ {Tri.BOTH, Tri.NO, Tri.NO},
            /* BOTH */ {Tri.YES, Tri.NO, Tri.BOTH},
    };

    private static final Tri[][] JOIN = {
            /* YES  */ {Tri.YES, Tri.BOTH, Tri.BOTH},
            /* NO   */ {Tri.BOTH, Tri.NO, Tri.BOTH},
            /* BOTH */ {Tri.BOTH, Tri.BOTH, Tri.BOTH},
    };

    @Test
    public void testTriMeetJoinNot() {
        var vs = Tri.values();
        // 下列矩阵的行列序依赖枚举声明序，先钉住
        Assertions.assertArrayEquals(
                new Tri[]{Tri.YES, Tri.NO, Tri.BOTH}, vs);

        for (int i = 0; i < vs.length; i++) {
            var a = vs[i];
            Assertions.assertSame(a, Tri.meet(a, a));
            Assertions.assertSame(a, Tri.join(a, a));
            Assertions.assertSame(a, Tri.not(Tri.not(a)));
            // BOTH 是 meet 的单位元、join 的吸收元
            Assertions.assertSame(a, Tri.meet(a, Tri.BOTH));
            Assertions.assertSame(Tri.BOTH, Tri.join(a, Tri.BOTH));
            for (int j = 0; j < vs.length; j++) {
                var b = vs[j];
                Assertions.assertSame(MEET[i][j], Tri.meet(a, b),
                        "meet(" + a + ", " + b + ")");
                Assertions.assertSame(JOIN[i][j], Tri.join(a, b),
                        "join(" + a + ", " + b + ")");
                // 交换律
                Assertions.assertSame(Tri.meet(a, b), Tri.meet(b, a));
                Assertions.assertSame(Tri.join(a, b), Tri.join(b, a));
                // 吸收律 meet(a, join(a, b)) = a
                Assertions.assertSame(a, Tri.meet(a, Tri.join(a, b)));
            }
        }

        Assertions.assertSame(Tri.NO, Tri.not(Tri.YES));
        Assertions.assertSame(Tri.YES, Tri.not(Tri.NO));
        Assertions.assertSame(Tri.BOTH, Tri.not(Tri.BOTH));
    }

}
