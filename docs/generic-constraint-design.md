# 泛型类型约束系统 — 最终实现设计

> 状态：最终实现（分支 `dev-constraint`，HEAD `996a01a` "Add set-algebra-based constraints for generic types"）
> 前置文档：`type-constraint-roadmap.md`（过程路线图）、`type-init-capability.md`（初始化能力）仅为过程记录，
> 部分描述与最终实现有出入，**本文以当前源码为准**。
>
> **更新（引用三特征值投影融合）**：本文 §3/§4/§5 的引用维度设计已按
> `generic-constraint-refer-projection.md`（引用三特征值投影定稿）更新——
> `GenericTypeDeclarer.refer()` 已由约束投影计算化，
> 并新增决策 D1–D4 与消费点重写（§4.5）。
> 引用维度以新文档为定稿，其余章节不变。

## 1. 设计目标

泛型类型参数（`class Box`T`{...}` / `func f`T`(x T)`）的约束系统：

1. **集合语义**：约束是一个"类型集合"，`contains(TypeDeclarer)` 回答"实参是否属于该集合"；
2. **部分类型**：约束同时投影为多维度信息（引用性 / 可空性 / 不可变性 / 成员集 / newable / 域），
   使泛型参数在分析期可进行成员查找、`new`、字面量初始化、可调用判断——即使实参尚未绑定；
3. **opt-in 许可**：`default` 标记显式开启"以字面量/无参方式构造泛型参数实例"的能力，
   不是自动行为。

```
class Box`T: *any` { var v T; }          // 约束 *any：所有引用类型
class Box`T: A & B` { var v T; }         // 交集：同时满足 A 与 B
func f`T: (Task)`(t T) int { return t(); } // 函数原型约束：T 可调用
func f`T default: (A)`(){ var a T = {}; }  // default：允许字面量初始化
```

## 2. 语法（`Feng.g4`）

```
typeParameter    : name=Identifier DEFAULT? typeConstraint? ;
typeConstraint   : primaryConstraint                       # PrimaryTypeConstraint
                 | '(' typeConstraint ')'                  # ParenTypeConstraint
                 | NOT typeConstraint                      # ExcludeTypeConstraint
                 | l=typeConstraint op=BITAND r=...        # BinaryTypeConstraint
                 | l=typeConstraint op=BITOR r=...         # BinaryTypeConstraint
                 ;
primaryConstraint: typeDomain | MUL | QUESTION | HASH | definedType | AT symbol ;
typeDomain       : CLASS | INTERFACE | ENUM | STRUCT | UNION | ATTRIBUTE | FUNC | PRIMITIVE ;
```

- 优先级（ANTLR 备选顺序）：`primary` > `(C)` > `!C` > `&` > `|`。
- `DEFAULT` 关键字出现在 `typeParameter` 上，与 switch 的 `default` 分属不同上下文。
- 解析入口：`SourceParseVisitor.visitTypeParameter`（`initable = ctx.DEFAULT() != null`）及
  各 `visit*TypeConstraint` 构造约束节点。

## 3. 约束 AST：集合代数（`ast/type/`）

抽象基类 `TypeConstraint`（`ast/type/TypeConstraint.java`）：

- `abstract boolean contains(TypeDeclarer t)` —— 集合成员判定；
- `abstract boolean include(TypeConstraint tc)` —— 集合包含判定（sound，允许不完整）；
- 多维度投影方法族：
  - 有限维度：`Tri referenced()`（引用性）/ `optional()`（可空性）/ `unmodifiable()`（不可变性）/ `newable()`（可构造）/ `EnumSet<TypeDomain> domains()`（域集）;
  - 开放维度（成员名是开放宇宙，只能求"保证存在"的下近似，补集下坍缩为空）：`MemberSet members()`;
  - 特征维度：`hasAttr(Symbol)` / `has(Concept)`（`Tri`）。
- 结构化 `equals`/`hashCode`（约束树可作为 key）。

`Tri` 三态（`ast/type/Tri.java`）：`YES`（属性确定成立）/ `NO`（属性确定不成立）/ `BOTH`（两者皆可，确定的正信息，非"未知"）。
AND 组合 `Tri.meet`、OR 组合 `Tri.join`、补集 `Tri.not`。
`Tri.meet(YES,NO)` 理论上是 ∅（无效约束），在投影层保守归 `BOTH`，由 `checkEmpty` 结构化排查（§6.1）。

### 3.1 九个实现类（`contains` 语义）

