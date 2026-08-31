# 约束集合包含判定 — `TypeConstraint.include(TypeConstraint)`

> 状态：设计定稿
> 前置文档：`generic-constraint-design.md`（约束集合代数）、`generic-constraint-projection.md`（维度投影）
> 触发场景：`output/sem.feng` —— 泛型实参为**类型变量**时，`contains(TypeDeclarer)` 无法证明
> `Val.contains(T)` / `Val.contains(S)`，因为实参不是具体类型，而是另一个约束集合。

## 1. 问题

约束是一个**类型集合**。现有 `contains(TypeDeclarer t)` 判定「具体类型 t 是否属于集合」，
但实参绑定时（`GenericMap` → `checkConstraint` → `TypeParameter.match` → `constraint.contains(t)`），
实参可能是**同胞类型变量**（`GenericTypeDeclarer`）：

```
concept Val = primitive | struct | union | enum;
class R`U Val` { var u U; }
func g`T Val`(r R`T`) {}     // 检查 U.match(T)：Val.contains(T)
func h`S int`(r R`S`) {}     // 检查 U.match(S)：Val.contains(S)
```

`T` 的约束集合是 `Val`，`S` 的约束集合是 `int`。直觉上：

- `T`：`Val ⊆ Val` → 满足；
- `S`：`int ⊆ Val`（int 是 primitive，primitive 是 Val 的子域）→ 满足。

但 `contains(TypeDeclarer)` 的实参维度只认具体类型：

- `DomainTypeConstraint.contains` 中 `domainOf(GenericTypeDeclarer) == null` → false；
- `DefinedTypeConstraint.contains` 的 `DerivedType` 分支要求 `DerivedTypeDeclarer`；
- `Refer/Optional/Unmodifiable` 看 `maybeRefer`（类型变量无 `*`/`?`/`#` 修饰时为空）。

**结论**：需要约束集合之间的**包含关系**判定：
`this.include(tc)` ⟺ tc 集合 ⊆ this 集合（任何满足 tc 的类型也满足 this）。

## 2. 语义与定位

### 2.1 定义

```
TypeConstraint.include(TypeConstraint tc) : boolean
```

`include` 是 `contains(TypeDeclarer)` 在**约束集合**维度的推广：

```
this.include(tc)  ⟺  ∀t ∈ Types:  tc.contains(t) ⟹ this.contains(t)
```

与 `contains(TypeDeclarer)` 的换算：

- 具体类型 `t` 可视为单例约束 `{t}`（`DefinedTypeConstraint`），
  `this.contains(t) ⟺ this.include(单例约束(t))`；
- 类型变量 `T`（约束 `C_T`）表示集合 `C_T`，
  `this.contains(T) ⟺ this.include(C_T)`（`C_T` 存在时）。

### 2.2 定位

`include` 只做**判定**，不做投影；不产生新视图，不影响成员查找 / `new` / `default` 路径。
检查仍在分析期：`contains(TypeDeclarer)` 的实参分支遇到 `GenericTypeDeclarer` 时转入 `include`。

### 2.3 正确性契约

**sound（不允许误放行）**：`include` 返回 true 必须保证包含关系成立。
允许**不完整**（false 阴性）：无法证明时返回 false，保持既有拒绝行为。

## 3. 算法理论

### 3.1 剥壳（normalize）

入口先剥壳，再比较，消除语法包装的干扰：

```
normalize(c):
  while c 是 ParenTypeConstraint  : c = c.child()        // 括号
        c 是 ConceptTypeConstraint : c = c.concept().expr() // 概念展开
  return c
```

概念表达式是 DAG（`analyse(IdentifierMap<Concept>)` 已建图、拒绝自引用），展开终止。
**基例**：`normalize(tc).equals(this)` → true（同一集合）。
这是 `Val ⊆ Val` 的命中点：`Val` 展开为 `primitive|struct|union|enum` 后与自身相等。

### 3.2 逐类推导

#### `ParenTypeConstraint` / `ConceptTypeConstraint`

委托：`child.include(tc)` / `concept.expr().include(tc)`。

#### `DomainTypeConstraint`（单域 `d`）

利用「域」维度投影 `domains()`（可能域的**上近似**，见 3.3）：

```
this.domains().containsAll(tc.domains())
```

即 `tc` 中任何类型的域都属于 `{d}` ⟹ 全部命中本域。空集（矛盾约束）空真。

#### `BinaryTypeConstraint`

```
AND（this = A & B）：left.include(tc) && right.include(tc)   // 子集⊆交 ⟺ 分别⊆两侧
OR （this = A | B）：left.include(tc) || right.include(tc)   // 子集⊆并：⊆任一侧即可（不完整但不失 sound）
```

