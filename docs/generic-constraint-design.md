# 泛型类型约束系统 — 最终实现设计

> 状态：最终实现（分支 `dev-constraint`，HEAD `996a01a` "Add set-algebra-based constraints for generic types"）
> 前置文档：`type-constraint-roadmap.md`（过程路线图）、`type-init-capability.md`（初始化能力）仅为过程记录，
> 部分描述与最终实现有出入，**本文以当前源码为准**。

## 1. 设计目标

泛型类型参数（`class Box`T`{...}` / `func f`T`(x T)`）的约束系统：

1. **集合语义**：约束是一个"类型集合"，`contains(TypeDeclarer)` 回答"实参是否属于该集合"；
2. **部分类型**：约束同时投影为一个 `TypeView`（部分类型），使泛型参数在分析期可进行
   成员查找、`new`、字面量初始化、可调用判断——即使实参尚未绑定；
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
- `abstract TypeView view()` —— 约束投影为部分类型；
- 结构化 `equals`/`hashCode`（约束树可作为 key）。

九个实现类：

| 类 | 语法 | `contains` 语义 | `view` |
|---|---|---|---|
| `DefinedTypeConstraint` | `int` / `Bus` / `T` | ① `PrimitiveType`：原始类型恒等；② `GenericType`：同胞参数恒等，否则按该参数自身约束展开；③ `DerivedType`：定义相等，或经 `ObjectTool.checkInherited` 沿 `supers()` 链子类型匹配 | `PrimitiveType/DerivedType` 走 `TypeView.of(declarer)`；泛型参数走其约束视图（无约束 → `unknown`） |
| `DomainTypeConstraint` | `class`/`struct`/`enum`/`union`/`interface`/`func`/`attribute`/`primitive` | `domainOf(t) == domain`（primitive→域、Derived→定义域、Func→FUNC） | `TypeView.domain(domain)` |
| `ReferTypeConstraint` | `*` | `t.maybeRefer().has()` | `TypeView.refer(true)` |
| `OptionalTypeConstraint` | `?` | 可空引用（`refer.has() && !required`） | `TypeView.optional(true)` |
| `UnmodifiableTypeConstraint` | `#` | 不可变引用 | `TypeView.unmodifiable(true)` |
| `AttributeTypeConstraint` | `@X` | 定义的 `modifier().attributes()` 含 `X` | `TypeView.attribute(attr)` |
| `ExcludeTypeConstraint` | `!C` | 补集：`!operand.contains(t)` | `unknown`（补集无法表示为单一视图） |
| `BinaryTypeConstraint` | `A&B` / `A\|B` | AND：两操作数都含；OR：任一含 | `intersect` / `union` |
| `ParenTypeConstraint` | `(C)` | 委托子约束 | 委托 |

子类型匹配（`DefinedTypeConstraint.contains` → `ObjectTool.checkInherited`）：

- 左右必须都是 `DerivedTypeDeclarer` 且定义都是 `ObjectDefinition`（class/interface）；
- 从实参定义沿 `supers()` 深度优先搜索，逐层用 `st.gm().overlay(next)` 累积泛型映射，
  命中即返回该层实例化的 `TypeArguments`（`Optional.has` → 满足）。

## 4. TypeView：四维三态（`ast/type/TypeView.java`）

### 4.1 模型

`TypeView` 是约束/类型参数的**投影**，只暴露分析所需类型信息，**永远不作为返回值类型**——
对其查询产出的是 `TypeDeclarer`（字段类型/方法签名）。

四个正交维度，全部为 `Boolean` 三态（`null` = unknown，`false`/`true` = 确定）：

| 维度 | 含义 |
|---|---|
| `isRefer` | false=值类型，true=引用类型 |
| `domain` | `TypeDomain`（null=未知） |
| `optional` | false=必填，true=可空 |
| `unmodifiable` | false=可变，true=不可变 |

另有两个聚合判据 + 成员信息：

- `newable`（Boolean 三态）：类型定义本身能否构造实例（class/struct/enum/array/primitive 可；
  interface/attribute/func 不可；unknown）；
- `hasRequiredInit`（boolean）：视图中**某个成员**携带必填初始化（`f.type().requiredInit()`）。
  union/intersect 中字段可能被丢弃，但该必填性以 OR 存活；
- `attributes` / `fields` / `methods` / `parent`（类继承）：成员经声明类型泛型映射实例化。

`unknown()` 是"无公共特征"哨兵（如 `struct | func`），`union` 产出的空视图。

### 4.2 提取（`TypeView.of(TypeDeclarer)`）

从具体类型提取完整视图：`isReferOf`（`maybeRefer` 有值即 true）、`optionalOf`/`unmodifiableOf`、
`newableOf`、`hasRequiredInitOf`、`attributesOf`、`fieldsOf`/`methodsOf`、`parentOf`。