| 类 | 语法 | `contains` 语义 |
|---|---|---|
| `DefinedTypeConstraint` | `int` / `Bus` / `T` | ① `PrimitiveType`：原始类型恒等；② `GenericType`：同胞参数恒等，否则按该参数自身约束展开；③ `DerivedType`：定义相等，或经 `ObjectTool.checkInherited` 沿 `supers()` 链子类型匹配 |
| `DomainTypeConstraint` | `class`/`struct`/`enum`/`union`/`interface`/`func`/`attribute`/`primitive` | `domainOf(t) == domain`（primitive→域、Derived→定义域、Func→FUNC） |
| `ReferTypeConstraint` | `*` | `t.maybeRefer().has()` |
| `OptionalTypeConstraint` | `?` | 可空引用（`refer.has() && !required`）**或可空 func 原型**（`FuncTypeDeclarer !required()`，func 非引用，可空性由 `required()` 表达） |
| `UnmodifiableTypeConstraint` | `#` | 不可变引用（`refer.has() && unmodifiable`） |
| `AttributeTypeConstraint` | `@X` | 定义的 `modifier().attributes()` 含 `X` |
| `ExcludeTypeConstraint` | `!C` | 补集：`!operand.contains(t)` |
| `BinaryTypeConstraint` | `A&B` / `A\|B` | AND：两操作数都含；OR：任一含 |
| `ParenTypeConstraint` | `(C)` | 委托子约束 |

> 类型变量分支：实参是 `GenericTypeDeclarer` 时，`contains` 委托 `containsByConstraint`（`include(oc)`），
> 无约束的类型变量 = 全集，不满足任何非全集约束 → false（§3.1 `DefinedTypeConstraint` 的
> `GenericType` 分支另有"同胞参数恒等"短路：`T` 对 `T` 自身恒真）。

子类型匹配（`DefinedTypeConstraint.contains` → `ObjectTool.checkInherited`）：

- 左右必须都是 `DerivedTypeDeclarer` 且定义都是 `ObjectDefinition`（class/interface）；
- 从实参定义沿 `supers()` 深度优先搜索，逐层用 `st.gm().overlay(next)` 累积泛型映射，
  命中即返回该层实例化的 `TypeArguments`（`Optional.has` → 满足）。

### 3.2 `include` 集合包含判定

`this.include(tc)` ⟺ ∀t: `tc.contains(t) ⟹ this.contains(t)`。sound（不允许误放行），允许不完整（false 阴性）。

- `DefinedTypeConstraint`：单类型约束降维复用 `contains`（含 `supers()` 继承链）；`GenericType` 分支委托同胞约束；
- `DomainTypeConstraint`：`domains().containsAll(x.domains())`（域投影是可能域的上近似）；
- `ReferTypeConstraint`：剥壳 equals 之外，`interface` 域 ⊆ 引用域集合（`x.domains()` 非空且全为 `INTERFACE`）→ 命中 `*any`（见 §4.5 决策 D1 连带）；
- `ExcludeTypeConstraint` / `OptionalTypeConstraint` / `UnmodifiableTypeConstraint`：补集/可空/不可变维度无上近似投影，剥壳 equals 除外保守 false；
- `BinaryTypeConstraint`：`&` 两侧都要，`|` 任一侧即可（不完整但不失 sound）。

### 3.3 三特征值投影求值表（引用维度定稿）

> 融合自 `generic-constraint-refer-projection.md` §4.2。引用形态共 5 种组合（§4.3），
> 三个有限维度的投影见下表。`?` / `#` / 域 / 定义类型在引用性维度上统一为 `BOTH`，
> 使 `Tri.not(BOTH)=BOTH` 在补集上自动正确；`*` 保持 `YES` 作为"必引用"的唯一精确信号，
> `!*`（`not(YES)=NO`）作为"必值"的唯一精确信号。

| 约束 | `referenced()` | `optional()` | `unmodifiable()` |
|---|---|---|---|
| `*`（ReferTypeConstraint） | **YES** | BOTH | BOTH |
| `?`（OptionalTypeConstraint） | **BOTH** | **YES** | BOTH |
| `#`（UnmodifiableTypeConstraint） | **BOTH** | BOTH | **YES** |
| `!C`（ExcludeTypeConstraint） | `Tri.not(C.referenced())` | `Tri.not(C.optional())` | `Tri.not(C.unmodifiable())` |
| `A & B` / `A \| B` | meet / join | meet / join | meet / join |
| `(C)` 括号 / concept（ConceptTypeConstraint） | 委托 child / concept | 委托 | 委托 |
| 域约束（DomainTypeConstraint） | **BOTH** | BOTH | BOTH |
| `(A)` 定义类型（DefinedTypeConstraint） | **BOTH**（`GenericType` 同胞分支委托其自身约束） | 同上 | 同上 |
| `@Attr`（AttributeTypeConstraint） | BOTH | BOTH | BOTH |
| Syncable（SyncableTypeConstraint） | BOTH | BOTH | BOTH |

**跨维度取反不精确**：`Tri.not` 只对纯引用性谓词精确（`!*`）；对跨维度谓词
（`?`/`#`/class 等），`¬proj(operand) ≠ proj(!operand)`——`!?`/`!#`/`!A` 的引用性投影
应为 `BOTH` 而非 `not(YES)=NO`。全 `BOTH` 化正是为在补集上自动正确（§4.5 决策 D1）。

## 4. 投影模型：多维度三态投影

### 4.1 模型