OR 的不完整性示例：`tc = A|B` 时 `(A|B).include(A|B)` 由基例（equals）命中；
`tc = A|C` 且 `C ⊈ B` 时无法分解，保守 false。

#### `DefinedTypeConstraint`

按 `definedType` 三种形态：

| 形态 | 集合语义 | include |
|---|---|---|
| `PrimitiveType p` | `{p}` | 仅剥壳后 equals（`{p}` 的子集只能是 `{p}` 或空） |
| `GenericType U` | `C_U`（无约束=全集） | `C_U` 存在 → `C_U.include(tc)`；无约束 → true |
| `DerivedType dt` | `{dt} ∪ 其子类型` | tc 为单类型约束（`DefinedTypeConstraint` 且 `DerivedType d2`）→ 复用 `contains(d2.declarer(...))`（含 `supers()` 继承链）；否则保守 false |

单类型约束降维复用 `contains(TypeDeclarer)`，避免重复实现继承判定。

#### `ExcludeTypeConstraint`（补集 `!A`）

`tc ⊆ !A` 一般不可证（补集无上近似），剥壳 equals 除外，保守 false。

#### `ReferTypeConstraint`（`*`）

利用「引用性」维度投影 `referenced()`（三态，见 3.3）：

```
tc.referenced() == Tri.YES → true   // 必为引用 ⟹ 全部命中 *any
```

#### `OptionalTypeConstraint` / `UnmodifiableTypeConstraint` / `AttributeTypeConstraint`

可空/不可变/属性维度没有可用的上近似投影（`referenced`/`domains` 之外无对应查询），
剥壳 equals 除外，保守 false。

### 3.3 投影的上近似性（sound 依据）

`include` 用到两个既有投影，需确认它们给出的是**上近似**（超集），才不误放行：

- `domains()`：每种类型有唯一域。`AND` 取交集、`OR` 取并集、`!C` 取补集、叶子精确、
  `GenericType` 委托自身约束 —— 任一实现都满足「∀t ∈ 集合, domainOf(t) ∈ domains()」。
  因此 `domains()` 是可能域的超集，`DomainTypeConstraint.include` 用其判定子集 sound。
- `referenced()`：`YES` = 集合中每个元素都是引用（下界语义，直接可作充分条件），
  `ReferTypeConstraint.include` 用 `== YES` sound；`BOTH/NO` 无法证明 → false。

### 3.4 配套：`contains(TypeDeclarer)` 的类型变量分支

每个 `contains(TypeDeclarer)` 顶部统一增加（委托到 include）：

```
if (t instanceof GenericTypeDeclarer gtd):
    oc = gtd.param().constraint()
    return !oc.none() && include(oc.get())
```

- `oc.none()`（无约束的类型变量 = 全集）→ false：全集 ⊄ 任何非全集约束；
- 有约束 → `include(C_T)`，走 §3.2 的集合推导。

**例外**：`DefinedTypeConstraint` 的 `GenericType` 分支保留 param 恒等特判在顶部——
同一类型变量（`R`U`` 中 U 对 U 自身）即使无约束也恒真，避免回归：

```
if (definedType 是 GenericType gt && t 是 GenericTypeDeclarer gtd):
    if gtd.type().param().equals(gt.param()) → true
    else → 按 §3.4 委托 include（C_U 存在时 C_U.include(C_T)）
```

## 4. 程序结构

```
TypeConstraint (abstract)
 ├─ abstract boolean include(TypeConstraint tc)          // 新增
 ├─ protected static TypeConstraint normalize(TypeConstraint c)  // 新增：剥壳（Paren/Concept）
 └─ boolean contains(TypeDeclarer t)                     // 各子类顶部加 GenericTypeDeclarer 分支

DefinedTypeConstraint  : include 按 §3.2 三形态；contains 的 GenericType 分支改造
DomainTypeConstraint   : include = domains 上近似子集判定
BinaryTypeConstraint   : include = AND 分解 / OR 分解 + 基例
ConceptTypeConstraint  : include = 委托 expr
ParenTypeConstraint    : include = 委托 child
ExcludeTypeConstraint  : include = 基例，否则 false
ReferTypeConstraint    : include = referenced()==YES
OptionalTypeConstraint : include = 基例，否则 false
UnmodifiableTypeConstraint : include = 基例，否则 false
AttributeTypeConstraint    : include = 基例，否则 false
```

## 5. 示例推演