成员提取时做泛型实例化（`mapField`/`mapMethod`，经声明类型 `dtd.gm()`）：

- 字段：`gm.mapIf(f.type())`，无映射返回原字段；
- 方法：仅当 `gm` 非空且原型含类型变量时 `gm.instantiate(prototype)`，并保留
  `master/dynamic/override` 身份（`ClassMethod`），`InterfaceMethod` 直接重建；
- 数组视图：字段 `length`/`values`，方法 `swap`/`move`。

### 4.3 组合（`union` / `intersect`）

对应 `|`（OR 约束）与 `&`（AND 约束）两个方向：

```
union(a,b)      // OR：结果携带两侧都"保证"的公共特征
  and(a,b)      // 维度：双方一致才确定，否则 null（unknown 吸收）
  hasRequiredInit || other.hasRequiredInit
  fields/methods: 同名且 sameView 才保留（交集）；冲突剔除

intersect(a,b)  // AND：结果是被组合类型必须"满足"的收紧视图
  or(a,b)       // 维度：任一侧确定即确定，冲突→null
  hasRequiredInit || other.hasRequiredInit
  fields/methods: 并集，同名不同签名/形态剔除
```

`sameView` 判据（成员"相同"才能存活）：

- 字段：类型 + `sync` + `immutable` + `modifier`（含属性）；
- 方法：原型 + `escaped` + `unmodifiable` + `modifier`。

域组合：`andDomain`/`orDomain`（相等才确定）。`parent` 组合：双方相同才保留。

### 4.4 工厂

`unknown()`、`refer(Boolean)`、`optional(Boolean)`、`unmodifiable(Boolean)`、
`attribute(Attribute)`、`ofRefer(Refer)`（把语法 `Refer` 分解为 isRefer=true + optional + unmod 三维）、
`domain(TypeDomain)`（primitive/struct/union/enum 隐含值类型 isRefer=false；interface/class/func 未知）、
`of(TypeDeclarer)`。

## 5. 类型参数与泛型类型

### 5.1 `TypeParameter`（`ast/gen/TypeParameter.java`）

- `constraint`：`Optional<TypeConstraint>`；
- `initable`：`default` 标记（分析期校验"必须有约束"）；
- `view()`：约束视图缓存；
- `match(td)`：无约束恒真，否则 `constraint.contains(td)`；
- 恒等：`id`（`IdGenerator`），不做值比较——同胞/自引用判定的基础。

### 5.2 `GenericTypeDeclarer`（`ast/dcl/GenericTypeDeclarer.java`）四字段模型

```
kind        : Optional<ReferKind>   // *T → STRONG；无修饰 → none
required    : boolean               // ?T → false
unmodifiable: boolean               // #T → true
refer       : Optional<Refer>       // = kind.map(...) 构造
```

- `requiredInit()`：kind 有值 → `required`；无 → 参数视图的 `hasRequiredInit`；
- `derefer()`：kind→none 返回值形态副本；
- 视图字段（`fields`/`methods`）来自 `param().view()`，是泛型参数成员查找的唯一通道。

## 6. 约束检查管线（分析期）

### 6.1 声明处（`analyse(TypeParameters)`，`SemanticAnalyzer` L440）

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

`checkConstraint(gm)`（`SemanticAnalyzer` L478）——**所有实参绑定的统一出口**，7 个入口：

| 入口 | 场景 |
|---|---|
| `findDef`（L543） | 类型实参（`Box`A`` 中的 A） |
| `genericInfer`（L4175） | 函数调用实参推断 |
| `optimizeMethod`（L4640 / L4677） | 方法调用显式/视图实参 |
| `findCallable`（L5071 / L5100） | 函数/方法作值引用 + 显式类型参数 |
| `findSymbol`（L5174） | 泛型函数作值引用（else 分支） |

每条绑定执行两级检查：

1. `c.match(t)`：不满足 → `type '%s' doesn't satisfy constraint '%s'`；
2. `c.initable() && !literalSafe(c,t)`：不满足 → `can't support default-init`。

`checkConstraint` 用 `error` 记录（非 `semantic` 抛出），一条实参失败不连坐其余实参。

### 6.3 `literalSafe`（default 外层边界，L502）

"实参能否作为 `default` 参数被字面量初始化"，三条分支：

1. **引用实参拒**：`t.maybeRefer().has()` → false（引用字面量是 nil，形态未知）；
2. **泛型链**（实参是 `GenericTypeDeclarer`）：链上参数无 `default` → false；
   且约束视图字段 ⊆ 实参参数视图字段（`allMatch`）——实参自身的必填字段必须在 T 视图中可见；