约束/类型参数投影为多维度信息，全部经 `TypeConstraint` 的抽象投影方法暴露，**永远不作为
返回值类型**——对查询产出的是 `TypeDeclarer`（字段类型/方法签名）：

| 维度 | 投影 | 取值 |
|---|---|---|
| 引用性 | `referenced()` | `Tri`：YES=必引用，NO=必值，BOTH=可值可引用 |
| 可空性 | `optional()` | `Tri`：YES=必可空，NO=必非空，BOTH=可空可非空 |
| 不可变性 | `unmodifiable()` | `Tri`：YES=必不可变，NO=必可修，BOTH=可修可不可变 |
| 域 | `domains()` | `EnumSet<TypeDomain>`（可能域集合，补集在此维度精确闭合） |
| 成员集 | `members()` | `MemberSet`（字段/方法下近似 + `hasRequiredInit` OR 存活） |
| 可构造 | `newable()` | `Tri`：YES=必可 new，NO=必不可 new，BOTH=不确定 |
| 特征 | `hasAttr()` / `has()` | `Tri`：标注/蕴含判断 |

`MemberSet`（`ast/type/MemberSet.java`）是成员维度的下近似：成员名是开放宇宙，只能求
"保证存在"的公共成员，补集下坍缩为空。AND 组合取并集（同名不同形态剔除）、OR 组合取交集
（`sameView` 判据：字段=类型+immutable+modifier，方法=原型+escaped+unmodifiable+modifier）。

### 4.2 引用三特征值的 5 组合空间 + func 正交

引用形态共 5 种，三个维度的精确投影：

| 形态 | `referenced()` | `optional()` | `unmodifiable()` |
|---|---|---|---|
| 值类型 | NO | NO | NO |
| 必选可修引用 `*` | YES | NO | NO |
| 必选不可变引用 `*#` | YES | NO | YES |
| 可空可修引用 `*?` | YES | YES | NO |
| 可空不可变引用 `*?#` | YES | YES | YES |

func 正交：func 类型不是引用（`FuncTypeDeclarer` 无 `Refer`），但有自己独立的可空轴——
`?F`（可空 func 原型）的 `optional()=YES` 而 `referenced()=NO`，可空性由
`!FuncTypeDeclarer.required()` 表达。因此 `optional()` 维度覆盖两类载体：引用（`!Refer.required()`）
与 func 原型（`!FuncTypeDeclarer.required()`）。

### 4.3 决策 D1：保持全 `BOTH`（不做精确分域）

既有设计主张精确分域（`interface→YES`、`struct/union/enum/primitive→NO`、`class→BOTH`，
`!C` 在整体树层求补后再投影）。当前实现改为全部 `BOTH` + `Tri.not`：

- **一致性**：全 `BOTH` 使 `!C` 补集自动正确（§3.3 跨维度取反不精确的规避）；
- **代价**：丢失 `interface`（应 YES）与 `struct/union/enum/primitive`（应 NO）的精确信号，
  导致 `*any` include 判据（`referenced()==YES`）失效，需改用域判据（§4.5）；
- **定夺**：**保持全 `BOTH`**，消费点判据重建。恢复精确分域并引入"组合形状"枚举
  （每个约束输出 `EnumSet<ComboShape>`，三维投影统一从 shapes 派生，`!C` 在形状空间上求补）
  列为后续可选改进，不阻塞落地。

### 4.4 决策 D2：BOTH 保守收敛在消费点（不在约束层压扁）

`BOTH` 的保守收敛**不在约束层**（`TypeConstraint.optional()`/`unmodifiable()` 保持返回原始 `Tri`），
而在 **`SemanticAnalyzer` 消费点**统一收敛。约束层只负责投影，语义决策（"不确定则按保守方向处理"）
归分析器。

| 投影 | 保守取值 | 收敛位置 | 理由 |
|---|---|---|---|
| `optional()==BOTH` | `required=false`（可空） | `GenericTypeDeclarer.refer()` / 消费点 | 不确定是否 required 就判空（安全收敛：不误当作必选非空，宁可要求判空检查） |
| `unmodifiable()==BOTH` | `unmodifiable=true`（不可修改） | `unmodifiable(Variable)` 消费点 | 不确定是否可修改就判不可修改（安全收敛：不误放行可变操作） |

### 4.5 消费点重写（全 `BOTH` 化后的判据重建）

`referenced()` 的全部消费点仅 3 处，全 `BOTH` 化后全部失效（实测 8 个 `checkSucc` 回归），
必须重建判据：

**`new(T)`**（`analyseNewType`，`SemanticAnalyzer`）：

> 新判据：**`referenced() != YES && newable() == YES`**（非必引用，且值形态可构造）。
> 决策 D3：`? & int` / `# & int` **不拦截**——没有 `*` 标记却带 `?`/`#`，匹配不到任何类型，
> 不产生任何有效类型形态，放行无副作用。

