# 泛型约束：投影模型设计（取代 TypeView 物化）

> 状态：**设计定稿**（本文件），对应实现尚未落地。
> 前置文档：`generic-constraint-design.md`（已有实现的设计，其 §4「TypeView 四维三态」被本文取代）。
> 本文是 `generic-constraint-design.md` 的演进：它保留了集合代数的约束语法与 `contains()` 判定，
> 但把「物化一个公共特征集 TypeView」改为「按维度分别提取投影，判定式求值」。
> 同时补齐 concept（`HEAD 56bf093`，此前无文档）。

---

## 1. 动机

约束系统同时承担两件事，但它们有本质不同的可计算性：

1. **约束类型参数** —— `contains(TypeDeclarer t)`：对传入的具体类型做成员判定。集合是开放、无限、
   不可数的，所以只能逐类型判定。**这条路是对的。**
2. **提供泛型参数的可用特征** —— `TypeView view()`：试图把约束预先「算成」一个公共特征集，
   供 `*T` / `new(T)` / 成员访问 / default 初始化在实参未绑定时使用。**这条路是盲区。**

盲区的原因：公共特征集并非总是存在、也并非总是可计算。现实现已经用 `unknown()` 作为兜底承认了这一点：

```java
// ExcludeTypeConstraint.view()
return TypeView.unknown();   // 补集无法表示为单一视图
```