3. **`DerivedTypeDeclarer`**：视图（`c.view()`）已覆盖的字段跳过；其余字段经
   `dtd.gm().mapIf(f.type()).requiredInit()` 有必填 → false（视图外新增必填字段拒）。

### 6.4 约束依赖收集（`findInitDeps` / `collectConstraintTypeDeps`，L1109）

约束中引用的类（`Cat`Moon`` 中的 `Cat`）也是本类的前置 visit 依赖；`GenericType`
（同胞参数引用）不产生类依赖。类 visit 顺序（DAG）由此保证约束树先于使用处解析。

## 7. 视图成员查找（Abstractable / Aggregatable 重构）

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
- `TypeView`：Aggregatable + Abstractable（视图字段/方法）；
- `OrderlyMap`：实现 `ReadMap`；`Groups.G3` 新增 `reduce()`。

### 7.2 `optimize(MemberOfExpression)` 分派（L4874）

1. `DefinitionDeclarer` + Enum → `optimizeEnum`（`S.A` 枚举值访问主路径）；
2. `ArrayTypeDeclarer` → 数组 `length/values/swap/move`；
3. `expectCallable`：
   - `DerivedTypeDeclarer`（定义 Abstractable）→ `optimizeMethod(dtd.gm())`；
   - `GenericTypeDeclarer`（视图）→ `optimizeMethod(GenericMap.EMPTY)`；
4. 非 callable：
   - `EnumTypeDeclarer` → `optimizeField(etd.def(), EMPTY, false)`（`.id/.value/.name` token）；
   - `DerivedTypeDeclarer`（定义 Aggregatable）→ `optimizeField(dtd.gm(), expectCallable)`；
   - `GenericTypeDeclarer`（视图）→ `optimizeField(EMPTY, expectCallable)`；
5. 查找失败/非泛型实参 → `invalid(e.generic())`。

### 7.3 `optimizeField`（L4615）

`d.field(name)` 缺失 → `'%s' not defined field`；`checkExport` 过滤；字段类型
`analyse(gm.mapIf(f.type()))`（泛型映射 + 原型类型转 `NamedFuncTypeDeclarer`）；
`expectCallable` 时仅 `FuncTypeDeclarer`（或 `GenericTypeDeclarer` 经 `funcPrototype`
转 `AnonFuncTypeDeclarer`）可调用。

### 7.4 `optimizeMethod`（L4646）

`d.method(name)` 缺失 → 空结果；新检查：非泛型方法带泛型实参 → `'%s' is not generic method`；
`checkExport` / `checkEscaped`（`checkRefer(STRONG)` 判逃逸方法可否经引用调用）/
`checkUnmodifiable` / `checkEnterAsync`；`checkConstraint(GenericMap.make(...))` 校验
方法级实参约束；返回 `MethodExpression`。

### 7.5 `funcPrototype`（L5023）：原型约束可调用

`Paren` 剥壳 → `DefinedTypeConstraint`：

- `DerivedType` 且定义为 `PrototypeDefinition` → 原型经 `d.gm().instantiate` 实例化；
- `GenericType` → 递归同胞参数（`U (Task), T (U)` 链）。

复合约束（`Task1 | Task2`）不可解析 → 不可调用。入口：`findCallable`（变量）、
`optimizeField`（视图字段）、`optimize(MethodOperand)` 等。

## 8. default 字面量初始化（三层检查）

```
┌─ 内层 gate：声明处（optimize(ObjectExpression)）
│   L5287 引用类型拒：*A 不能 {} 初始化
│   L5290 union 多字段拒
│   L5297 GenericTypeDeclarer 无 default 拒：requires 'default'
│   L5302 literalInitable（空视图守卫）：视图字段空 → 无 hasRequiredInit 且 CLASS/STRUCT 域
│   L5311 视图字段逐字段 requiredInit 校验（缺必填字段拒）
├─ 外层 gate：实例化处（checkConstraint → literalSafe，见 §6.3）
│   视图外新增必填字段拒 / 引用实参拒 / 泛型链 default 传递
└─ 后端不参与：视图只存在于分析期，C 代码生成不感知
```

`literalInitable(GenericTypeDeclarer)`（L3498）：视图字段非空 → 可初始化（逐字段校验）；
字段空 → 无 `hasRequiredInit` 且域为 CLASS/STRUCT 才放行（`{}` 空字面量的确定可构造）。

## 9. 泛型 `new(T)`（`analyseNewType(GenericType)`，L4913）

对照具体路径 `new(A)` 的**两个正交问题**：

1. **能否构造**（`newable`，类型定义属性）：视图 `unknown` / `isRefer` 非 false /
   域 FUNC/ATTRIBUTE/INTERFACE → 拒；