| 用例 | referenced | newable | 新判据 | 期望 |
|---|---|---|---|---|
| `(A)` class / `(E)` enum / `@D & int` / `Val & int` 等 | BOTH | YES | 放行 | ✅ 恢复 |
| `*` | YES | — | 拒 | ✅ |
| `(class)` 域 / `(struct)` 域 | BOTH | BOTH | 拒 | ✅ |
| `(F)` func / `(I)` interface | BOTH | NO | 拒 | ✅ |
| `!int` | BOTH | NO | 拒 | ✅ |
| `* & int` | YES | — | 拒 | ✅ |
| `? & int` / `# & int` | BOTH | **YES** | 放行 | ✅ **决策 D3：不拦截** |

**`*T` 引用检查**（`analyse(GenericTypeDeclarer)`）：

> 决策 D4：无约束 `*T` **不能放行**——无法确定 T 是值类型，就不能标记 `*T`。
> 不能简单改成 `referenced() == YES`（那会把"无约束 BOTH"与"有约束 `(A)` BOTH"一视同仁地放行），
> 必须区分"无约束 BOTH"与"有约束 BOTH"。
> 最终落地判据：**`domains()` 非空且全为"确定值域"**
> （`d != FUNC && d != INTERFACE && d != ATTRIBUTE`，即 class/primitive/struct/union/enum 域）。

| 场景 | domains | 判据 | 期望 |
|---|---|---|---|
| `*T` with `(A)` / `(E)` / `(int)` / `(S)` / `(U)` | 单值域（class/enum/primitive/struct/union） | 全为确定值域 → 放行 | ✅ |
| `*T` with `*` | 空（多级引用投影坍缩） | 空 → 拒 | ✅ |
| `*T` with `!?` / `!#` | 空（投影坍缩） | 空 → 拒 | ✅ |
| `*T` with 无约束 | 全 8 域 | 含 FUNC/INTERFACE/ATTRIBUTE → 拒 | ✅ **决策 D4** |
| `*T` with `(F)` / `(I)` / func域 / interface域 | 单值域（func/interface） | 含 FUNC/INTERFACE → 拒 | ✅ |
| `*T` with `(A \| F)` | 混合域（class+func） | 含 FUNC → 拒 | ✅ |

> 注：无约束 `TypeParameter.domains()` 返回全部 8 域，故天然被拒；
> `ExcludeTypeConstraint.domains()` 为 `allDomains() - operand.domains()`（空域来源）。
> 值域判定在 `analyse(GenericTypeDeclarer)`，`?T` 分支另用 `syntacticRequired()` + domains=={FUNC} 判据（§5.2）。

**`*any` include**（`ReferTypeConstraint.include`）：改用 `domains()` 判定——`interface` 域 ⊆
引用域集合，即 `x.domains()` 非空且全为 `INTERFACE` 才命中 `*any`。仅 `INTERFACE` 域命中——
interface 是唯一"必引用"的域（class 可值可引用，其余域为值类型）。若未来恢复精确分域
（`INTERFACE→YES`），此判据可退回 `referenced()==YES`。

**`unmodifiable(Variable)`**：依赖 §5.2 的 `refer()` 计算化——`maybeRefer()` 有值时自动走
`ref.get().unmodifiable()` 分支（用户示例 `*&!?&!#&A` 的放行点）。

### 4.6 ∅ 检测扩展（决策 D3：不拦截，维持现状）

`checkEmpty`（`SemanticAnalyzer`）只拦两类矛盾：`C & !C`（约束与其补集相交）与两个确定域相异
（`domainOf` 非空且不同）。**不覆盖** `? & int` / `# & int`（可空/不可变标记 × 具体类型相交为 ∅）——
依据：这类约束没有 `*` 标记却带 `?`/`#`，匹配不到任何类型，不产生任何有效类型形态，放行无副作用。

## 5. 类型参数与泛型类型

### 5.1 `TypeParameter`（`ast/gen/TypeParameter.java`）

- `constraint`：`Optional<TypeConstraint>`；
- `initable`：`default` 标记（分析期校验"必须有约束"）；
- 多维度投影镜像出口：`referenced()` / `optional()` / `unmodifiable()` / `members()` / `newable()` /
  `domains()` / `hasAttr()` / `has()`——无约束时 `referenced/optional/unmodifiable/newable/hasAttr/has`
  返回 `BOTH`，`members()` 返回空下近似，`domains()` 返回全部 8 域；
- `match(td)`：无约束恒真，否则 `constraint.contains(td)`；
- 恒等：`id`（`IdGenerator`），不做值比较——同胞/自引用判定的基础。

### 5.2 `GenericTypeDeclarer`（`ast/dcl/GenericTypeDeclarer.java`）—— `refer()` 由约束投影计算化

四字段语法形态：

```
kind        : Optional<ReferKind>   // *T → STRONG；无修饰 → none
required    : boolean               // ?T → false（语法层）
unmodifiable: boolean               // #T → true（语法层）
refer       : Optional<Refer>       // 语法 kind 有值 → kind.map(...) 构造；否则由约束投影派生（deriveRefer）
```