但 `unknown()` 把「本可精确判定的有限维度」和「确实无法物化的无界维度」一起丢掉了，
于是出现了 `class Atomic`T !*&Referable``{ var value *T; }` 这类本应放行却报
`only value-type support refer` 的误报。

**本文的结论**：不物化整体，而是把约束投影拆成一个个独立维度，逐维度做**判定式**提取。

---

## 2. 理论基础

### 2.1 约束即集合，特征维度即投影

一个约束 `C` 是类型集合 `C ⊆ Type`（`Type` 为不可数的语义域）。对任意「特征维度」`D`
（引用性、可空性、可变性、域、字段集、方法集…），约束在该维度上形成一个**投影**
（即 `C` 在该维度坐标上能取到哪些值的集合）。

### 2.2 二值维度的幂集：四种子集

对任一**二值**维度（如「引用性」，坐标 `{值, 引用}`），投影是它的子集，一共四种：

| 投影 | 含义 | 对应命名 |
|---|---|---|
| `∅` | 没有任何类型可匹配（无效约束） | —（检查期排查） |
| `{引用}` | 只能是引用 | `YES` / `REF` |
| `{值}` | 只能是值 | `NO` / `VAL` |
| `{值, 引用}` | 两者皆可（等于该维度全集） | `BOTH` |

这是二值集合的幂集 `2^{a,b} = {∅, {a}, {b}, {a,b}}` 的必然结果。

### 2.3 补集与三态

补集定义在**幂集**上（相对该维度全集取补）：

```
¬∅      = {a,b} = BOTH
¬{a}    = {b}
¬{b}    = {a}
¬{a,b}  = ∅          // ¬BOTH = ∅
```

要点：

1. **`¬BOTH = ∅`**（不是 `¬BOTH = BOTH`）。补集只在四元素幂集上闭合；一旦去掉 `∅`，
   三元素集上的补集不闭合——`¬BOTH` 会跑出到 `∅`。
2. 因此 **`∅` 必须在约束检查阶段显式排查**：它表示「该约束无法匹配任何类型」，属于无效约束，
   应报错，而不是让它流到 `contains` / 谓词里被含糊放过。
3. 排查掉 `∅` 之后，`contains` 只剩三种有效情况，于是取三态枚举
   `Tri { YES, NO, BOTH }`（`BOTH` 是“两者皆可”这一**确定的正信息**，不是“未知”）。

> 说明：现实现的 `TypeView` 用 `Boolean` 三态（`null=unknown/false/true`）承载这类投影，
> 但 `unknown` 一词语义含糊——它实际表达的是「要包容的类型过多，导致没有公共特征」，
> 即上述 `BOTH`（全集）。二者应当分离：真正的“不可知”只有「无约束」，而 `BOTH` 是“确定的两者皆可”。

### 2.4 投影与补集不交换（重要不变量）

```
proj(!C) ≠ ¬proj(C)
```

反例（引用性维度 `U={值,引用}`，class 既可值又可引用）：

- `proj(class)  = {值, 引用} = BOTH`            （裸 `class` 是值、`*class` 是引用）
- `¬proj(class) = ¬BOTH = ∅`
- 但 `proj(!class) = {值, 引用} = BOTH`          （非 class 里：struct/enum 是值，interface 是引用）

两边不等（`BOTH ≠ ∅`）。

**推论**：每个维度的「像」必须从**完整约束树整体求值**（连同 `!` 节点），投影放在**最后一步**；
绝不能在维度层面先投影、再独立做补集。否则 `class` 这类跨界维度会被错算成 `∅`。

### 2.5 域维度：有限多值集合，用枚举而非位图

「域」不是二值，而是 8 值枚举 `TypeDomain`。它的投影是 `TypeDomain` 的子集，
用 **`EnumSet`/枚举集合**表达——枚举本身就是有限集，语义清晰；不用位图（位图是底层实现细节，
泄漏到语义层会丢失「哪个位对应哪个域」的可读性）。

---

## 3. 特征维度的分类

| 类别 | 维度 | 值域 | 代数性质 | `!` 补集 |
|---|---|---|---|---|
| 有限二值 | 引用性 | `{值, 引用, BOTH}` | 三态格 | 精确取反 |
| 有限二值 | 可空性 | `{必填, 可空, BOTH}` | 三态格 | 精确取反 |
| 有限二值 | 可变性 | `{可变, 只读, BOTH}` | 三态格 | 精确取反 |
| 有限多值 | 域 | `EnumSet<TypeDomain>` | 有限幂集格 | 精确（整体树层） |
| 无界 | 字段集 / 方法集 / 属性集 | 开放宇宙 | 下近似 | 坍缩为空 |

- **有限维度**：对任意约束（多开放、多不可数、含多少补集），投影像都是有限集的子集，
  **精确可判定**。
- **无界维度**：字段名/方法名/属性名是开放宇宙（任意 `Identifier` 都能作成员名），
  只能求「保证存在」的**下近似**，`!` 下坍缩为空。这是它“无法物化”的数学根源，而不是实现缺陷。

---

## 4. 设计：判定式投影器（取代 `view()`）

### 4.1 接口形态

在 `TypeConstraint` 上，`view()` 移除，改为一组**独立投影器**，每个只回答一个维度：

```java
abstract class TypeConstraint {
    boolean        contains(TypeDeclarer t);   // 不变：点归属判定
    Tri            referenced();               // 有限二值：值 / 引用 / both
    Tri            nullable();
    Tri            unmodifiable();
    EnumSet<TypeDomain> domains();             // 有限多值：域集合（补集在整体树层）
    Optional<FieldSet>    fields();            // 无界：下近似，none = 无公共下近似
    Optional<MethodSet>   methods();
    Optional<AttributeSet> attrs();
}
```

`Tri` 为三态枚举：

```java
enum Tri { YES, NO, BOTH }   // 相对“某二值属性的成立性”而言
```

### 4.2 检查期排查 `∅`

约束检查阶段（`analyse(TypeConstraint)`）遍历整棵树，对每个维度计算投影；若任一维度的投影为 `∅`
（例如 `* & !*`，或域集求交后为空），报告「无效约束」。排查后的 `contains` 只见三态。

### 4.3 各节点求值表（以「引用性」为例）

| 节点 | `referenced()` |
|---|---|
| `*`（Refer） | `YES`（=引用） |
| `!*` | `NO`（=值）；由整体树对 `*` 的投影取补 |
| `class` 域 | `BOTH`（裸=值、`*class`=引用） |
| `interface` 域 | `YES`（interface 只能是引用） |
| `struct`/`union`/`enum`/`primitive` 域 | `NO`（值类型） |
| `A & B` | 按集合交：`referenced(A) ⊓ referenced(B)` |
| `A \| B` | 按集合并：`referenced(A) ⊔ referenced(B)` |
| `!C` | 在整体树层对 `C` 求补后再投影（见 §2.4） |
| concept / 括号 | 委托 |

三态格的 `⊓`（与）与 `⊔`（或）：

```
YES ⊓ BOTH = YES    YES ⊓ NO = ∅（→无效约束）   NO ⊓ BOTH = NO
YES ⊔ BOTH = BOTH   YES ⊔ NO = BOTH              NO ⊔ BOTH = BOTH
```

### 4.4 引用性的三种语法载体

对泛型参数的实际标记规则（这是“不能丢失 BOTH”的正确定义）：

| 约束写法 | 引用性投影 |
|---|---|
| `*T` | 引用（`YES`） |
| `!*T` | 值（`NO`） |
| 无 `*` 且无 `!*` | `BOTH`（两者皆可） |

即：**`*` 是“引用”，`!*` 是“值”，无标记是“两种”**。不要用「裸 class 是值、`*class` 是引用」
这种逐类型定义——它会把 `BOTH` 这个投影丢掉（正是 §2.4 的陷阱）。

### 4.5 使用点替换

`SemanticAnalyzer` 中所有 `param().view().xxx()` 判定点改为相应投影器：

- `analyse(GenericTypeDeclarer)` 的 `*T` 检查：`referenced() == NO`（保证是值类型）；
- `new(T)` 检查：`referenced() == NO && newable()`；
- 成员查找：`fields()/methods()` 下近似替代物化成员表。

`contains()`（实例化处约束校验）**完全不变**——它本就是判定式。

---

## 5. 形态标记的组合规则

`*` / `?` / `#` / `@Attr` 是**修饰符，不是类型**，因此不能作为约束单独出现，必须依附于
「能标识类型的符号」（域或命名类型）才合法。这与其类型语义一致：