```
Val.contains(T)，T 的约束 = Val（ConceptTypeConstraint）：
  contains → 类型变量分支 → Val.include(Val)
  → normalize(Val) = (primitive|struct|union|enum) → equals(this) → true ✔

Val.contains(S)，S 的约束 = int（DefinedTypeConstraint PrimitiveType）：
  contains → 类型变量分支 → Val.include(int)
  → Concept 委托 → (A|B|C|D).include(int) → OR → DomainTypeConstraint(primitive).include(int)
  → int.domains()={PRIMITIVE} ⊆ {PRIMITIVE} → true ✔

Val.contains(X)，X 无约束：
  contains → 类型变量分支 → oc.none() → false ✘（保守拒绝，正确）
```

## 6. 测试计划（SemanticAnalyzerTest）

| # | 场景 | 断言 |
|---|---|---|
| 66 | 概念同约束同胞参数（`T:Val` 对 `U:Val`，sem.feng 原例） | checkSucc |
| 67 | 概念子集约束同胞参数（`S:int` 对 `U:Val`） | checkSucc |
| 68 | 无约束类型变量对概念约束 | checkFail |
| 69 | 域约束 vs 具体 primitive（`int` 对 `primitive` 域） | checkSucc |
| 70 | 概念自引用（`Val ⊆ Val`）经 Binary OR 基例 | checkSucc |
| 71 | 类继承单类型包含（子类约束对父类约束） | checkSucc / checkFail 对照 |

## 7. 已知边界

- `include` 允许 false 阴性：OR 无法分解、补集、可空/不可变/属性维度一律保守拒绝；
- 同胞参数相互约束的循环引用不做额外防护（与既有 `contains` 递归行为一致，声明处已拒自引用）；
- 概念表达式展开依赖概念 DAG 无环（`makeDAG` 保证）。

## 8. 覆盖验证（2026-09-19，SemanticAnalyzerTest 447/447）

Jacoco 分支覆盖结论：**新增代码（`include` / `contains` / `normalize` / `containsByConstraint`）的所有可达分支均覆盖**。
测试 66-80 覆盖矩阵：

| 方法 | 分支覆盖 | 说明 |
|---|---|---|
| `TypeConstraint.containsByConstraint` | 6/6 | 类型变量约束委托 + 无约束全集 |
| `TypeConstraint.normalize` | 3/4 | 唯一未覆盖 = Paren 分支（见下） |
| `DefinedTypeConstraint.contains` | 27/28 | 唯一未覆盖 = L77 兜底（见下） |
| `DefinedTypeConstraint.include` | 15/16 | 唯一未覆盖 = L115 兜底（见下） |
| `BinaryTypeConstraint.contains` | 15/16 | 唯一未覆盖 = L70 `referenced`（旧代码） |
| `BinaryTypeConstraint.include` | 12/12 | AND/OR 分解 + equals 基例全覆盖 |
| `Domain/Concept/Exclude/Optional/Unmodifiable/Attribute.contains` | 全覆盖 | 含标记作二元操作数的实参/类型变量两侧 |
| `ReferTypeConstraint.include` | 3/4 | 唯一未覆盖 = equals 基例（见下） |
| `ParenTypeConstraint.contains/include` | 0 | 整类不可达（见下） |

**不可达分支（防御性代码，非缺陷）**：

1. `normalize` 的 Paren 分支：`analyse(TypeConstraint)` 在语义分析期**急切剥壳**（`case ParenTypeConstraint pc -> analyse(pc.child())`），
   Paren 节点分析后不存活 → `include` 经 `normalize` 永不见 Paren。概念展开同理只到 Concept 分支。
2. `ParenTypeConstraint.contains/include` 整类：同上，无 Paren 约束能存活到运行时判定。
3. `DefinedTypeConstraint.contains` L77 / `include` L115 的「非 DerivedType」兜底：
   `DefinedType` 仅 3 个子类（Primitive/Generic/Derived），L65/L70（contains）与 L102/L111（include）
   已排除前两者，到达时必为 DerivedType → 兜底分支不可达（为未来新增 DefinedType 子类保留的防御）。
4. 标记类约束（`Refer/Optional/Unmodifiable/Attribute`）`include` 的 equals 基例：
   `checkMarker` 拒绝裸标记作为约束（`X *` / 概念 expr 均报错），标记只能作二元操作数，
   因此没有任何约束能 `normalize` 成裸标记 → equals 恒 false。`ReferTypeConstraint.include` L29 的 `return true` 即此分支。

若未来允许裸标记约束（放宽 `checkMarker`），上述分支将变为可达，需补测试。