**合并规则**（`deriveRefer`，`kind` 空时）：

```java
if (param().referenced() != Tri.YES) return Optional.empty();   // 非必引用 → 不派生
return Optional.of(new Refer(pos, ReferKind.STRONG,
        param().optional() == Tri.NO,         // 仅 NO → 非空；YES/BOTH → 可空（BOTH 判空，决策 D2）
        param().unmodifiable() != Tri.NO));   // 仅 NO → 可修；BOTH/YES → 不可改（决策 D2）
```

- `kind` 缺失时，仅 `referenced()==YES`（必引用）才派生 `Refer`；
- 派生 `kind` 默认 `STRONG`（PHANTOM 需显式 `&`）；
- `required` / `unmodifiable` 由 optional / unmodifiable 投影按保守方向收敛（决策 D2）。

**`syntacticRequired()`**：返回语法层 `required`（是否写了 `?` 标记），不受约束投影改写。
`?T` 是语法构造，其可空性不能由派生 refer 的 required 决定——`?T` 形态检查
（`analyse(GenericTypeDeclarer)`）必须用 `syntacticRequired()`，否则 bare `T` 派生
`required=false`（D2 BOTH→可空）会误入 `?T` 分支报 `only func-constraint support optional`。

**连锁影响**（`maybeRefer()` 从此对裸 `T` 有值）：

| 位置 | 计算化前 | 计算化后 |
|---|---|---|
| `unmodifiable(Variable)` | ref 空 → `v.isConst()`（用户示例失败根源） | 走 `ref.get().unmodifiable()` → **示例放行点** |
| `required()` | 裸 T → true | 由派生 refer.required() 决定 |
| `derefer()` | kind 空 → 返回 this | 派生 refer 存在 → 报 "can't de-refer a reference-type parameter"（T 本身是引用无法解引用）；否则返回 this |
| `requiredInit()` | kind 空 → `members().hasRequiredInit()` | `refer.has()` → `refer.get().required()`，与派生 refer 一致 |
| `checkEscaped` | — | 派生 kind=STRONG → 裸 T 调 escaped 方法放行 |
| `checkEnterAsync` | — | `maybeRefer()` 非空 → 可进 async |
| `?T` optional 检查 | `!td.required()` | 用 `syntacticRequired()` 判断语法标记（`?T` 是语法构造，可空性不受约束投影改写） |

**成员查找通道**：`GenericTypeDeclarer` 的字段/方法查找来自 `param().members()`（下近似），
是泛型参数成员查找的唯一通道。

## 6. 约束检查管线（分析期）

### 6.1 声明处（`analyse(TypeParameters)`，`SemanticAnalyzer` L484）

- `default` 必须有约束：`"default' requires a constraint"`；
- 递归 `analyse(TypeConstraint)`：`DefinedTypeConstraint` 解析 `DerivedType`（`findDef`，
  同胞引用在此解析）、`AttributeTypeConstraint` 解析属性、二元/排除/括号递归子约束；
  叶子约束（域/引用/可空/不可变）无需解析。

### 6.2 实例化处（`GenericMap.make` + `checkConstraint`）

`GenericMap.make`（`ast/gen/GenericMap.java`）：

- 实参个数检查（`mismatch` / `too much`）；
- PHANTOM 引用实参拒：`can't use phantom-refer as type argument`；
- 实参经 `parent.mapIf` 预替换（泛型链）；
- 同一参数被推得两个不同实参 → `cannot be deduced as both`（推断冲突）；
- `merge(parent)` 成链。

`checkConstraint(gm)`（`SemanticAnalyzer` L590）——**所有实参绑定的统一出口**，7 个入口：

| 入口 | 场景 |
|---|---|
| `findDef`（L682） | 类型实参（`Box`A`` 中的 A） |
| `genericInfer`（L4256） | 函数调用实参推断 |
| `optimizeMethod`（L4736） | 方法调用显式/下近似实参 |
| `findCallable`（L4989 / L5020） | 函数/方法作值引用 + 显式类型参数 |
| `findSymbol`（L5084） | 泛型函数作值引用（else 分支） |

每条绑定执行两级检查：

1. `c.match(t)`：不满足 → `type '%s' doesn't satisfy constraint '%s'`；
2. `c.initable() && !literalSafe(c,t)`：不满足 → `can't support default-init`。

`checkConstraint` 用 `error` 记录（非 `semantic` 抛出），一条实参失败不连坐其余实参。

### 6.3 `literalSafe`（default 外层边界）

"实参能否作为 `default` 参数被字面量初始化"，三条分支：

1. **引用实参拒**：`t.maybeRefer().has()` → false（引用字面量是 nil，形态未知）；
2. **泛型链**（实参是 `GenericTypeDeclarer`）：链上参数无 `default` → false；
   且约束成员下近似字段 ⊆ 实参参数成员下近似字段（`allMatch`）——实参自身的必填字段必须在 T 的下近似中可见；