| 标记 | 语义 | 依附 | 投影维度 |
|---|---|---|---|
| `*` | 引用 | 任何值类型/class | 引用性 |
| `?` | 可空 | 引用、函数类型 | 可空性 |
| `#` | 只读 | 仅与 `*` 组合（`*#T`） | 可变性 |
| `@Attr` | 携带属性 | 命名类型 | 属性集 |

`# ⇒ *`（值类型没有 unmodifiable 维度，天然成立）；`?` 作用于引用与函数两类
（`!func()` 非空 / `?func()` 可空），载体不同但语义是同一个「required」轴。

---

## 6. concept：命名约束（语法糖）

concept 是**命名的约束表达式**，参与其他约束时结果与直接内嵌一致。

### 6.1 语法（`Feng.g4`）

```
concept : EXPORT? CONCEPT name=Identifier ASSIGN expr=typeConstraint SEMI ;
```

```
concept Referable = class | interface | struct | union | enum;
class Atomic`T !* & Referable` { var value *T; }
```

`Referable` 在约束表达式里出现时，等价于把 `class|interface|struct|union|enum` 内嵌。
因此 `!* & Referable` 的引用性投影 = `referenced(!*) ⊓ referenced(Referable)`，
由 `!*` 一侧（`NO`）精确兜底 —— `*T` 放行，无需理解“interface 不能被值引用”再做额外剔除。

### 6.2 运行模型（对应 `HEAD 56bf093`）

- `Concept`（`ast/type/Concept.java`）：`export` + `symbol` + `expr`。
- `ConceptTypeConstraint`（`ast/type/ConceptTypeConstraint.java`）：
  `contains(t)` 与 `view()` 均委托 `concept.expr()`。
- 解析：`SourceParseVisitor.visitConcept` 定义符号并加入 `ParseSymbolTable.concepts`；
  约束表达式里的 `DefinedType` 若命中 concept 符号，在 `SemanticAnalyzer.analyse(TypeConstraint)`
  中替换为 `ConceptTypeConstraint`。
- 依赖顺序：`analyse(IdentifierMap<Concept>)` 先递归解析各 concept 的 `expr`，
  再经 `conceptDeps` 建 DAG（`makeDAG`），保证 concept 定义先于使用处解析。

### 6.3 设计含义

concept 是“不自动缩小”的配套：用户用 `concept Referable = class|interface|struct|union|enum`
**显式**枚举域集合，编译器不替用户自动推断/剔除；编译器只负责按形态标记（`*`/`?`/`#`/`@Attr`）
检查组合、按投影器回答能力。

---

## 7. 重构路线

| 阶段 | 内容 | 效果 |
|---|---|---|
| 一 | 有限维度投影器 `referenced()/newable()/nullable()/domains()`，替换 `view().isRefer/domain/newable` | 修复 `!*&Referable` 误报；不动成员集 |
| 二 | 成员集下近似 `fields()/methods()/attrs()`，替换 `view().fields/methods` 与 default-init 检查 | 收编无界维度，删除 `TypeView` |
| 三 | 检查期 `∅` 排查 + 形态标记依附校验 | 无效约束报错；`*`/`?`/`#`/`@Attr` 不能单独出现 |

---

## 8. 与 `generic-constraint-design.md` 的主要差异

- `TypeView`（四维三态物化）**删除**，由「独立维度投影器 + `Tri`/`EnumSet`」取代；
- 幂集四值 `{∅, {a}, {b}, {a,b}}` 取代 `Boolean` 三态（`unknown/false/true`），`∅` 显式排查；
- 明确 `¬BOTH = ∅`（纠正此前设想的 `¬BOTH = BOTH`）与「投影补集不交换」不变量；
- concept 首次文档化（此前 `56bf093` 无文档）。