2. **能否省略参数**（`hasRequiredInit`，聚合判据）：视图字段可能被 union 丢弃，
   但必填性以 OR 存活——单侧必填的 `A|B` 无参 `new(T)` 拒。

带参 `new(T, arg)`（L4950）：实参必须与 T 恒等（`it.equals(g.b())`，泛型赋值退化），
且不能是引用（`maybeRefer().has()` 拒）；object-literal 实参受 `default` 门控。

值类型变量声明 `var a T;` 走同一判据（必填未提供拒，`testGenericConstraint51`）。

## 10. 单态化修正（`mono/Monomorphization.java`）

- `TryStatement` 递归扫描 body/catch（实参类型 + 类型集）/finally —— **修复 try 块内
  泛型实例化/类型物化漏收集**；
- `IterableForStatement` / `ConditionalForStatement` 分解扫描（iterable/initializer/condition/updater/body）；
- `ThrowStatement` / `AssertStatement` / `LabeledStatement` / `Branch` 补充扫描。

## 11. 设计决策与已知边界

| 决策/边界 | 说明 |
|---|---|
| 补集视图退化 | `!C` 的 `view()` 恒为 `unknown`（补集无法表示为单一视图），下游保守拒绝 |
| union 成员冲突剔除 | 同名不同形态（var/const、@X、m/m#、escaped）无共同保证，直接剔除 |
| `contains` 只认 `DerivedTypeDeclarer` | 可空函数包装（如 `?Task`）不匹配 `DefinedTypeConstraint`（已知缺陷，测试覆盖缺口见 §12） |
| 视图字段丢失 | union 混合必填/非必填类时字段信息丢弃，`hasRequiredInit` 以 OR 存活兜底 |
| `default` 是许可 | 不是自动能力；声明处 `default` 必须带约束（§6.1） |
| 检查在分析期 | monomorphization 只替换不检查；泛型语义错误一律查分析期判据 |
| `checkConstraint` 用 `error` | 单条实参失败不连坐；同一 `GenericMap` 内其余绑定继续检查 |
| 枚举 token 字段 | `id`/`value`/`name` 为 `Identifier` 常量，实例访问走 `EnumTypeDeclarer → optimizeField → fields`；C 端 `Feng$Enum` 仅 `$value`/`$name`（`Header.h`），`.id` 生成主体引用 |

## 12. 测试覆盖（`SemanticAnalyzerTest`）

`testGenericConstraint1`–`61`（61 个用例，420/0/0 全绿，全量 651 例 BUILD SUCCESS）：

- **约束代数**（1–30）：接口、类继承、深层继承 BFS、并集/交集、域（class/interface/enum/struct/union/attribute/func/primitive）、
  引用/可空/不可变/属性/排除/括号、同胞引用、自引用（拒）、嵌套约束、函数推断/显式实参、方法推断/显式实参、primitive 约束；
- **视图成员查找**（31–40）：字段/方法经 gm 实例化、可调用字段、sameView 各维度（immutable/modifier/unmodifiable/escaped）、
  intersect 方向并集剔除、视图写路径（值参数 vs var 局部）；
- **new / default / 字面量**（41–42、48–53）：newable 与 hasRequiredInit 正交、带参 new 恒等退化、
  default 门控、视图外必填边界、引用实参形态、union 单侧必填、枚举 new、`var a T` 声明路径、无约束/域守卫/`(struct)` 域约束 new 拒、
  引用字面量初始化拒；
- **可调用与形态**（43–47、54–61）：`*T`/`?T` 形态检查、func 域引用拒、原型约束变量/字段可调用、
  funcPrototype 同胞链递归、literalSafe 嵌套泛型实参、逃逸方法经 STRONG 调用、enum/union/prototype 实参 default、
  约束依赖收集（primitive 约束回退）、泛型函数作值引用。

## 13. 与旧文档的主要差异

- **`type-constraint-roadmap.md`**（过程路线图）：
  - 路线图中的 `TypeView extends TypeDeclarer implements Referable` 已被**独立四维三态类**取代；
  - `link()` 从 `TypeDefinition` 下移到 `ObjectDefinition`（`b53b19d`）；
  - 约束检查入口、`analyse(TypeParameters)` 调用点、DomainTypeConstraint 视图精度等遗留问题已按最终实现落地；
  - 引用已删除的 `type-set-algebra.md`/`type-set-design.md`，以本文为准。
- **`type-init-capability.md`**（初始化能力）：
  - 三层检查已细化为 §8：内层 gate = `param.initable()` + `literalInitable`（空视图守卫），
    外层 gate = `checkConstraint` 的 `literalSafe` 三条分支；
  - 新增 `new(T)` 的 `newable`/`hasRequiredInit` 正交模型（§9）与视图成员查找路径（§7）。