3. **`DerivedTypeDeclarer`**：约束 `members()`（`c.members()`）已覆盖的字段跳过；其余字段经
   `dtd.gm().mapIf(f.type()).requiredInit()` 有必填 → false（下近似外新增必填字段拒）。

### 6.4 约束依赖收集（`findInitDeps`，L1289）

`findInitDeps` 收集类的初始化依赖（继承 + 字段类型），泛型参数约束中引用的类
（`Cat`Moon`` 中的 `Cat`）曾是其中的 visit 依赖（`collectConstraintTypeDeps`，已于
概念约束落地时移除）。当前类 visit 顺序（DAG）只按继承/字段依赖排序，约束树在
`visitClass` 内经 `analyse(cd.generic())` 递归解析，随后 `checkConstraint(table.types.values())`
（L390）统一校验——约束中引用的类不产生独立 visit 依赖。

## 7. 成员查找（Abstractable / Aggregatable 重构）

### 7.1 接口统一（`ast/`）

```
ReadMap<K,V>            : Optional<V> tryGet(K key)
Abstractable<M:Method>  : Optional<M> method(Identifier); ReadMap<Identifier,M> methods()
Aggregatable<F:Field>   : Optional<F> field(Identifier);  ReadMap<Identifier,F> fields()
```

- `ClassDefinition`：Aggregatable + Abstractable（字段 `allFields`，方法 `allMethods`）；
- `StructureDefinition`：Aggregatable（字段 `fields`，`modifier` export=true）；
- `InterfaceDefinition`：Abstractable（非 Aggregatable）；
- `EnumDefinition`：Aggregatable（token 字段 `id`/`value`/`name` 由初始化块注入 `IdentifierMap`，
  枚举值在 `values`）；
- `MemberSet`：Aggregatable + Abstractable（成员下近似字段/方法）；
- `OrderlyMap`：实现 `ReadMap`；`Groups.G3` 新增 `reduce()`。

### 7.2 `optimize(MemberOfExpression)` 分派（L4768）

1. `DefinitionDeclarer` + Enum → `optimizeEnum`（`S.A` 枚举值访问主路径）；
2. `ArrayTypeDeclarer` → 数组 `length/values/swap/move`；
3. `expectCallable`：
   - `DerivedTypeDeclarer`（定义 Abstractable）→ `optimizeMethod(dtd.gm())`；
   - `GenericTypeDeclarer`（下近似）→ `optimizeMethod(GenericMap.EMPTY)`；
4. 非 callable：
   - `EnumTypeDeclarer` → `optimizeField(etd.def(), EMPTY, false)`（`.id/.value/.name` token）；
   - `DerivedTypeDeclarer`（定义 Aggregatable）→ `optimizeField(dtd.gm(), expectCallable)`；
   - `GenericTypeDeclarer`（下近似）→ `optimizeField(EMPTY, expectCallable)`；
5. 查找失败/非泛型实参 → `invalid(e.generic())`。

### 7.3 `optimizeField`（L4704）

`d.field(name)` 缺失 → `'%s' not defined field`；`checkExport` 过滤；字段类型
`analyse(gm.mapIf(f.type()))`（泛型映射 + 原型类型转 `NamedFuncTypeDeclarer`）；
`expectCallable` 时仅 `FuncTypeDeclarer`（或 `GenericTypeDeclarer` 经 `funcPrototype`
转 `AnonFuncTypeDeclarer`）可调用。

### 7.4 `optimizeMethod`（L4736）

`d.method(name)` 缺失 → 空结果；新检查：非泛型方法带泛型实参 → `'%s' is not generic method`；
`checkExport` / `checkEscaped`（`checkRefer(STRONG)` 判逃逸方法可否经引用调用）/
`checkUnmodifiable` / `checkEnterAsync`；`checkConstraint(GenericMap.make(...))` 校验
方法级实参约束；返回 `MethodExpression`。

### 7.5 `funcPrototype`（L4973）：原型约束可调用

`Paren` 剥壳 → `DefinedTypeConstraint`：

- `DerivedType` 且定义为 `PrototypeDefinition` → 原型经 `d.gm().instantiate` 实例化；
- `GenericType` → 递归同胞参数（`U (Task), T (U)` 链）。

复合约束（`Task1 | Task2`）不可解析 → 不可调用。入口：`findCallable`（变量）、
`optimizeField`（下近似字段）、`optimize(MethodOperand)` 等。

## 8. default 字面量初始化（三层检查）

```
┌─ 内层 gate：声明处（optimize(ObjectExpression)，L5224）
│   L5234 引用类型拒：*A 不能 {} 初始化
│   L5238-5241 union 多字段拒
│   L5243 GenericTypeDeclarer 无 default 拒：requires 'default'
│   L5246 literalInitable（空成员下近似守卫）：下近似字段空 → 无 hasRequiredInit 且域单值 CLASS/STRUCT
│   L5258 下近似字段逐字段 requiredInit 校验（缺必填字段拒）
├─ 外层 gate：实例化处（checkConstraint → literalSafe，见 §6.3）
│   下近似外新增必填字段拒 / 引用实参拒 / 泛型链 default 传递
└─ 后端不参与：下近似只存在于分析期，C 代码生成不感知
```

`literalInitable(GenericTypeDeclarer)`：成员下近似字段非空 → 可初始化（逐字段校验）；
字段空 → 无 `hasRequiredInit` 且 `domains()` 单值 CLASS/STRUCT 才放行（`{}` 空字面量的确定可构造）。

## 9. 泛型 `new(T)`（`analyseNewType(GenericType)`）

对照具体路径 `new(A)` 的**两个正交问题**：

1. **能否构造**（`newable`，类型定义属性）：约束投影 `newable()==YES` 且 `referenced()!=YES`
   才可 new（见 §4.5 新判据：非必引用，且值形态可构造）；域 FUNC/ATTRIBUTE/INTERFACE / `*` 拒；
2. **能否省略参数**（`hasRequiredInit`，聚合判据）：`members()` 的必填性以 OR 存活——
   单侧必填的 `A|B` 无参 `new(T)` 拒。

带参 `new(T, arg)`：实参必须与 T 恒等（`it.equals(g.b())`，泛型赋值退化），
且不能是引用（`maybeRefer().has()` 拒）；object-literal 实参受 `default` 门控。

值类型变量声明 `var a T;` 走同一判据（必填未提供拒，`testGenericConstraint51`）。

> 消费点判据细节（含决策 D3：`? & int` / `# & int` 放行）见 §4.5。

## 10. 单态化修正（`mono/Monomorphization.java`）

- `TryStatement` 递归扫描 body/catch（实参类型 + 类型集）/finally —— **修复 try 块内
  泛型实例化/类型物化漏收集**；
- `IterableForStatement` / `ConditionalForStatement` 分解扫描（iterable/initializer/condition/updater/body）；
- `ThrowStatement` / `AssertStatement` / `LabeledStatement` / `Branch` 补充扫描。

## 11. 设计决策与已知边界

| 决策/边界 | 说明 |
|---|---|
| 决策 D1（引用投影精度） | 引用性维度保持全 `BOTH`，不做精确分域；消费点判据重建（§4.3/§4.5）。恢复精确分域（组合形状枚举）列为可选改进 |
| 决策 D2（BOTH 收敛位置） | `BOTH` 保守收敛在 `SemanticAnalyzer` 消费点，不在约束层压扁（§4.4） |
| 决策 D3（`? & int`/`# & int`） | 不拦截、不做 ∅ 检测扩展（§4.5/§4.6） |
| 决策 D4（无约束 `*T`） | 不放行；`*T` 判据改用 domains（仅"确定值域"可引用，§4.5） |
| 补集投影退化 | `!C` 的 `members()` 恒为空（补集在开放成员维度无法保证公共成员）；`domains()` 精确闭合（`allDomains() - operand.domains()`） |
| union 成员冲突剔除 | 同名不同形态（var/const、@X、m/m#、escaped）无共同保证，直接剔除 |
| `contains` 只认 `DerivedTypeDeclarer` | 可空函数包装（如 `?Task`）不匹配 `DefinedTypeConstraint`（已知缺陷，测试覆盖缺口见 §12） |
| 成员下近似丢失 | union 混合必填/非必填类时字段信息丢弃，`hasRequiredInit` 以 OR 存活兜底 |
| `default` 是许可 | 不是自动能力；声明处 `default` 必须带约束（§6.1） |
| 检查在分析期 | monomorphization 只替换不检查；泛型语义错误一律查分析期判据 |
| `checkConstraint` 用 `error` | 单条实参失败不连坐；同一 `GenericMap` 内其余绑定继续检查 |
| 枚举 token 字段 | `id`/`value`/`name` 为 `Identifier` 常量，实例访问走 `EnumTypeDeclarer → optimizeField → fields`；C 端 `Feng$Enum` 仅 `$value`/`$name`（`Header.h`），`.id` 生成主体引用 |
| 约束投影·解析时序 | 概念约束在类分析后才会被解析（`visitClasses` 先于 `analyse(concepts)`），`DefinedTypeConstraint.domains()` 遇未解析 `DerivedType` 须 `dt.analyzed()` 守卫返回 `allDomains()`（保守默认） |
| 派生引用解引用 | 裸 T 由约束投影出引用（派生 refer）后，`*t` 无法解引用——T 本身是引用（§5.2 `derefer()`） |
| 派生组合表达空间限制 | `optional()==YES && unmodifiable()==NO`（可空且可修改）的组合无法由语法构造——`?` 提供 optional=YES 但 referenced=BOTH，必须 `& *`（提供 referenced=YES）才触发派生，而 `*` 的 unmodifiable=BOTH 经 D2 收敛为不可修改；无语法手段单独拉成 NO。不产生有效输入，属表达空间而非实现缺陷 |

## 12. 测试覆盖（`SemanticAnalyzerTest`）

`testGenericConstraint1`–`87`（含 `71b`/`D2`/`D2b`/`D2c`/`D3` 及 concept 用例），全量回归 717 例全绿 BUILD SUCCESS：

- **约束代数**（1–30）：接口、类继承、深层继承 BFS、并集/交集、域（class/interface/enum/struct/union/attribute/func/primitive）、
  引用/可空/不可变/属性/排除/括号、同胞引用、自引用（拒）、嵌套约束、函数推断/显式实参、方法推断/显式实参、primitive 约束；
- **成员查找**（31–40）：字段/方法经 gm 实例化、可调用字段、sameView 各维度（immutable/modifier/unmodifiable/escaped）、
  intersect 方向并集剔除、写路径（值参数 vs var 局部）；
- **new / default / 字面量**（41–42、48–53、57、82–87）：newable 与 hasRequiredInit 正交、带参 new 恒等退化、
  default 门控、下近似外必填边界、引用实参形态、union 单侧必填、枚举 new、`var a T` 声明路径、无约束/域守卫/`(struct)` 域约束 new 拒、
  引用字面量初始化拒、concept 委托投影（`Val & int` / `int & primitive`）、DefinedType `GenericType` 同胞委托分支（`V U` 链）、
  `Tri.meet`/`join` 各分支（`? | int` / `@D | int` / `int | @D`）；
- **可调用与形态**（43–47、54–61）：`*T`/`?T` 形态检查、func 域引用拒、原型约束变量/字段可调用、
  funcPrototype 同胞链递归、literalSafe 嵌套泛型实参、逃逸方法经 STRONG 调用、enum/union/prototype 实参 default、
  约束依赖收集（primitive 约束回退）、泛型函数作值引用；
- **引用投影与决策**（71b、83、D2、D2b、D2c、D3）：类约束直接传值实参（`add\`B\`(B{})`）、
  `? & int` / `# & int` 放行与 `* & int`/`!?`/`!#`/`!*` 拒绝（决策 D3）、BOTH 收敛（`*&A` 不可修改拒 vs
  `*&!?&!#&A` 可修改放行，决策 D2）、`*T` 值域补全（primitive/struct/union/enum，决策 D4）、
  deriveRefer 可空派生（`* & ? & A` 未判空拒 / 判空放行）、派生引用解引用拒（D2c）、interface 域裸 T 派生 STRONG 必选引用（D3）。

## 13. 与旧文档的主要差异

- **`type-constraint-roadmap.md`**（过程路线图）：
  - 路线图中的 `TypeView extends TypeDeclarer implements Referable` 已被**多维度投影模型**取代
    （先独立为四维三态类，后进一步被投影方法族取代，见 §4）；
  - `link()` 从 `TypeDefinition` 下移到 `ObjectDefinition`（`b53b19d`）；
  - 约束检查入口、`analyse(TypeParameters)` 调用点、DomainTypeConstraint 投影精度等遗留问题已按最终实现落地；
  - 引用已删除的 `type-set-algebra.md`/`type-set-design.md`，以本文为准。
- **`type-init-capability.md`**（初始化能力）：
  - 三层检查已细化为 §8：内层 gate = `param.initable()` + `literalInitable`（空成员下近似守卫），
    外层 gate = `checkConstraint` 的 `literalSafe` 三条分支；
  - 新增 `new(T)` 的 `newable`/`hasRequiredInit` 正交模型（§9）与成员查找路径（§7）。
- **`generic-constraint-projection.md`**（投影模型定稿）：
  - 本文 §3.3/§4 以其为前置，实现其提出的 `nullable()`/`unmodifiable()` 投影器
    （命名对齐约束类，`nullable()` → `optional()`）；
  - §4.3 精确分域求值表被决策 D1（全 `BOTH` + `Tri.not`）取代；
  - §4.5 `*T`/`new(T)` 用 `referenced()==NO` 的判据被 §4.5 消费点重写取代。
- **`generic-constraint-refer-projection.md`**（引用三特征值投影定稿）：
  - **本文引用维度以该文档为定稿**：引用三特征值（引用性/可空性/可变性）此前既未完整实现、又存在
    跨维度取反错误（缺陷 A：`ExcludeTypeConstraint.referenced()` 跨维度取反；缺陷 B：
    `DefinedTypeConstraint`(class) 的 `referenced()=NO` 与其 `contains(*A)=true` 矛盾；
    缺陷 C：泛型引用投影未合并到 `GenericTypeDeclarer`），本次一并修正；
  - `GenericTypeDeclarer.refer()` 由约束投影计算化（§5.2）+ `syntacticRequired()`；
  - `OptionalTypeConstraint.contains` 补可空 func 原型（`?F`）；
  - 消费点重写（§4.5）：`new(T)`（`!=YES && newable()==YES`）、`*T`（domains 判据，决策 D4）、
    `*any` include（domains 全 INTERFACE）、`unmodifiable(Variable)`（走 ref 分支）；
  - 决策 D1–D4 全部落地（§4.3/§4.4/§4.5/§4.6）。
